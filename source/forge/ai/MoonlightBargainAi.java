package forge.ai;

import forge.game.ability.ApiType;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.cost.Cost;
import forge.game.cost.CostPayLife;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Opt-in end-step refuel. Keep choices use only the cards the native effect has already shown. */
public final class MoonlightBargainAi {
    private MoonlightBargainAi() { }
    private record Key(int id, long stamp) {
        static Key of(final Card card) { return new Key(card.getId(), card.getGameTimestamp()); }
    }
    // Primitive identities only: values must not retain a game or card. Native LKI ability copies
    // may retain ability IDs, so a copied game must never share a live resolution's keep set.
    private record Keeps(Key source, Set<Key> cards) { }
    private static final Map<Game, Map<Integer, Keeps>> KEEPS = Collections.synchronizedMap(new WeakHashMap<>());

    private static Map<Integer, Keeps> keepsFor(final Player ai) {
        return KEEPS.computeIfAbsent(ai.getGame(), ignored -> Collections.synchronizedMap(new HashMap<>()));
    }

    private static boolean tagged(final SpellAbility sa) {
        return sa != null && "Moonlight Bargain".equals(sa.getHostCard().getName())
                && "MoonlightBargain".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()));
    }

    public static boolean usesSpell(final SpellAbility sa) {
        return tagged(sa) && sa.isSpell() && sa.getApi() == ApiType.PeekAndReveal;
    }

    private static boolean original(final SpellAbility root) {
        return usesSpell(root) && "5".equals(root.getParam("PeekAmount"))
                && "True".equals(root.getParam("NoReveal")) && "True".equals(root.getParam("RememberPeeked"));
    }

    public static AiAbilityDecision decide(final Player ai, final SpellAbility sa) {
        if (!usesSpell(sa)) { return null; }
        final Card source = sa.getHostCard();
        final var ph = ai.getGame().getPhaseHandler();
        final boolean play = original(sa) && source.isInZone(ZoneType.Hand) && source.getOwner() == ai
                && ai.getGame().getCardState(source) == source
                && ph.is(PhaseType.END_OF_TURN) && !ph.isPlayerTurn(ai) && ph.getNextTurn() == ai
                && ai.getGame().getStack().isEmpty() && ai.getCardsIn(ZoneType.Hand).size() <= 3
                && ai.getCardsIn(ZoneType.Library).size() >= 7 && ai.getLife() >= 14
                && sa.checkRestrictions(ai) && sa.getPayCosts().isOnlyManaCost()
                && ComputerUtilCost.canPayCost(sa, ai, false);
        return new AiAbilityDecision(play ? 100 : 0, play ? AiPlayDecision.WillPlay : AiPlayDecision.CantPlayAi);
    }

    public static boolean preflight(final Player ai, final SpellAbility sa) {
        if (!usesSpell(sa)) { return true; }
        if (!decide(ai, sa).willingToPlay()) { return false; }
        keepsFor(ai).remove(sa.getRootAbility().getId()); // reused ability starts a fresh resolution budget
        return true;
    }

    private static Keeps choose(final Player ai, final Card source) {
        final List<Card> shown = new ArrayList<>();
        for (final Object object : source.getRemembered()) {
            if (!(object instanceof Card card)) { return new Keeps(Key.of(source), Set.of()); }
            shown.add(card);
        }
        if (shown.isEmpty() || shown.size() > 5) { return new Keeps(Key.of(source), Set.of()); }
        final int slots = Math.min(3, Math.max(0, (ai.getLife() - 10) / 2));
        final Set<Key> selected = new HashSet<>();
        if (slots > 0 && ai.getLandsInPlay().size() < 6
                && ai.getCardsIn(ZoneType.Hand).stream().noneMatch(Card::isLand)) {
            shown.stream().filter(Card::isLand).min(Comparator.comparing(card -> card.getName()))
                    .ifPresent(card -> selected.add(Key.of(card)));
        }
        final int ceiling = ai.getLandsInPlay().size() + 1;
        shown.stream().filter(card -> !card.isLand())
                .sorted(Comparator.<Card>comparingInt(card -> card.getCMC() <= ceiling ? 0 : 1)
                        .thenComparingInt(card -> card.getCMC() <= ceiling ? -card.getCMC() : card.getCMC())
                        .thenComparing(card -> card.getName()))
                .limit(Math.max(0, slots - selected.size())).forEach(card -> selected.add(Key.of(card)));
        return new Keeps(Key.of(source), Set.copyOf(selected));
    }

    /** Null delegates all other cards/profiles to native AI. The original resolver pays each card once. */
    public static Boolean willPay(final Player ai, final SpellAbility sa, final Cost cost, final boolean alreadyPaid) {
        if (!tagged(sa)) { return null; }
        final SpellAbility root = sa.getRootAbility();
        final Card source = sa.getHostCard();
        if (!original(root) || sa.getApi() != ApiType.ChangeZone || ai != sa.getActivatingPlayer()
                || !source.isInZone(ZoneType.Stack) || alreadyPaid
                || !"Imprinted".equals(sa.getParam("Defined")) || !"Library".equals(sa.getParam("Origin"))
                || !"Graveyard".equals(sa.getParam("Destination")) || !"PayLife<2>".equals(sa.getParam("UnlessCost"))
                || cost == null || cost.getCostParts().size() != 1 || !cost.hasSpecificCostType(CostPayLife.class)
                || cost.getCostPartByType(CostPayLife.class).getAbilityAmount(sa) != 2
                || source.getImprintedCards().size() != 1 || ai.getLife() - 2 < 10
                || !ComputerUtilCost.canPayCost(cost, sa, ai, true)) {
            return false;
        }
        final Map<Integer, Keeps> plans = keepsFor(ai);
        Keeps keeps = plans.get(root.getId());
        if (keeps == null || !keeps.source().equals(Key.of(source))) {
            keeps = choose(ai, source);
            plans.put(root.getId(), keeps);
        }
        return keeps.cards().contains(Key.of(source.getImprintedCards().getFirst()));
    }
}
