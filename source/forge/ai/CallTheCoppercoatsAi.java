package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.replacement.ReplacementType;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbilityMode;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Opt-in bounded army building; all costs, Strive adjustments and actual tokens remain native. */
public final class CallTheCoppercoatsAi {
    private CallTheCoppercoatsAi() { }

    public static boolean uses(final SpellAbility sa) {
        return sa != null && sa.isSpell() && sa.getApi() == ApiType.Token && sa.getHostCard() != null
                && "CallTheCoppercoats".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()))
                && "X".equals(sa.getParam("TokenAmount")) && "w_1_1_human_soldier".equals(sa.getParam("TokenScript"))
                && "Opponent".equals(sa.getParam("ValidTgts")) && sa.getSubAbility() == null;
    }

    private static boolean window(final Player ai) {
        final var ph = ai.getGame().getPhaseHandler();
        return !Thread.currentThread().isInterrupted() && ai.getGame().getStack().isEmpty()
                && !ai.getGame().EXPERIMENTAL_RESTORE_SNAPSHOT
                && (ph.is(PhaseType.MAIN2, ai) || ph.is(PhaseType.END_OF_TURN)
                    && ph.getPlayerTurn() != ai && ph.getNextTurn() == ai);
    }

    private static boolean known(final Player ai) {
        for (final Card card : ai.getGame().getCardsIn(ZoneType.STATIC_ABILITIES_SOURCE_ZONES)) {
            if (card.isPhasedOut()) { continue; }
            for (final var trigger : card.getTriggers()) {
                if (trigger.isSuppressed() || !trigger.zonesCheck(card.getZone())) { continue; }
                final TriggerType type = trigger.getMode();
                if (type == TriggerType.SpellCast || type == TriggerType.SpellCastOrCopy
                        || type == TriggerType.SpellAbilityCast || type == TriggerType.BecomesTarget
                        || type == TriggerType.BecomesTargetOnce || type == TriggerType.Taps || type == TriggerType.TapAll
                        || type == TriggerType.AbilityCast || type == TriggerType.AbilityResolves) { return false; }
                if ((type == TriggerType.ChangesZone || type == TriggerType.ChangesZoneAll)
                        && !"Card.Self".equals(trigger.getParam("ValidCard"))
                        && (trigger.getParamOrDefault("Destination", "Any").contains("Battlefield")
                            || "Any".equals(trigger.getParamOrDefault("Destination", "Any")))) { return false; }
            }
            for (final var replacement : card.getReplacementEffects()) {
                if (replacement.isSuppressed() || !replacement.zonesCheck(card.getZone())
                        || !replacement.requirementsCheck(ai.getGame())) { continue; }
                if (card.isImmutable() && "Commander Effect".equals(card.getName())) { continue; }
                if (replacement.getMode() == ReplacementType.CreateToken || replacement.getMode() == ReplacementType.Moved
                        || replacement.getMode() == ReplacementType.Tap) { return false; }
            }
            if (card.isInPlay() && card.getStaticAbilities().stream().anyMatch(st -> !st.isSuppressed()
                    && st.getMode().contains(StaticAbilityMode.Continuous)
                    && (st.hasParam("AddPower") || st.hasParam("AddToughness") || st.hasParam("SetPower")
                        || st.hasParam("SetToughness") || st.hasParam("SetPowerToughness")))) { return false; }
        }
        return true;
    }

    private static List<Player> choice(final Player ai, final SpellAbility sa) {
        if (!window(ai) || !known(ai)) { return List.of(); }
        final List<Player> opponents = new ArrayList<>(ai.getOpponents());
        opponents.sort(Comparator.comparingInt(Player::getId));
        if (opponents.isEmpty() || opponents.size() > 3) { return List.of(); }
        opponents.removeIf(p -> p.getCreaturesInPlay().size() < 2 || !sa.canTarget(p));
        if (opponents.stream().noneMatch(p -> p.getCreaturesInPlay().size() >= 3)) { return List.of(); }
        try {
            final GameCopier copier = new GameCopier(ai.getGame(), true);
            copier.makeCopy(null, ai);
            final Player payer = copier.find(ai);
            final Card source = copier.find(sa.getHostCard());
            final SpellAbility spell = sa.copy(source, payer, false);
            List<Player> best = List.of();
            int bestTokens = 0, bestCost = Integer.MAX_VALUE;
            for (int mask = 1; mask < (1 << opponents.size()); mask++) {
                if (Thread.currentThread().isInterrupted()) { return List.of(); }
                spell.resetTargets();
                final List<Player> selected = new ArrayList<>();
                int tokens = 0, largest = 0;
                for (int i = 0; i < opponents.size(); i++) {
                    if ((mask & (1 << i)) == 0) { continue; }
                    final Player opponent = opponents.get(i);
                    final int count = opponent.getCreaturesInPlay().size();
                    if (count < 2 || !sa.canTarget(opponent)) { selected.clear(); break; }
                    selected.add(opponent); tokens += count; largest = Math.max(largest, count);
                    spell.getTargets().add(copier.find(opponent));
                }
                if (selected.isEmpty() || largest < 3 || !spell.isTargetNumberValid()
                        || !CommandBeaconAi.canPayProjection(payer, spell)) { continue; }
                final int cost = ComputerUtilMana.calculateManaCost(spell.getPayCosts(), spell, payer, true, 0, false).getConvertedManaCost();
                if (tokens > bestTokens || tokens == bestTokens && cost < bestCost) {
                    best = selected; bestTokens = tokens; bestCost = cost;
                }
            }
            return best;
        } catch (final RuntimeException unsupportedProjection) {
            return List.of();
        }
    }

    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        sa.resetTargets();
        for (final Player target : choice(ai, sa)) { sa.getTargets().add(target); }
        return sa.getTargets().isEmpty() ? new AiAbilityDecision(0, AiPlayDecision.CantPlayAi)
                : new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    public static boolean preflight(final Player ai, final SpellAbility sa) {
        if (!uses(sa)) { return true; }
        final List<Player> best = choice(ai, sa);
        return !best.isEmpty() && sa.canCastTiming(ai) && sa.isTargetNumberValid() && sa.getTargets().size() == best.size()
                && best.stream().allMatch(p -> sa.getTargets().contains(p));
    }
}
