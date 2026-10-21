package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.*;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import java.util.List;

/** Narrow, deterministic counter transfers. Costs and counter resolution remain native. */
public final class NestingGroundsAi {
    private NestingGroundsAi() { }
    private static final CounterType PLUS = CounterEnumType.P1P1;
    private static final CounterType MINUS = CounterEnumType.M1M1;

    public static boolean uses(final SpellAbility sa) {
        if (sa == null) { return false; }
        final SpellAbility root = sa.getRootAbility();
        final SpellAbility move = root.getSubAbility();
        return "NestingGrounds".equals(AiOverrideProfile.get().getAiLogic(root.getHostCard()))
                && root.getApi() == ApiType.Pump && root.hasParam("SorcerySpeed")
                && "Permanent.YouCtrl".equals(root.getParam("ValidTgts"))
                && move != null && move.getApi() == ApiType.MoveCounter
                && "ParentTarget".equals(move.getParam("Source"))
                && "Any".equals(move.getParam("CounterType")) && "1".equals(move.getParam("CounterNum"))
                && move.hasParam("TargetUnique") && move.getSubAbility() == null;
    }

    private static Card preview(final Card c, final CounterType type, final int delta) {
        final Card copy = CardCopyService.getLKICopy(c);
        copy.setCounters(type, c.getCounters(type) + delta);
        return copy;
    }

    private static boolean survives(final Card c, final Card original) {
        // LKI copies omit marked damage and the native lethal-by-power flag.
        final int lethal = original.isLethalDamageByPower() ? c.getNetPower() : c.getNetToughness();
        return c.getNetToughness() > 0
                && (original.hasKeyword(Keyword.INDESTRUCTIBLE) || lethal > original.getDamage());
    }

    private static boolean deathUncertain(final Player ai) {
        for (final Card c : ai.getGame().getCardsIn(List.of(ZoneType.Battlefield, ZoneType.Graveyard, ZoneType.Command))) {
            for (final Trigger t : c.getTriggers()) {
                if (t.isSuppressed() || !t.zonesCheck(c.getZone())
                        || (t.getMode() != TriggerType.ChangesZone && t.getMode() != TriggerType.ChangesZoneAll)) { continue; }
                final String from = t.getParamOrDefault("Origin", "Any");
                final String to = t.getParamOrDefault("Destination", "Any");
                if ((from.contains("Battlefield") || from.equals("Any"))
                        && (to.contains("Graveyard") || to.contains("Exile") || to.equals("Any"))) { return true; }
            }
        }
        return false;
    }

    private static int score(final Player ai, final SpellAbility root, final Card from, final Card to, final CounterType type) {
        final SpellAbility move = root.getSubAbility();
        if (from == null || to == null || from == to || !from.isCreature() || !to.isCreature()
                || from.isPhasedOut() || to.isPhasedOut() || !from.getController().equals(ai)
                || !root.canTarget(from) || !move.canTarget(to)
                || from.getCounters(type) < 1 || !from.canRemoveCounters(type) || !to.canReceiveCounters(type)) { return 0; }
        final Card donor = preview(from, type, -1);
        final Card recipient = preview(to, type, 1);
        if (!survives(donor, from)) { return 0; }
        if (type.equals(MINUS)) {
            if (!to.getController().isOpponentOf(ai)) { return 0; }
            final boolean kills = !survives(recipient, to);
            if (kills && recipient.getNetToughness() > 0 && CreatureShrinkAi.casualty(to, root, 1) != 1) {
                return 0; // Do not promise a marked-damage kill through shields or unknown destruction replacements.
            }
            if (kills && (deathUncertain(ai) || !to.getStaticAbilities().isEmpty()
                    || !to.getReplacementEffects().isEmpty())) { return 0; }
            final int gain = ComputerUtilCard.evaluateCreature(donor) - ComputerUtilCard.evaluateCreature(from)
                    + ComputerUtilCard.evaluateCreature(to) - ComputerUtilCard.evaluateCreature(recipient);
            return gain > 0 ? (kills ? 100000 : 10000) + gain : 0;
        }
        if (!to.getController().equals(ai) || (to.getCounters(PLUS) == 0 && to.hasKeyword(Keyword.UNDYING))) { return 0; }
        final int gain = ComputerUtilCard.evaluateCreature(donor) + ComputerUtilCard.evaluateCreature(recipient)
                - ComputerUtilCard.evaluateCreature(from) - ComputerUtilCard.evaluateCreature(to);
        final boolean spreads = from.getCounters(PLUS) >= 2 && to.getCounters(PLUS) == 0;
        return gain > 0 || spreads ? 1 + Math.max(0, gain) : 0;
    }

    public static AiAbilityDecision consider(final Player ai, final SpellAbility root) {
        final SpellAbility move = root.getSubAbility();
        root.resetTargets(); move.resetTargets();
        Card donor = null, recipient = null;
        int best = 0;
        for (final Card from : ai.getCreaturesInPlay()) {
            for (final Card to : ai.getGame().getCardsIn(ZoneType.Battlefield)) {
                for (final CounterType type : List.of(MINUS, PLUS)) {
                    final int value = score(ai, root, from, to, type);
                    if (value > best) { best = value; donor = from; recipient = to; }
                }
            }
        }
        if (donor != null) { root.getTargets().add(donor); move.getTargets().add(recipient); }
        return decision(best > 0);
    }

    public static AiAbilityDecision validate(final Player ai, final SpellAbility move) {
        final SpellAbility root = move.getRootAbility();
        return decision(type(ai, root, root.getTargetCard(), move.getTargetCard(), List.of(MINUS, PLUS)) != null);
    }

    public static CounterType type(final Player ai, final SpellAbility root, final Card from, final Card to, final List<CounterType> options) {
        CounterType bestType = null;
        int best = 0;
        for (final CounterType type : List.of(MINUS, PLUS)) {
            if (!options.contains(type)) { continue; }
            final int value = score(ai, root, from, to, type);
            if (value > best) { best = value; bestType = type; }
        }
        return bestType;
    }

    private static AiAbilityDecision decision(final boolean play) {
        return new AiAbilityDecision(play ? 100 : 0, play ? AiPlayDecision.WillPlay : AiPlayDecision.CantPlayAi);
    }
}
