package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.cost.CostExile;
import forge.game.cost.CostPartMana;
import forge.game.cost.CostTap;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.replacement.ReplacementType;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import java.lang.ref.WeakReference;
import java.util.*;

/** Bounded draw and explicitly protected graveyard payment. All actual costs/effects stay native. */
public final class SkeletalScryingAi {
    private record Choice(int x, List<Card> cards) { }
    private record Plan(WeakReference<Player> player, WeakReference<Card> source, int sourceId, long sourceStamp,
                        int turn, PhaseType phase, int x, List<WeakReference<Card>> cards,
                        List<Long> stamps, boolean authorized) { }
    private static final Map<SpellAbility, Plan> PLANS = Collections.synchronizedMap(new WeakHashMap<>());
    private SkeletalScryingAi() { }

    public static boolean uses(final SpellAbility sa) {
        if (sa == null || !sa.isSpell() || sa.getApi() != ApiType.Draw
                || !"SkeletalScrying".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()))
                || !"X".equals(sa.getParam("NumCards")) || !"Count$xPaid".equals(sa.getSVar("X"))
                || sa.usesTargeting() || sa.getSubAbility() == null
                || sa.getSubAbility().getApi() != ApiType.LoseLife
                || !"X".equals(sa.getSubAbility().getParam("LifeAmount"))
                || sa.getSubAbility().getSubAbility() != null || sa.getPayCosts() == null) { return false; }
        final CostExile exile = sa.getPayCosts().getCostPartByType(CostExile.class);
        return exile != null && exile.zoneRestriction == 1 && exile.getFrom().equals(List.of(ZoneType.Graveyard))
                && "X".equals(exile.getAmount()) && "Card".equals(exile.getType())
                && sa.getPayCosts().getCostParts().size() == 2
                && sa.getPayCosts().getCostParts().stream().allMatch(c -> c instanceof CostExile || c instanceof CostPartMana);
    }

    private static boolean window(final Player ai, final SpellAbility sa) {
        final var ph = ai.getGame().getPhaseHandler();
        return !Thread.currentThread().isInterrupted() && sa.getHostCard().isInZone(ZoneType.Hand)
                && ai.getCardsIn(ZoneType.Hand).stream().anyMatch(c -> c == sa.getHostCard())
                && sa.getHostCard().getController() == ai && sa.getHostCard().getOwner() == ai
                && ai.getCardsIn(ZoneType.Hand).size() <= 3 && ai.getGame().getStack().isEmpty()
                && (ph.is(PhaseType.MAIN2, ai) || ph.is(PhaseType.END_OF_TURN)
                    && !ph.isPlayerTurn(ai) && ph.getNextTurn() == ai);
    }

    private static boolean known(final Player ai) {
        for (final Card card : ai.getGame().getCardsIn(List.of(ZoneType.Battlefield, ZoneType.Graveyard, ZoneType.Command))) {
            if (card.isPhasedOut()) { continue; }
            for (final var trigger : card.getTriggers()) {
                if (trigger.isSuppressed() || !trigger.zonesCheck(card.getZone())) { continue; }
                final TriggerType type = trigger.getMode();
                if (type == TriggerType.Drawn || type == TriggerType.LifeLost || type == TriggerType.LifeLostAll
                        || type == TriggerType.Exiled || type == TriggerType.SpellCast
                        || type == TriggerType.SpellCastOrCopy || type == TriggerType.SpellAbilityCast
                        || type == TriggerType.Taps || type == TriggerType.TapsForMana) { return false; }
                if (type == TriggerType.ChangesZone || type == TriggerType.ChangesZoneAll) {
                    final String from = trigger.getParamOrDefault("Origin", "Any");
                    final String to = trigger.getParamOrDefault("Destination", "Any");
                    if (zone(from, "Graveyard") && zone(to, "Exile")
                            || zone(from, "Library") && zone(to, "Hand")
                            || zone(from, "Hand") && zone(to, "Stack")
                            || zone(from, "Stack") && zone(to, "Graveyard")) { return false; }
                }
            }
            for (final var re : card.getReplacementEffects()) {
                if (re.isSuppressed() || !re.getParamOrDefault("ActiveZones", "Battlefield").contains(card.getZone().getZoneType().name())
                        || !re.requirementsCheck(ai.getGame())) { continue; }
                if (card.isImmutable() && "Commander Effect".equals(card.getName())) { continue; }
                final ReplacementType type = re.getMode();
                if (type == ReplacementType.Draw || type == ReplacementType.DrawCards || type == ReplacementType.LifeReduced
                        || type == ReplacementType.PayLife || type == ReplacementType.Moved) { return false; }
            }
            if (card.getController() == ai && card.isInPlay() && card.getManaAbilities().stream().anyMatch(m ->
                    m.getSubAbility() != null || m.getPayCosts().getCostParts().stream()
                        .anyMatch(c -> !(c instanceof CostPartMana) && !(c instanceof CostTap)))) { return false; }
        }
        return true;
    }

    private static boolean zone(final String value, final String zone) {
        return "Any".equals(value) || value.contains(zone);
    }

    public static boolean expendable(final Player ai, final SpellAbility sa, final Card card) {
        if (card.getGame() != ai.getGame() || card.getOwner() != ai
                || ai.getCardsIn(ZoneType.Graveyard).stream().noneMatch(c -> c == card)
                || !card.canExiledBy(sa, false) || !(card.isBasicLand() || !card.isPermanent()
                    && (card.isInstant() || card.isSorcery())) || !card.mayPlay(ai).isEmpty()) { return false; }
        for (final Keyword key : List.of(Keyword.FLASHBACK, Keyword.JUMP_START, Keyword.RETRACE, Keyword.DREDGE,
                Keyword.ESCAPE, Keyword.AFTERMATH, Keyword.DISTURB, Keyword.RECOVER)) {
            if (card.hasKeyword(key)) { return false; }
        }
        if (card.getSpellAbilities().stream().anyMatch(a -> a.getRestrictions() != null
                && a.getRestrictions().getZone() == ZoneType.Graveyard)
                || card.getTriggers().stream().anyMatch(t -> !t.isSuppressed() && t.zonesCheck(card.getZone()))
                || card.getReplacementEffects().stream().anyMatch(r -> !r.isSuppressed()
                    && r.getParamOrDefault("ActiveZones", "Battlefield").contains("Graveyard"))
                || card.getStaticAbilities().stream().anyMatch(s -> !s.isSuppressed()
                    && s.getParamOrDefault("EffectZone", "Battlefield").contains("Graveyard"))) { return false; }
        return true;
    }

    private static List<Card> candidates(final Player ai, final SpellAbility sa) {
        final List<Card> result = new ArrayList<>();
        for (final Card card : ai.getCardsIn(ZoneType.Graveyard)) { if (expendable(ai, sa, card)) { result.add(card); } }
        result.sort(Comparator.comparingInt((Card c) -> c.isBasicLand() ? 0 : 1).thenComparingInt(Card::getId));
        return result;
    }

    private static Choice choice(final Player ai, final SpellAbility sa) {
        if (!window(ai, sa) || !known(ai) || !ai.canDraw()) { return null; }
        final List<Card> cards = candidates(ai, sa);
        final int maximum = Math.min(3, Math.min(cards.size(), Math.min(ai.getLife() - 5,
                Math.min(ai.getCardsIn(ZoneType.Library).size() - 4, ai.getMaxHandSize() - ai.getCardsIn(ZoneType.Hand).size() + 1))));
        if (maximum < 2) { return null; }
        try {
            final GameCopier copier = new GameCopier(ai.getGame(), true);
            copier.makeCopy(null, ai);
            final Player payer = copier.find(ai);
            final SpellAbility spell = sa.copy(copier.find(sa.getHostCard()), payer, false);
            for (int x = maximum; x >= 2; x--) {
                if (Thread.currentThread().isInterrupted()) { return null; }
                spell.setXManaCostPaid(x);
                if (CommandBeaconAi.canPayProjection(payer, spell)) { return new Choice(x, List.copyOf(cards.subList(0, x))); }
            }
        } catch (final RuntimeException unsupportedProjection) { return null; }
        return null;
    }

    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        PLANS.remove(sa);
        final Choice choice = uses(sa) ? choice(ai, sa) : null;
        if (choice == null) { return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi); }
        sa.setXManaCostPaid(choice.x());
        PLANS.put(sa, new Plan(new WeakReference<>(ai), new WeakReference<>(sa.getHostCard()), sa.getHostCard().getId(), sa.getHostCard().getGameTimestamp(),
                ai.getGame().getPhaseHandler().getTurn(), ai.getGame().getPhaseHandler().getPhase(), choice.x(),
                choice.cards().stream().map(WeakReference::new).toList(), choice.cards().stream().map(Card::getGameTimestamp).toList(), false));
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    private static CardCollection selected(final Player ai, final SpellAbility sa, final Plan plan) {
        if (Thread.currentThread().isInterrupted() || plan == null || !uses(sa) || plan.player().get() != ai || sa.getHostCard().getGame() != ai.getGame()
                || plan.sourceId() != sa.getHostCard().getId() || sa.getHostCard().getController() != ai
                || !Objects.equals(sa.getXManaCostPaid(), plan.x()) || !known(ai) || !ai.canDraw()
                || ai.getLife() - plan.x() < 5 || ai.getCardsIn(ZoneType.Library).size() - plan.x() < 4
                || ai.getGame().getPhaseHandler().getTurn() != plan.turn()
                || ai.getGame().getPhaseHandler().getPhase() != plan.phase()) { return null; }
        final CardCollection result = new CardCollection();
        for (int i = 0; i < plan.cards().size(); i++) {
            final Card card = plan.cards().get(i).get();
            if (card == null || card.getGameTimestamp() != plan.stamps().get(i) || !expendable(ai, sa, card)) { return null; }
            result.add(card);
        }
        return result;
    }

    public static boolean handlesPayment(final SpellAbility sa) { return uses(sa) || PLANS.containsKey(sa); }

    public static CardCollection payment(final Player ai, final SpellAbility sa, final Iterable<Card> allowed, final int amount) {
        final Plan plan = PLANS.get(sa);
        final CardCollection cards = selected(ai, sa, plan);
        final CardCollection valid = new CardCollection(allowed);
        if (cards == null || amount != plan.x() || !valid.containsAll(cards)) { return null; }
        if (sa.getHostCard().isInZone(ZoneType.Stack)) { return plan.authorized() ? cards : null; }
        return sa.getHostCard() == plan.source().get() && sa.getHostCard().getGameTimestamp() == plan.sourceStamp()
                && window(ai, sa) ? cards : null;
    }

    public static boolean preflight(final Player ai, final SpellAbility sa) {
        if (!handlesPayment(sa)) { return true; }
        final Plan plan = PLANS.get(sa);
        final CardCollection cards = selected(ai, sa, plan);
        if (cards == null || sa.getHostCard() != plan.source().get() || sa.getHostCard().getGameTimestamp() != plan.sourceStamp()
                || ai.getGame().getPhaseHandler().getPhase() != plan.phase() || !sa.canCastTiming(ai)) { return false; }
        final Choice fresh = choice(ai, sa);
        if (fresh == null || fresh.x() != plan.x() || fresh.cards().size() != cards.size()) { return false; }
        for (int i = 0; i < cards.size(); i++) { if (fresh.cards().get(i) != cards.get(i)) { return false; } }
        PLANS.put(sa, new Plan(plan.player(), plan.source(), plan.sourceId(), plan.sourceStamp(), plan.turn(), plan.phase(), plan.x(), plan.cards(), plan.stamps(), true));
        return true;
    }
}
