package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.game.Game;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.cost.CostSacrifice;
import forge.game.player.Player;
import forge.game.phase.PhaseType;
import forge.game.replacement.ReplacementEffect;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.zone.ZoneType;

/** Bounded opt-in commander-tax relief. All live costs and zone changes remain native. */
public final class CommandBeaconAi {
    private static final String CHOICE = "CommandBeaconChoice";
    private static final ThreadLocal<Game> PAYMENT_PROJECTION = new ThreadLocal<>();
    private CommandBeaconAi() { }

    public static boolean uses(final SpellAbility sa) {
        return sa != null && sa.isActivatedAbility() && sa.getApi() == ApiType.ChangeZone
                && "CommandBeacon".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()))
                && "Command".equals(sa.getParam("Origin")) && "Hand".equals(sa.getParam("Destination"))
                && "Card.IsCommander+YouOwn".equals(sa.getParam("ChangeType"))
                && sa.getPayCosts().hasTapCost()
                && sa.getPayCosts().hasSpecificCostType(CostSacrifice.class)
                && sa.getPayCosts().getCostPartByType(CostSacrifice.class).payCostFromSource()
                && "1".equals(sa.getPayCosts().getCostPartByType(CostSacrifice.class).getAmount())
                && "1".equals(sa.getParam("ChangeNum")) && sa.getSubAbility() == null;
    }

    private static boolean window(final Player ai, final SpellAbility sa) {
        final Card land = sa.getHostCard();
        return (ai.getGame().getPhaseHandler().is(PhaseType.MAIN1, ai)
                || ai.getGame().getPhaseHandler().is(PhaseType.MAIN2, ai))
                && ai.getGame().getStack().isEmpty() && land.isInPlay() && land.isLand()
                && sa.canPlay() && sa.checkRestrictions(ai)
                && land.getController() == ai && !land.isTapped() && !land.isPhasedOut()
                && land.canBeSacrificedBy(sa, false) && ai.getLandsInPlay().size() >= 5
                && !land.hasCardAttachments() && !uncertain(ai);
    }

    private static boolean uncertain(final Player ai) {
        // The private projection below changes zones without resolving triggers/replacements.
        // Hold positions where those omitted actions could change the forecast.
        for (final Card card : ai.getGame().getCardsIn(ZoneType.STATIC_ABILITIES_SOURCE_ZONES)) {
            if (card.isPhasedOut()) { continue; }
            for (final Trigger t : card.getTriggers()) {
                if (t.isSuppressed() || !t.zonesCheck(card.getZone())) { continue; }
                switch (t.getMode()) {
                    case ChangesZone: case ChangesZoneAll: case Sacrificed: case SacrificedOnce:
                    case Taps: case TapAll: case AbilityCast: case AbilityResolves: case SpellAbilityCast:
                        return true;
                    default: break;
                }
            }
            for (final ReplacementEffect r : card.getReplacementEffects()) {
                if (r.isSuppressed() || !r.zonesCheck(card.getZone()) || !r.requirementsCheck(ai.getGame())) { continue; }
                // Native optional commander replacement is wired by Player; its hand-return AI accepts Hand.
                if (card.isImmutable() && "Commander Effect".equals(card.getName())) { continue; }
                switch (r.getMode()) {
                    case Moved: case Tap: return true;
                    default: break;
                }
            }
        }
        return false;
    }

    private static boolean eligible(final Player ai, final Card commander) {
        return commander != null && commander.getOwner() == ai && commander.isCommander()
                && commander.isInZone(ZoneType.Command) && !commander.isImmutable()
                && ai.getCommanderCast(commander) >= 2;
    }

    private static boolean payableAfter(final Player ai, final SpellAbility beacon, final Card commander) {
        if (Thread.currentThread().isInterrupted() || ai.getGame().EXPERIMENTAL_RESTORE_SNAPSHOT) { return false; }
        try {
            return forecast(ai, beacon, commander);
        } catch (final RuntimeException unsupportedProjection) {
            // A partial or unsupported copy is uncertainty, never authorization to sacrifice.
            return false;
        }
    }

    private static boolean forecast(final Player ai, final SpellAbility beacon, final Card commander) {
        final GameCopier copier = new GameCopier(ai.getGame(), true);
        final Game projected = copier.makeCopy(null, ai);
        final Player payer = copier.find(ai);
        final Card land = copier.find(beacon.getHostCard());
        final Card returned = copier.find(commander);
        // Only the copy is edited. This is a forecast, not an activation or simulated decision.
        land.getZone().remove(land);
        payer.getZone(ZoneType.Graveyard).add(land);
        returned.getZone().remove(returned);
        payer.getZone(ZoneType.Hand).add(returned);
        projected.getAction().checkStaticAbilities();
        if (Thread.currentThread().isInterrupted()) { return false; }
        for (final Card c : projected.getCardsIn(ZoneType.Battlefield)) {
            if (c.isCreature() && c.getNetToughness() <= 0) { return false; }
        }
        for (final SpellAbility spell : returned.getSpellAbilities()) {
            if (!spell.isSpell() || spell.getPayCosts().hasXInAnyCostPart() || !spell.getPayCosts().isOnlyManaCost()) { continue; }
            spell.setActivatingPlayer(payer);
            if (!spell.canPlay() || !spell.checkRestrictions(payer)) { continue; }
            if (canPayProjection(payer, spell)) { return true; }
        }
        return false;
    }

    /** Fresh copies have no reservations. Avoid native reservation RNG during this read-only forecast. */
    public static boolean paymentProjection(final Player ai) {
        return PAYMENT_PROJECTION.get() == ai.getGame();
    }

    /** Native affordability on a private, hidden-information-pruned game with no live reservations. */
    public static boolean canPayProjection(final Player payer, final SpellAbility spell) {
        final Game previous = PAYMENT_PROJECTION.get();
        PAYMENT_PROJECTION.set(payer.getGame());
        try {
            return ComputerUtilCost.canPayCost(spell, payer, false);
        } finally {
            if (previous == null) { PAYMENT_PROJECTION.remove(); } else { PAYMENT_PROJECTION.set(previous); }
        }
    }

    /** Spend mana only on a private copy, using the same reservation-free native payment path. */
    static boolean payManaProjection(final Player payer, final SpellAbility ability) {
        final Game previous = PAYMENT_PROJECTION.get();
        PAYMENT_PROJECTION.set(payer.getGame());
        try {
            final var mana = ComputerUtilMana.calculateManaCost(ability.getPayCosts(), ability, payer, true, 0, false);
            return ComputerUtilMana.payManaCost(mana, ability, payer, false);
        } finally {
            if (previous == null) { PAYMENT_PROJECTION.remove(); } else { PAYMENT_PROJECTION.set(previous); }
        }
    }

    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        sa.setSVar(CHOICE, "");
        Card best = null;
        if (window(ai, sa)) {
            for (final Card c : ai.getCardsIn(ZoneType.Command)) {
                if (eligible(ai, c) && payableAfter(ai, sa, c)
                        && (best == null || ai.getCommanderCast(c) > ai.getCommanderCast(best)
                            || ai.getCommanderCast(c) == ai.getCommanderCast(best) && c.getId() < best.getId())) { best = c; }
            }
        }
        if (best == null) { return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi); }
        sa.setSVar(CHOICE, Integer.toString(best.getId()));
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    public static Card choose(final SpellAbility sa, final Iterable<Card> options) {
        for (final Card c : options) {
            if (Integer.toString(c.getId()).equals(sa.getSVar(CHOICE))) { return c; }
        }
        return null;
    }

    public static boolean preflight(final Player ai, final SpellAbility sa) {
        if (!uses(sa)) { return true; }
        final Card c = choose(sa, ai.getCardsIn(ZoneType.Command));
        return window(ai, sa) && eligible(ai, c) && payableAfter(ai, sa, c);
    }
}
