package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.game.CardTraitBase;
import forge.game.GameEntity;
import forge.game.ability.AbilityKey;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.replacement.ReplacementEffect;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.lang.ref.WeakReference;
import java.util.*;

/** Opt-in decisions only. The original optional replacement and reveal/movement effects remain native. */
public final class AbundanceAi {
    private record Plan(WeakReference<Player> player, WeakReference<Card> source, long stamp) { }
    private static final Map<SpellAbility, Plan> PLANS = Collections.synchronizedMap(new WeakHashMap<>());
    private AbundanceAi() { }

    public static boolean applies(final Card card) {
        return card != null && "Abundance".equals(AiOverrideProfile.get().getAiLogic(card));
    }

    private static boolean choiceShape(final SpellAbility sa) {
        if (sa == null || sa.getApi() != ApiType.GenericChoice || sa.getSubAbility() != null || !sa.getTargets().isEmpty()
                || !Map.of("DB", "GenericChoice", "Choices", "DigLand,DigNonland", "Defined", "You").equals(sa.getMapParams())) { return false; }
        final var list = sa.getAdditionalAbilityList("Choices");
        if (list == null || list.size() != 2) { return false; }
        for (int i = 0; i < 2; i++) {
            final SpellAbility b = list.get(i);
            if (b.getApi() != ApiType.DigUntil || b.getSubAbility() != null || !b.getTargets().isEmpty()
                    || !Map.of("DB", "DigUntil", "Valid", i == 0 ? "Card.Land" : "Card.nonLand",
                        "ValidDescription", i == 0 ? "land" : "nonland", "FoundDestination", "Hand",
                        "RevealedDestination", "Library", "RevealedLibraryPosition", "-1",
                        "SpellDescription", i == 0 ? "Land" : "Nonland").equals(b.getMapParams())) { return false; }
        }
        return true;
    }

    private static ReplacementEffect original(final Card source) {
        if (!applies(source) || source.isFaceDown() || source.isCloned() || source.getReplacementEffects().size() != 1) { return null; }
        final var r = source.getReplacementEffects().get(0);
        if (r.getHostCard() != source || !r.getMode().name().equals("Draw")
                || !"Battlefield".equals(r.getParam("ActiveZones")) || !"You".equals(r.getParam("ValidPlayer"))
                || !"AbundantChoice".equals(r.getParam("ReplaceWith")) || !"True".equals(r.getParam("Optional"))
                || !Set.of("Event", "ActiveZones", "ValidPlayer", "ReplaceWith", "Optional", "Description").containsAll(r.getMapParams().keySet())
                || !choiceShape(r.getOverridingAbility())) { return null; }
        return r;
    }

    private static boolean drawEngine(final SpellAbility sa, final Set<SpellAbility> seen) {
        if (sa == null || !seen.add(sa)) { return false; }
        if (sa.getApi() != null && Set.of(ApiType.Draw, ApiType.Mill, ApiType.Dig, ApiType.DigUntil,
                ApiType.Reveal, ApiType.PeekAndReveal).contains(sa.getApi())) { return true; }
        if (sa.getMapParams().values().stream().anyMatch(v -> v.contains("DrawnThis") || v.contains("Draw<"))
                || drawEngine(sa.getSubAbility(), seen)) { return true; }
        for (final var extra : sa.getAdditionalAbilities().values()) { if (drawEngine(extra, seen)) { return true; } }
        for (final var list : sa.getAdditionalAbilityLists().values()) {
            for (final var extra : list) { if (drawEngine(extra, seen)) { return true; } }
        }
        return false;
    }

    private static boolean commanderReturn(final Card c, final ReplacementEffect r) {
        final var sa = r.getOverridingAbility();
        return c.isImmutable() && "Commander Effect".equals(c.getName()) && r.getMode().name().equals("Moved")
                && "Card.IsCommander+YouOwn".equals(r.getParam("ValidCard"))
                && "True".equals(r.getParam("Secondary")) && "True".equals(r.getParam("Optional"))
                && "You".equals(r.getParam("OptionalDecider")) && "True".equals(r.getParam("CommanderMoveReplacement"))
                && "Hand,Library".equals(r.getParam("Destination"))
                && Set.of("Event", "ValidCard", "Secondary", "Optional", "OptionalDecider", "CommanderMoveReplacement", "Destination", "Description").containsAll(r.getMapParams().keySet())
                && sa != null && sa.getApi() == ApiType.ChangeZone && sa.getSubAbility() == null && sa.getTargets().isEmpty()
                && Map.of("DB", "ChangeZone", "Origin", "Battlefield,Graveyard,Exile,Library,Hand",
                    "Destination", "Command", "Defined", "ReplacedCard").equals(sa.getMapParams());
    }

