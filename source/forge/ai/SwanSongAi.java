package forge.ai;

import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.replacement.ReplacementType;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbilityMode;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import java.lang.ref.WeakReference;
import java.util.*;

/** Bounded permission policy; original counter, payment and opposing Bird stay native. */
public final class SwanSongAi {
    private record Choice(SpellAbility target, String reason) { }
    private record Plan(WeakReference<Player> player, WeakReference<Card> source, long sourceStamp,
                        WeakReference<SpellAbility> target, long targetStamp, String reason) { }
    private static final Map<SpellAbility, Plan> PLANS = Collections.synchronizedMap(new WeakHashMap<>());
    private SwanSongAi() { }

    public static boolean uses(final SpellAbility sa) {
        return sa != null && sa.isSpell() && sa.getApi() == ApiType.Counter && sa.usesTargeting()
                && "SwanSong".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()))
                && "Enchantment,Instant,Sorcery".equals(sa.getParam("ValidTgts"))
                && sa.getSubAbility() != null && sa.getSubAbility().getApi() == ApiType.Token
                && "TargetedController".equals(sa.getSubAbility().getParam("TokenOwner"))
                && "u_2_2_bird_flying".equals(sa.getSubAbility().getParam("TokenScript"))
                && sa.getSubAbility().getSubAbility() == null;
    }

    private static boolean known(final Player ai) {
        if (Thread.currentThread().isInterrupted()) { return false; }
        for (final Card c : ai.getGame().getCardsIn(ZoneType.STATIC_ABILITIES_SOURCE_ZONES)) {
            if (c.isPhasedOut() || c.isFaceDown()) { continue; }
            for (final var t : c.getTriggers()) {
                if (t.isSuppressed() || !t.zonesCheck(c.getZone())) { continue; }
                final TriggerType mode = t.getMode();
                if (mode == TriggerType.TokenCreated || mode == TriggerType.TokenCreatedOnce
                        || mode == TriggerType.Countered || mode == TriggerType.SpellCast
                        || mode == TriggerType.SpellCastOrCopy || mode == TriggerType.SpellAbilityCast
                        || mode == TriggerType.BecomesTarget || mode == TriggerType.BecomesTargetOnce) { return false; }
                if (mode == TriggerType.ChangesZone || mode == TriggerType.ChangesZoneAll) {
                    final String from = t.getParamOrDefault("Origin", "Any");
                    final String to = t.getParamOrDefault("Destination", "Any");
                    if (("Any".equals(to) || to.contains("Battlefield")) && !"Card.Self".equals(t.getParam("ValidCard"))
                            || ("Any".equals(from) || from.contains("Stack"))
                                && ("Any".equals(to) || to.contains("Graveyard"))) { return false; }
                }
            }
            for (final var r : c.getReplacementEffects()) {
                if (r.isSuppressed() || !r.getParamOrDefault("ActiveZones", "Battlefield").contains(c.getZone().getZoneType().name())
                        || !r.requirementsCheck(ai.getGame())) { continue; }
                if (c.isImmutable() && "Commander Effect".equals(c.getName())) { continue; }
                if (r.getMode() == ReplacementType.Counter || r.getMode() == ReplacementType.CreateToken || r.getMode() == ReplacementType.Moved
                        || r.getMode().name().contains("Damage") || r.getMode() == ReplacementType.LifeReduced) { return false; }
            }
            // Defer continuous token/creature enhancements rather than assuming an ordinary 2/2 flyer.
            for (final var st : c.getStaticAbilities()) {
                if (!st.isSuppressed() && st.checkConditions(StaticAbilityMode.Continuous)
                        && (st.hasParam("AddKeyword") || st.hasParam("AddPower") || st.hasParam("AddToughness")
                            || st.hasParam("SetPower") || st.hasParam("SetToughness") || st.hasParam("AddAbility")
                            || st.hasParam("GainAbilitiesOf"))) { return false; }
            }
        }
        // A newly created flying blocker must not invalidate our pending attack/block choices.
        final var ph = ai.getGame().getPhaseHandler();
        return !(ph.isPlayerTurn(ai) && ph.inCombat() && ph.getPhase().isBefore(PhaseType.COMBAT_DECLARE_BLOCKERS));
    }

    private static boolean lethal(final Player ai, final SpellAbility top) {
        if (top.getSubAbility() != null || !top.getTargets().contains(ai)
                || !ai.canLoseLife() || ai.cantLoseForZeroOrLessLife()) { return false; }
        final String key = top.getApi() == ApiType.DealDamage ? "NumDmg" : top.getApi() == ApiType.LoseLife ? "LifeAmount" : null;
        if (key == null) { return false; }
        final String value = top.getParamOrDefault(key, "");
        if (!value.matches("[0-9]+") && !("X".equals(value) && "Count$xPaid".equals(top.getSVar("X")))) { return false; }
        final int amount = AbilityUtils.calculateAmount(top.getHostCard(), value, top);
        return (top.getApi() == ApiType.DealDamage
                ? ComputerUtilCombat.predictDamageTo(ai, amount, top.getHostCard(), false) : amount) >= ai.getLife();
    }

    private static String reason(final Player ai, final SpellAbility top) {
        if (top.getHostCard().getCMC() >= 4) { return "mana-value"; }
        // Cheap emergency exceptions concern this spell alone, not another pending threat.
        if (ai.getGame().getStack().size() != 1 || top.getSubAbility() != null || top.hasParam("UnlessCost")) { return null; }
        if (lethal(ai, top)) { return "lethal"; }
        final ApiType api = top.getApi();
        if (api != ApiType.Destroy && api != ApiType.DealDamage && !(api == ApiType.ChangeZone
                && "Battlefield".equals(top.getParam("Origin")) && "Exile".equals(top.getParam("Destination")))) { return null; }
        for (final Card c : top.getTargets().getTargetCards()) {
            if (c.isInPlay() && c.isCreature() && c.getController() == ai && c.getOwner() == ai && !c.isPhasedOut()
                    && (c.isCommander() || !c.isToken() && c.getCMC() >= 3 && !ComputerUtilCard.isUselessCreature(ai, c))
                    && DoomedCreatureAi.isDoomed(ai, c, true)) { return "rescue"; }
        }
        return null;
    }

    private static Choice choice(final Player ai, final SpellAbility sa) {
        if (!uses(sa) || !sa.getHostCard().isInZone(ZoneType.Hand) || sa.getHostCard().getOwner() != ai
                || sa.getHostCard().getController() != ai || ai.getCardsIn(ZoneType.Hand).stream().noneMatch(c -> c == sa.getHostCard())
                || ai.getGame().getStack().isEmpty() || !known(ai)) { return null; }
        final SpellAbility top = ai.getGame().getStack().peekAbility();
        if (!top.isSpell() || top.getActivatingPlayer() == null || !top.getActivatingPlayer().isOpponentOf(ai)
                || !top.isCounterableBy(sa) || !sa.canTargetSpellAbility(top)) { return null; }
        final String reason = reason(ai, top);
        return reason == null ? null : new Choice(top, reason);
    }

    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        PLANS.remove(sa); sa.resetTargets();
        final Choice c = choice(ai, sa);
        if (c == null) { return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi); }
        sa.getTargets().add(c.target());
        PLANS.put(sa, new Plan(new WeakReference<>(ai), new WeakReference<>(sa.getHostCard()), sa.getHostCard().getGameTimestamp(),
                new WeakReference<>(c.target()), c.target().getHostCard().getGameTimestamp(), c.reason()));
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    public static boolean preflight(final Player ai, final SpellAbility sa) {
        if (!uses(sa) && !PLANS.containsKey(sa)) { return true; }
        final Plan p = PLANS.get(sa);
        final Choice c = choice(ai, sa);
        return p != null && c != null && p.player().get() == ai && p.source().get() == sa.getHostCard()
                && p.sourceStamp() == sa.getHostCard().getGameTimestamp() && p.target().get() == c.target()
                && p.targetStamp() == c.target().getHostCard().getGameTimestamp() && p.reason().equals(c.reason())
                && sa.getTargets().size() == 1 && sa.getTargets().getFirstTargetedSpell() == c.target()
                && sa.isTargetNumberValid() && sa.canCastTiming(ai);
    }
}
