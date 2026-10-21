package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.lang.ref.WeakReference;
import java.util.*;

/** Bounded, opt-in protection removal. Never selects or forces the subsequent removal spell. */
public final class ArcaneLighthouseAi {
    private record Choice(Card removal, Card target) { }
    private record Plan(WeakReference<Player> player, WeakReference<Card> source, long sourceStamp,
                        WeakReference<Card> removal, long removalStamp, WeakReference<Card> target, long targetStamp) { }
    private static final Map<SpellAbility, Plan> PLANS = Collections.synchronizedMap(new WeakHashMap<>());
    private ArcaneLighthouseAi() { }

    public static boolean uses(final SpellAbility sa) {
        return sa != null && sa.isActivatedAbility() && sa.getApi() == ApiType.AnimateAll
                && "ArcaneLighthouse".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()))
                && sa.getHostCard().isLand() && "Creature.OppCtrl".equals(sa.getParam("ValidCards"))
                && "Hexproof & Shroud".equals(sa.getParam("RemoveKeywords"))
                && "Hexproof & Shroud".equals(sa.getParam("CantHaveKeyword"))
                && sa.getSubAbility() == null && !sa.usesTargeting()
                && sa.getPayCosts().hasTapCost() && !sa.getPayCosts().hasXInAnyCostPart()
                && sa.getPayCosts().getCostParts().stream().allMatch(p -> p instanceof forge.game.cost.CostTap
                    || p instanceof forge.game.cost.CostPartMana);
    }

    private static boolean window(final Player ai, final SpellAbility sa) {
        final var ph = ai.getGame().getPhaseHandler();
        return uses(sa) && !Thread.currentThread().isInterrupted() && !ai.getGame().EXPERIMENTAL_RESTORE_SNAPSHOT
                && ai.getGame().getStack().isEmpty() && (ph.is(PhaseType.MAIN1, ai) || ph.is(PhaseType.MAIN2, ai))
                && sa.getHostCard().getController() == ai && sa.getHostCard().isInPlay()
                && ai.getGame().getCardsIn(ZoneType.Battlefield).stream().anyMatch(c -> c == sa.getHostCard())
                && !sa.getHostCard().isTapped() && !sa.getHostCard().isPhasedOut() && sa.canPlay() && sa.checkRestrictions(ai);
    }

    private static boolean known(final Player ai) {
        // The preview omits responses and triggered consequences; defer active engines of those kinds.
        for (final Card c : ai.getGame().getCardsIn(ZoneType.STATIC_ABILITIES_SOURCE_ZONES)) {
            if (c.isPhasedOut()) { continue; }
            if (c.isFaceDown()) { return false; }
            for (final var t : c.getTriggers()) {
                if (t.isSuppressed() || !t.zonesCheck(c.getZone())) { continue; }
                final String mode = t.getMode().name();
                if (mode.contains("Target") || mode.contains("Cast") || mode.equals("AbilityResolves")
                        || mode.equals("Taps") || mode.equals("TapAll") || mode.equals("Sacrificed")
                        || mode.equals("SacrificedOnce") || mode.equals("AbilityTriggered")) { return false; }
                if (mode.startsWith("ChangesZone") && !"Battlefield".equals(t.getParam("Destination"))) { return false; }
            }
            for (final var r : c.getReplacementEffects()) {
                if (r.isSuppressed() || !r.zonesCheck(c.getZone()) || !r.requirementsCheck(ai.getGame())) { continue; }
                if (c.isImmutable() && "Commander Effect".equals(c.getName())) { continue; }
                if (r.getMode().name().equals("Moved") && "Card.Self".equals(r.getParam("ValidCard"))
                        && "Battlefield".equals(r.getParam("Destination"))) { continue; }
                if (r.getMode().name().equals("Moved") || r.getMode().name().equals("Tap")) { return false; }
            }
        }
        return true;
    }

    private static boolean simpleRemoval(final SpellAbility s) {
        return s.isSpell() && s.usesTargeting() && s.getMinTargets() == 1 && s.getMaxTargets() == 1
                && Set.of("SP", "ValidTgts", "TgtPrompt", "SpellDescription", "NoRegen", "Origin", "Destination")
                    .containsAll(s.getMapParams().keySet())
                && s.getSubAbility() == null && s.getPayCosts() != null && s.getPayCosts().isOnlyManaCost()
                && !s.getPayCosts().hasXInAnyCostPart() && !s.hasParam("AILogic")
                && (s.getApi() == ApiType.Destroy || s.getApi() == ApiType.ChangeZone
                    && "Battlefield".equals(s.getParam("Origin")) && "Exile".equals(s.getParam("Destination")))
                && !ComputerUtilCard.isCardRemAIDeck(s.getHostCard());
    }

    private static boolean useful(final Player ai, final Card c) {
        return c.isCreature() && c.getController().isOpponentOf(ai) && !c.isPhasedOut()
                && (c.hasKeyword(Keyword.HEXPROOF) || c.hasKeyword(Keyword.SHROUD))
                && ComputerUtilCard.evaluateCreature(c) >= 200;
    }

    private static Choice choice(final Player ai, final SpellAbility sa) {
        if (!window(ai, sa) || !known(ai)) { return null; }
        final List<Card> threats = new ArrayList<>();
        for (final Card c : ai.getGame().getCardsIn(ZoneType.Battlefield)) { if (useful(ai, c)) { threats.add(c); } }
        threats.sort(Comparator.comparingInt((Card c) -> ComputerUtilCard.evaluateCreature(c)).reversed().thenComparingInt(Card::getId));
        if (threats.isEmpty()) { return null; }
        final List<Card> hand = new ArrayList<>(ai.getCardsIn(ZoneType.Hand));
        hand.sort(Comparator.comparingInt(Card::getId));
        for (final Card target : threats) {
            for (final Card removal : hand) {
                for (final SpellAbility original : removal.getSpellAbilities()) {
                    if (!simpleRemoval(original)) { continue; }
                    // The copier does not preserve regeneration shields. Read those public facts first;
                    // do not invoke regeneration AI (which evaluates costs and reservations) on live objects.
                    if (original.getApi() == ApiType.Destroy && (!target.canBeDestroyed() || target.getShieldCount() > 0
                            || target.getCounters(forge.game.card.CounterEnumType.SHIELD) > 0
                            || ai.getOpponents().getCardsIn(ZoneType.Battlefield).stream().anyMatch(c ->
                                c.getSpellAbilities().stream().anyMatch(a -> a.getApi() == ApiType.Regenerate)))) { continue; }
                    try {
                        final GameCopier copier = new GameCopier(ai.getGame(), true);
                        final var projected = copier.makeCopy(null, ai);
                        final Player payer = copier.find(ai);
                        final Card land = copier.find(sa.getHostCard());
                        final Card victim = copier.find(target);
                        final SpellAbility spell = original.copy(copier.find(removal), payer, false);
                        if (spell.canTarget(victim)) { continue; } // Lighthouse must actually unlock this target.
                        final SpellAbility activation = sa.copy(land, payer, false);
                        land.setTapped(true); // unavailable to either native mana payment
                        if (!CommandBeaconAi.payManaProjection(payer, activation)) { continue; }
                        AbilityUtils.resolve(activation); // original keyword/layer effect, in the private copy only
                        projected.getAction().checkStaticAbilities();
                        if (!spell.canTarget(victim) || !spell.canPlay() || !spell.checkRestrictions(payer)
                                || !spell.canCastTiming(payer)) { continue; }
                        if (spell.getApi() == ApiType.Destroy && (!victim.canBeDestroyed() || victim.getShieldCount() > 0
                                || victim.getCounters(forge.game.card.CounterEnumType.SHIELD) > 0)) { continue; }
                        spell.resetTargets(); spell.getTargets().add(victim);
                        if (spell.isTargetNumberValid() && CommandBeaconAi.canPayProjection(payer, spell)
                                && !Thread.currentThread().isInterrupted()) { return new Choice(removal, target); }
                    } catch (final RuntimeException unsupportedProjection) { /* uncertainty never authorizes activation */ }
                }
            }
        }
        return null;
    }

    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        PLANS.remove(sa);
        final Choice c = choice(ai, sa);
        if (c == null) { return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi); }
        PLANS.put(sa, new Plan(new WeakReference<>(ai), new WeakReference<>(sa.getHostCard()), sa.getHostCard().getGameTimestamp(),
                new WeakReference<>(c.removal()), c.removal().getGameTimestamp(), new WeakReference<>(c.target()), c.target().getGameTimestamp()));
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    public static boolean preflight(final Player ai, final SpellAbility sa) {
        if (!uses(sa) && !PLANS.containsKey(sa)) { return true; }
        final Plan p = PLANS.get(sa);
        if (p == null || p.player().get() != ai || p.source().get() != sa.getHostCard()
                || p.sourceStamp() != sa.getHostCard().getGameTimestamp()) { return false; }
        final Choice c = choice(ai, sa);
        return c != null && c.removal() == p.removal().get() && c.removal().getGameTimestamp() == p.removalStamp()
                && c.target() == p.target().get() && c.target().getGameTimestamp() == p.targetStamp();
    }
}