    private static boolean supported(final Player ai, final ReplacementEffect own) {
        if (Thread.currentThread().isInterrupted() || ai.getGame().EXPERIMENTAL_RESTORE_SNAPSHOT) { return false; }
        for (final Card c : ai.getGame().getCardsIn(ZoneType.STATIC_ABILITIES_SOURCE_ZONES)) {
            if (c.isPhasedOut()) { continue; }
            for (final var r : c.getReplacementEffects()) {
                if (r == own || !ai.canDraw() || r.isSuppressed() || !r.zonesCheck(c.getZone()) || !r.requirementsCheck(ai.getGame())) { continue; }
                if (commanderReturn(c, r)) { continue; }
                if (Set.of("Draw", "DrawCards").contains(r.getMode().name())) { return false; }
                if (r.getMode().name().equals("Moved") && (!r.hasParam("Origin") || r.getParam("Origin").contains("Library")
                        || !r.hasParam("Destination") || r.getParam("Destination").contains("Hand") || r.getParam("Destination").contains("Library"))) { return false; }
            }
            for (final var t : c.getTriggers()) {
                if (t.isSuppressed() || !t.zonesCheck(c.getZone())) { continue; }
                if (t.getMode().name().equals("Drawn") || t.getMode().name().startsWith("Reveal")
                        || t.getMode().name().equals("Phase") && "Draw".equals(t.getParam("Phase"))
                        || drawEngine(t.ensureAbility(), Collections.newSetFromMap(new IdentityHashMap<>()))) { return false; }
            }
        }
        return true;
    }

    private static boolean castable(final Player ai, final SpellAbility sa) {
        try {
            final Card c = sa.getHostCard(); final var r = original(c);
            if (r == null || !sa.isSpell() || sa.getApi() != ApiType.PermanentNoncreature
                    || c.getController() != ai || c.getOwner() != ai || !c.isInZone(ZoneType.Hand)
                    || ai.getCardsIn(ZoneType.Hand).stream().noneMatch(x -> x == c)
                    || !ai.getGame().getPhaseHandler().is(PhaseType.MAIN2, ai) || !ai.getGame().getStack().isEmpty()
                    || ai.isCardInPlay(c.getName()) || !supported(ai, r)
                    || !sa.canPlay() || !sa.checkRestrictions(ai) || !sa.canCastTiming(ai)) { return false; }
            final GameCopier copy = new GameCopier(ai.getGame(), true); copy.makeCopy(null, ai);
            final Player payer = copy.find(ai); final var spell = sa.copy(copy.find(c), payer, false);
            return CommandBeaconAi.canPayProjection(payer, spell);
        } catch (final RuntimeException uncertain) { return false; }
    }

    public static AiAbilityDecision considerSpell(final Player ai, final SpellAbility sa) {
        PLANS.remove(sa); final boolean play = castable(ai, sa);
        if (play) { PLANS.put(sa, new Plan(new WeakReference<>(ai), new WeakReference<>(sa.getHostCard()), sa.getHostCard().getGameTimestamp())); }
        return new AiAbilityDecision(play ? 100 : 0, play ? AiPlayDecision.WillPlay : AiPlayDecision.CantPlayAi);
    }

    public static boolean preflight(final Player ai, final SpellAbility sa) {
        if (!applies(sa.getHostCard()) && !PLANS.containsKey(sa)) { return true; }
        final Plan p = PLANS.get(sa);
        return p != null && p.player().get() == ai && p.source().get() == sa.getHostCard()
                && p.stamp() == sa.getHostCard().getGameTimestamp() && castable(ai, sa);
    }

    private static boolean live(final Player ai, final SpellAbility sa) {
        final Card c = sa.getHostCard(); final var r = original(c);
        if (r == null || c.getController() != ai || !c.isInPlay() || c.isPhasedOut()
                || ai.getCardsIn(ZoneType.Battlefield).stream().noneMatch(x -> x == c)
                || !ai.canDraw() || r.isSuppressed() || !r.zonesCheck(c.getZone()) || !r.requirementsCheck(ai.getGame())
                || !choiceShape(sa) || sa.getReplacementEffect() != r || !supported(ai, r)) { return false; }
        final Object params = sa.getReplacingObject(AbilityKey.OriginalParams);
        return params instanceof Map<?, ?> m && m.get(AbilityKey.Affected) == ai;
    }

    public static boolean accept(final Player ai, final CardTraitBase effect, final SpellAbility sa, final GameEntity affected) {
        try { return affected == ai && effect instanceof ReplacementEffect r && sa != null
                && sa.getReplacementEffect() == r && live(ai, sa); }
        catch (final RuntimeException uncertain) { return false; }
    }

    public static SpellAbility choose(final Player ai, final SpellAbility sa, final List<? extends SpellAbility> offered) {
        // Once the engine has accepted an optional replacement, its choice is mandatory.
        // Only choose a native offered branch; never run candidate AI evaluators that inspect a library.
        final var branches = sa.getAdditionalAbilityList("Choices");
        if (!choiceShape(sa) || branches == null || !live(ai, sa)) { return offered.isEmpty() ? null : offered.get(0); }
        final boolean land = ai.getLandsInPlay().size() < 6 && ai.getCardsIn(ZoneType.Hand).stream().noneMatch(Card::isLand);
        final var desired = branches.get(land ? 0 : 1);
        if (offered.contains(desired)) { return desired; }
        for (final var b : branches) { if (offered.contains(b)) { return b; } }
        return null;
    }
}
