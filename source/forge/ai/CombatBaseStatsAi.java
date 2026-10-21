package forge.ai;

import forge.ai.ability.AnimateAi;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityStackInstance;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityMode;
import forge.game.zone.ZoneType;

import java.util.Set;
import java.util.function.Predicate;

/** Shared bounded combat-only base-P/T evaluation; all execution remains native. */
public final class CombatBaseStatsAi {
    private CombatBaseStatsAi() { }

    private static AiAbilityDecision hold(final SpellAbility sa) {
        sa.setXManaCostPaid(0);
        return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }

    // The native preview helper recomputes additive P/T statics. Do not promise safety through
    // conditional, variable, type-changing, keyword-changing or P/T-setting continuous effects.
    private static boolean previewKnown(final Player ai) {
        return previewKnown(ai, false);
    }

    public static boolean previewKnown(final Player ai, final boolean allowCommanderPermission) {
        for (final Card source : ai.getGame().getCardsIn(ZoneType.STATIC_ABILITIES_SOURCE_ZONES)) {
            for (final StaticAbility st : source.getStaticAbilities()) {
                if (!st.zonesCheck() || !st.checkMode(StaticAbilityMode.Continuous)) {
                    continue;
                }
                // Only the exact native command-zone casting permission is harmless to P/T.
                // Existing callers retain their original conservative behavior.
                if (allowCommanderPermission && source.isImmutable() && "Commander Effect".equals(source.getName())
                        && "True".equals(st.getParam("MayPlay"))
                        && "Card.IsCommander+YouOwn".equals(st.getParam("Affected"))
                        && "Command".equals(st.getParam("AffectedZone")) && "Command".equals(st.getParam("EffectZone"))
                        && Set.of("Mode", "MayPlay", "Affected", "AffectedZone", "EffectZone").containsAll(st.getMapParams().keySet())) {
                    continue;
                }
                // Intrinsic changeling is already true before animation and cannot alter P/T.
                if ("True".equals(st.getParam("AddAllCreatureTypes")) && "True".equals(st.getParam("CharacteristicDefining"))
                        && Set.of("Mode", "Secondary", "Description", "EffectZone", "AddAllCreatureTypes", "CharacteristicDefining")
                                .containsAll(st.getMapParams().keySet())) {
                    continue;
                }
                if (!Set.of("Mode", "Affected", "AffectedDefined", "AffectedZone", "AddPower", "AddToughness", "Description")
                        .containsAll(st.getMapParams().keySet())
                        || !st.getParamOrDefault("AddPower", "0").matches("-?\\d+")
                        || !st.getParamOrDefault("AddToughness", "0").matches("-?\\d+")) {
                    return false;
                }
            }
        }
        return true;
    }

    public static Card preview(final Player ai, final Card card, final SpellAbility sa) {
        final Card copy = AnimateAi.becomeAnimated(card, sa);
        copy.setDamage(card.getDamage());
        return copy;
    }

    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa, final Predicate<SpellAbility> matches) {
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        final boolean offense = ph.is(PhaseType.COMBAT_BEGIN, ai);
        final Combat combat = ai.getGame().getCombat();
        final boolean defense = !ph.isPlayerTurn(ai) && ph.is(PhaseType.COMBAT_DECLARE_BLOCKERS) && combat != null;
        if ((!offense && !defense) || !previewKnown(ai)) {
            return hold(sa);
        }
        for (final SpellAbilityStackInstance item : ai.getGame().getStack()) {
            final SpellAbility pending = item.getSpellAbility();
            if (matches.test(pending) && ai.equals(pending.getActivatingPlayer())) {
                return hold(sa);
            }
        }
        sa.setXManaCostPaid(0);
        if (ComputerUtilCost.setMaxXValue(sa, ai, false) < 3) {
            return hold(sa);
        }
        int improved = 0;
        for (final Card card : ai.getCreaturesInPlay()) {
            if (card.isPhasedOut()) {
                continue;
            }
            final Card after = preview(ai, card, sa);
            if (after.getNetPower() < card.getNetPower() || after.getNetToughness() < card.getNetToughness()
                    || after.getNetToughness() <= 0
                    || after.getDamage() > 0 && after.getDamage() >= after.getLethal() && !after.hasKeyword(Keyword.INDESTRUCTIBLE)) {
                return hold(sa);
            }
            final boolean participant = offense ? CombatUtil.canAttack(after) : combat.isBlocking(card);
            if (participant && after.getNetPower() > card.getNetPower()) {
                improved++;
            }
        }
        if (improved < 2 || !ComputerUtilCost.canPayCost(sa, ai, false)) {
            return hold(sa);
        }
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }
}
