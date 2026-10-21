package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardLists;
import forge.game.cost.CostDiscard;
import forge.game.cost.CostPartMana;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.lang.ref.WeakReference;
import java.util.*;

/** Opt-in mode decisions. Original damage, basic selection, discard, payment and shuffle remain native. */
public final class TreacherousTerrainAi {
    private record Plan(WeakReference<Player> player, WeakReference<Card> source, long stamp, boolean spell) { }
    private static final Map<SpellAbility, Plan> PLANS = Collections.synchronizedMap(new WeakHashMap<>());
    private TreacherousTerrainAi() { }

    public static boolean applies(final SpellAbility sa) {
        return sa != null && "TreacherousTerrain".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()));
    }

    private static boolean original(final SpellAbility sa) {
        if (sa.getSubAbility() != null || !sa.getTargets().isEmpty() || sa.getPayCosts() == null
                || sa.getHostCard().isFaceDown() || sa.getHostCard().isCloned()) { return false; }
        if (sa.isSpell()) {
            final var dmg = sa.getAdditionalAbility("RepeatSubAbility");
            return sa.getApi() == ApiType.RepeatEach && "Player.Opponent".equals(sa.getParam("RepeatPlayers"))
                    && "DmgOpp".equals(sa.getParam("RepeatSubAbility")) && "True".equals(sa.getParam("DamageMap"))
                    && Set.of("SP", "RepeatPlayers", "RepeatSubAbility", "DamageMap", "SpellDescription").containsAll(sa.getMapParams().keySet())
                    && "Count$Valid Land.RememberedPlayerCtrl".equals(sa.getSVar("X"))
                    && dmg != null && dmg.getApi() == ApiType.DealDamage && dmg.getSubAbility() == null && dmg.getTargets().isEmpty()
                    && Map.of("DB", "DealDamage", "Defined", "Remembered", "NumDmg", "X").equals(dmg.getMapParams())
                    && sa.getPayCosts().hasOnlySpecificCostType(CostPartMana.class);
        }
        final var discard = sa.getPayCosts().getCostPartByType(CostDiscard.class);
        return sa.getApi() == ApiType.ChangeZone && "Hand".equals(sa.getParam("ActivationZone"))
                && "Library".equals(sa.getParam("Origin")) && "Hand".equals(sa.getParam("Destination"))
                && "Basic".equals(sa.getParam("ChangeType"))
                && Set.of("AB", "Cost", "ActivationZone", "PrecostDesc", "CostDesc", "Origin", "Destination", "ChangeType", "SpellDescription").containsAll(sa.getMapParams().keySet())
                && discard != null && discard.payCostFromSource() && "1".equals(discard.getAmount())
                && sa.getPayCosts().getCostParts().stream().allMatch(c -> c instanceof CostPartMana || c == discard)
                && "{2}".equals(sa.getPayCosts().getTotalMana().toString());
    }

    private static boolean known(final Player ai, final boolean cycle) {
        for (final Card c : ai.getGame().getCardsIn(ZoneType.STATIC_ABILITIES_SOURCE_ZONES)) {
            if (c.isPhasedOut()) { continue; }
            if (c.isFaceDown()) { return false; }
            for (final var r : c.getReplacementEffects()) {
                if (r.isSuppressed() || !r.zonesCheck(c.getZone()) || !r.requirementsCheck(ai.getGame())) { continue; }
                final String mode = r.getMode().name();
                if (Set.of("DamageDone", "DealtDamage", "LifeReduced", "PayLife", "GainLife", "Tap", "ProduceMana").contains(mode)) { return false; }
                if (cycle && (mode.contains("Discard") || mode.contains("Search") || mode.equals("Moved"))) {
                    // The native commander return can affect neither this noncommander nor an ordinary basic.
                    // Validate the complete native template rather than exempting its whole source.
                    final var s = r.getOverridingAbility();
                    final boolean commanderReturn = c.isImmutable() && "Commander Effect".equals(c.getName()) && mode.equals("Moved")
                            && Map.of("DB", "ChangeZone", "Origin", "Battlefield,Graveyard,Exile,Library,Hand", "Destination", "Command", "Defined", "ReplacedCard").equals(s == null ? null : s.getMapParams())
                            && s.getSubAbility() == null && s.getTargets().isEmpty()
                            && "Card.IsCommander+YouOwn".equals(r.getParam("ValidCard")) && "True".equals(r.getParam("CommanderMoveReplacement"))
                            && "Hand,Library".equals(r.getParam("Destination")) && "True".equals(r.getParam("Optional"))
                            && "You".equals(r.getParam("OptionalDecider")) && "True".equals(r.getParam("Secondary"))
                            && Set.of("Event", "ValidCard", "CommanderMoveReplacement", "Destination", "Optional", "OptionalDecider", "Secondary", "Description").containsAll(r.getMapParams().keySet());
                    if (!commanderReturn) { return false; }
                }
            }
            for (final var t : c.getTriggers()) {
                if (t.isSuppressed() || !t.zonesCheck(c.getZone())) { continue; }
                final String mode = t.getMode().name();
                if (mode.startsWith("Damage") || mode.contains("Life") || mode.startsWith("Tap")
                        || mode.contains("Cast") || mode.equals("AbilityResolves") || mode.equals("AbilityTriggered")
                        || cycle && (mode.contains("Search") || mode.contains("Discard") || mode.contains("Cycl")
                            || mode.startsWith("ChangesZone") && (!t.hasParam("Origin") || t.getParam("Origin").matches(".*(Any|Hand|Library).*")))) { return false; }
            }
            for (final var st : c.getStaticAbilities()) {
                if (!st.isSuppressed() && st.zonesCheck() && st.getMode().stream().anyMatch(m ->
                        m.name().contains("Damage") || m.name().contains("Life") || m.name().contains("Poison"))) { return false; }
            }
            for (final var mana : c.getManaAbilities()) {
                if (c.isInPlay() && mana.getSubAbility() != null) { return false; }
            }
        }
        return true;
    }

    private static boolean window(final Player ai, final SpellAbility sa) {
        final Card c = sa.getHostCard(); final var ph = ai.getGame().getPhaseHandler();
        if (!applies(sa) || !original(sa) || Thread.currentThread().isInterrupted() || ai.getGame().EXPERIMENTAL_RESTORE_SNAPSHOT
                || !ai.getGame().getStack().isEmpty() || c.getOwner() != ai || c.getController() != ai || c.isCommander()
                || !c.isInZone(ZoneType.Hand) || ai.getCardsIn(ZoneType.Hand).stream().noneMatch(x -> x == c)
                || !ph.isPlayerTurn(ai) || !(ph.is(PhaseType.MAIN2) || !sa.isSpell() && ph.is(PhaseType.MAIN1))
                || !sa.canPlay() || !sa.checkRestrictions(ai) || !sa.canCastTiming(ai) || !known(ai, !sa.isSpell())) { return false; }
        for (final Player p : ai.getGame().getPlayers()) { if (!p.isOpponentOf(ai) && (p.getLife() < 5 || p.hasLost())) { return false; } }
        return !c.hasKeyword(Keyword.INFECT) && !c.hasKeyword(Keyword.LIFELINK) && !c.hasKeyword(Keyword.WITHER);
    }

    private static boolean forecast(final Player ai, final SpellAbility sa) {
        if (!window(ai, sa)) { return false; }
        final GameCopier copier = new GameCopier(ai.getGame(), true);
        final var game = copier.makeCopy(null, ai); final Player payer = copier.find(ai);
        final var action = sa.copy(copier.find(sa.getHostCard()), payer, false);
        final var cost = forge.game.cost.CostAdjustment.adjust(action.getPayCosts(), action, false);
        if (cost.getCostParts().stream().anyMatch(c -> !(c instanceof CostPartMana) && (sa.isSpell() || !(c instanceof CostDiscard)))
                || !action.canPlay() || !action.checkRestrictions(payer) || !action.canCastTiming(payer)
                || !CommandBeaconAi.canPayProjection(payer, action) || !CommandBeaconAi.payManaProjection(payer, action)) { return false; }
        if (!sa.isSpell()) {
            // Retain the native own-library availability boundary; never inspect order or opposing hidden cards.
            if (ai.getLandsInPlay().size() >= 6 || ai.getCardsIn(ZoneType.Hand).stream().anyMatch(Card::isLand)
                    || !ai.canSearchLibraryWith(sa, ai) || !ai.canDiscardBy(sa, false)
                    || CardLists.getValidCards(ai.getCardsIn(ZoneType.Library), sa.getParam("ChangeType"), ai, sa.getHostCard(), sa).isEmpty()) { return false; }
            final var cast = sa.getHostCard().getFirstSpellAbility().copy(ai);
            cast.setActivatingPlayer(ai);
            return !forecast(ai, cast);
        }
        final Map<Player,Integer> before = new IdentityHashMap<>();
        for (final Player p : game.getPlayers()) { before.put(p, p.getLife()); }
        final List<Card> board = new ArrayList<>(game.getCardsIn(ZoneType.Battlefield));
        AbilityUtils.resolve(action);
        game.getAction().checkStaticAbilities();
        int total = 0; boolean lethal = false;
        for (final Player p : ai.getGame().getPlayers()) {
            final Player projected = copier.find(p); final int loss = before.get(projected) - projected.getLife();
            if (!p.isOpponentOf(ai)) {
                if (loss != 0 || projected.getLife() < 5 || projected.hasLost()) { return false; }
            } else {
                if (loss < 0) { return false; }
                total += loss;
                lethal |= loss > 0 && projected.getLife() <= 0 && !projected.cantLoseForZeroOrLessLife() && !payer.cantWin();
            }
        }
        for (final Card c : board) { if (!c.isInPlay()) { return false; } }
        return (lethal || total >= 8) && !Thread.currentThread().isInterrupted();
    }

    private static boolean safe(final Player ai, final SpellAbility sa) {
        try { return forecast(ai, sa); } catch (final RuntimeException unknown) { return false; }
    }

    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        PLANS.remove(sa); final boolean play = safe(ai, sa);
        if (play) { PLANS.put(sa, new Plan(new WeakReference<>(ai), new WeakReference<>(sa.getHostCard()), sa.getHostCard().getGameTimestamp(), sa.isSpell())); }
        return new AiAbilityDecision(play ? 100 : 0, play ? AiPlayDecision.WillPlay : AiPlayDecision.CantPlayAi);
    }

    public static boolean preflight(final Player ai, final SpellAbility sa) {
        if (!applies(sa) && !PLANS.containsKey(sa)) { return true; }
        final Plan p = PLANS.get(sa);
        return p != null && p.player().get() == ai && p.source().get() == sa.getHostCard()
                && p.stamp() == sa.getHostCard().getGameTimestamp() && p.spell() == sa.isSpell() && safe(ai, sa);
    }
}
