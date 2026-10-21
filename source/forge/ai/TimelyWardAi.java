package forge.ai;

import forge.game.Game;
import forge.game.GameObject;
import forge.game.IEntityMap;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/** Bounded opt-in permanent protection and commander rescue; native flash permission and costs remain authoritative. */
public final class TimelyWardAi {
    private TimelyWardAi() { }

    public static boolean uses(final SpellAbility sa) {
        return sa != null && sa.isSpell() && sa.getApi() == ApiType.Attach && sa.getHostCard().isAura()
                && "TimelyWard".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()));
    }

    private static boolean known(final Player ai) {
        if (Thread.currentThread().isInterrupted() || DoomedCreatureAi.uncertain(ai, false)) { return false; }
        for (final Card c : ai.getGame().getCardsIn(ZoneType.STATIC_ABILITIES_SOURCE_ZONES)) {
            if (c.isPhasedOut()) { continue; }
            for (final var t : c.getTriggers()) {
                if (t.isSuppressed() || !t.zonesCheck(c.getZone())) { continue; }
                final TriggerType mode = t.getMode();
                if (mode == TriggerType.SpellCast || mode == TriggerType.SpellCastOrCopy
                        || mode == TriggerType.SpellAbilityCast || mode == TriggerType.BecomesTarget
                        || mode == TriggerType.BecomesTargetOnce || mode == TriggerType.Attached
                        || mode == TriggerType.DamageDone || mode == TriggerType.DamageDoneOnce
                        || mode == TriggerType.DamageAll || mode == TriggerType.DamageDealtOnce) { return false; }
                if ((mode == TriggerType.ChangesZone || mode == TriggerType.ChangesZoneAll)
                        && !"Card.Self".equals(t.getParam("ValidCard"))
                        && (t.getParamOrDefault("Destination", "Any").contains("Battlefield")
                            || "Any".equals(t.getParamOrDefault("Destination", "Any")))) { return false; }
            }
            if (c.isInPlay() && c.getStaticAbilities().stream().anyMatch(st -> !st.isSuppressed()
                    && st.getMode().contains(forge.game.staticability.StaticAbilityMode.WitherDamage))) { return false; }
            for (final var r : c.getReplacementEffects()) {
                if (!r.isSuppressed() && r.getParamOrDefault("ActiveZones", "Battlefield").contains(c.getZone().getZoneType().name())
                        && r.requirementsCheck(ai.getGame()) && (r.getMode().name().contains("Damage"))) { return false; }
            }
        }
        return true;
    }

    private static boolean declared(final Player ai) {
        final Combat combat = ai.getGame().getCombat();
        return combat != null && ai.getGame().getPhaseHandler().is(PhaseType.COMBAT_DECLARE_BLOCKERS)
                && combat.getAttackers().stream().allMatch(c -> combat.getBandOfAttacker(c) != null
                    && combat.getBandOfAttacker(c).isBlocked() != null)
                && combat.getAttackers().stream().noneMatch(Card::isWitherDamage)
                && combat.getAllBlockers().stream().noneMatch(Card::isWitherDamage);
    }

    private static boolean rescue(final Player ai, final SpellAbility sa, final Card card) {
        if (!card.isCommander()) { return false; }
        final boolean stack = !ai.getGame().getStack().isEmpty();
        if (stack) {
            for (final var entry : ai.getGame().getStack()) {
                final SpellAbility threat = entry.getSpellAbility();
                if (threat.getSubAbility() != null || (threat.getApi() != ApiType.Destroy && threat.getApi() != ApiType.DestroyAll
                        && threat.getApi() != ApiType.DealDamage && threat.getApi() != ApiType.DamageAll)
                        || threat.getHostCard().isWitherDamage()) { return false; }
            }
        } else if (!declared(ai)) { return false; }
        if (!DoomedCreatureAi.isDoomed(ai, card, stack)) { return false; }
        return DarksteelMutationAi.preview(card, sa.getHostCard(), copy -> {
            if (!copy.hasKeyword(Keyword.INDESTRUCTIBLE) || copy.getNetToughness() <= 0) { return false; }
            if (stack) { return true; } // only ordinary destruction/damage is admitted above
            final Combat combat = new Combat(ai.getGame().getCombat(), new IEntityMap() {
                public Game getGame() { return ai.getGame(); }
                public GameObject map(final GameObject object) { return object == card ? copy : object; }
            });
            return !ComputerUtilCombat.combatantWouldBeDestroyed(ai, copy, combat);
        });
    }

    private static boolean eligible(final Player ai, final SpellAbility sa, final Card card) {
        return card != null && card.getGame() == ai.getGame() && card.isInPlay() && card.isCreature()
                && card.getController() == ai && !card.isPhasedOut() && card.getNetToughness() > 0
                && !card.hasKeyword(Keyword.INDESTRUCTIBLE) && !card.isEnchantedBy(sa.getHostCard().getName())
                && !ComputerUtilCard.isUselessCreature(ai, card) && sa.canTarget(card);
    }

    private static boolean proactive(final Player ai) {
        return ai.getGame().getStack().isEmpty() && ai.getGame().getPhaseHandler().is(PhaseType.MAIN2, ai);
    }

    private static Card choice(final Player ai, final SpellAbility sa) {
        if (!known(ai)) { return null; }
        Card best = null;
        for (final Card card : ai.getCreaturesInPlay()) {
            if (!eligible(ai, sa, card) || !(proactive(ai) ? DarksteelMutationAi.preview(card, sa.getHostCard(), copy -> copy.hasKeyword(Keyword.INDESTRUCTIBLE) && copy.getNetToughness() > 0) : rescue(ai, sa, card))) { continue; }
            if (best == null || card.isCommander() && !best.isCommander()
                    || card.isCommander() == best.isCommander() && (ComputerUtilCard.evaluateCreature(card) > ComputerUtilCard.evaluateCreature(best)
                    || ComputerUtilCard.evaluateCreature(card) == ComputerUtilCard.evaluateCreature(best) && card.getId() < best.getId())) { best = card; }
        }
        return best;
    }

    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        sa.resetTargets();
        final Card best = choice(ai, sa);
        if (best != null) {
            sa.getTargets().add(best);
            if (sa.canCastTiming(ai)) { return new AiAbilityDecision(100, AiPlayDecision.WillPlay); }
        }
        sa.resetTargets();
        return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }

    public static boolean preflight(final Player ai, final SpellAbility sa) {
        return !uses(sa) || sa.isTargetNumberValid() && sa.getTargets().size() == 1 && sa.canCastTiming(ai)
                && sa.getTargetCard() == choice(ai, sa);
    }
}
