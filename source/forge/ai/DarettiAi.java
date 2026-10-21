package forge.ai;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/** Narrow opt-in admission checks for Daretti, Scrap Savant. */
public final class DarettiAi {
    private DarettiAi() { }

    public static boolean uses(final SpellAbility sa) {
        return sa != null && sa.getHostCard() != null
                && "Daretti, Scrap Savant".equals(sa.getHostCard().getName())
                && "DarettiScrapSavant".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()));
    }

    /** Null means the shared native DiscardAi should make its usual decision. */
    public static AiAbilityDecision checkPlusTwo(final Player ai, final SpellAbility sa) {
        if (!uses(sa) || sa.getApi() != ApiType.Discard) { return null; }
        if (ai != sa.getActivatingPlayer() || !ai.getGame().getPhaseHandler().is(PhaseType.MAIN2, ai)
                || !hasExcessLandToDiscard(ai) || !sa.canPlay()
                || !ComputerUtilCost.canPayCost(sa, ai, false)) {
            return decline();
        }
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    /** Null delegates to native sacrifice, target ranking, costs and resolution. */
    public static AiAbilityDecision checkMinusTwo(final Player ai, final SpellAbility sa) {
        if (!uses(sa) || sa.getApi() != ApiType.Sacrifice) { return null; }
        if (ai != sa.getActivatingPlayer() || !hasLegalReturnTarget(ai, sa)
                || !hasSacrificeMaterial(ai, sa)) {
            return decline();
        }
        return null;
    }

    private static boolean hasExcessLandToDiscard(final Player ai) {
        final int landsInPlay = CardLists.count(ai.getCardsIn(ZoneType.Battlefield), CardPredicates.LANDS_PRODUCING_MANA);
        final int keep = landsInPlay >= 5 ? 1 : 2;
        final CardCollection eligible = CardLists.filter(ai.getCardsIn(ZoneType.Hand), CardPredicates.LANDS);
        eligible.removeIf(card -> card.hasSVar("DoNotDiscardIfAble"));
        return eligible.size() > keep;
    }

    private static boolean hasLegalReturnTarget(final Player ai, final SpellAbility sa) {
        final SpellAbility changeZone = sa.getSubAbility();
        if (changeZone == null || changeZone.getApi() != ApiType.ChangeZone || !changeZone.usesTargeting()) { return false; }
        final String valid = changeZone.getParamOrDefault("ValidTgts", "Artifact.YouCtrl");
        final CardCollection candidates = CardLists.getValidCards(ai.getCardsIn(ZoneType.Graveyard), valid.split(";"),
                ai, sa.getHostCard(), changeZone);
        return !CardLists.filter(candidates, CardPredicates.isTargetableBy(changeZone)).isEmpty();
    }

    private static boolean hasSacrificeMaterial(final Player ai, final SpellAbility sa) {
        final String valid = sa.getParamOrDefault("SacValid", "Self");
        final CardCollection candidates = CardLists.getValidCards(ai.getCardsIn(ZoneType.Battlefield), valid.split(";"),
                ai, sa.getHostCard(), sa);
        return !CardLists.filter(candidates, CardPredicates.canBeSacrificedBy(sa, true)).isEmpty();
    }

    private static AiAbilityDecision decline() {
        return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }
}
