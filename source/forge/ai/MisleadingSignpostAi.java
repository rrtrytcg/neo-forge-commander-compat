package forge.ai;

import forge.game.GameEntity;
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

/** Opt-in flash rescue with original entry trigger, defender options and mana ability. */
public final class MisleadingSignpostAi {
    private record Choice(Card attacker, Player defender) { }
    private record Plan(WeakReference<Player> player, WeakReference<Card> source, long sourceStamp,
                        WeakReference<Card> attacker, long attackerStamp, WeakReference<Player> defender, boolean fallback) { }
    private static final Map<SpellAbility, Plan> PLANS = Collections.synchronizedMap(new WeakHashMap<>());
    private MisleadingSignpostAi() { }

    private static boolean tagged(final Card c) {
        return "MisleadingSignpost".equals(AiOverrideProfile.get().getAiLogic(c)) && c.isArtifact()
                && c.hasKeyword("Flash") && c.getTriggers().stream().anyMatch(t -> t.getMode() == TriggerType.ChangesZone
                    && "Card.Self".equals(t.getParam("ValidCard")) && "Battlefield".equals(t.getParam("Destination"))
                    && "Declare Attackers".equals(t.getParam("Phase")) && "You".equals(t.getParam("OptionalDecider")));
    }

    public static boolean usesSpell(final SpellAbility sa) {
        return sa != null && sa.isSpell() && sa.getApi() == ApiType.PermanentNoncreature && tagged(sa.getHostCard());
    }

    public static boolean usesRedirect(final SpellAbility sa) {
        return sa != null && sa.getApi() == ApiType.ChangeCombatants && "True".equals(sa.getParam("Attacking"))
                && "Creature.attacking".equals(sa.getParam("ValidTgts")) && tagged(sa.getHostCard());
    }

    private static boolean known(final Player ai, final Card entering) {
        if (Thread.currentThread().isInterrupted()) { return false; }
        for (final Card c : ai.getGame().getCardsIn(ZoneType.STATIC_ABILITIES_SOURCE_ZONES)) {
            if (c.isPhasedOut()) { continue; }
            if (c.isFaceDown()) { return false; }
            for (final var t : c.getTriggers()) {
                if (t.isSuppressed() || !t.zonesCheck(c.getZone())) { continue; }
                final TriggerType mode = t.getMode();
                if (mode == TriggerType.SpellCast || mode == TriggerType.SpellCastOrCopy || mode == TriggerType.SpellAbilityCast
                        || mode == TriggerType.BecomesTarget || mode == TriggerType.BecomesTargetOnce
                        || mode.name().contains("Attack") || mode.name().contains("Block") || mode.name().contains("Damage")) { return false; }
                if ((mode == TriggerType.ChangesZone || mode == TriggerType.ChangesZoneAll)
                        && !"Card.Self".equals(t.getParam("ValidCard"))
                        && ("Any".equals(t.getParamOrDefault("Destination", "Any"))
                            || t.getParamOrDefault("Destination", "Any").contains("Battlefield"))) { return false; }
            }
            for (final var r : c.getReplacementEffects()) {
                if (r.isSuppressed() || !r.getParamOrDefault("ActiveZones", "Battlefield").contains(c.getZone().getZoneType().name())
                        || !r.requirementsCheck(ai.getGame())) { continue; }
                if (c.isImmutable() && "Commander Effect".equals(c.getName())) { continue; }
                if (r.getMode() == ReplacementType.Moved && c.getId() != entering.getId()
                        && "Card.Self".equals(r.getParam("ValidCard")) && "Battlefield".equals(r.getParam("Destination"))) { continue; }
                if (r.getMode() == ReplacementType.Moved || r.getMode().name().contains("Damage")) { return false; }
            }
            for (final var st : c.getStaticAbilities()) {
                if (st.isSuppressed()) { continue; }
                for (final StaticAbilityMode mode : List.of(StaticAbilityMode.DisableTriggers, StaticAbilityMode.Panharmonicon,
                        StaticAbilityMode.AssignCombatDamageAsUnblocked, StaticAbilityMode.CombatDamageToughness,
                        StaticAbilityMode.CombatDamageNegatePower, StaticAbilityMode.AssignNoCombatDamage)) {
                    if (st.checkConditions(mode)) { return false; }
                }
            }
        }
        return true;
    }

    private static boolean combatWindow(final Player ai) {
        return ai.getGame().getCombat() != null && ai.getGame().getPhaseHandler().is(PhaseType.COMBAT_DECLARE_ATTACKERS)
                && ai.getGame().getCombat().getAttackingPlayer().isOpponentOf(ai);
    }

    private static boolean eligible(final Player ai, final SpellAbility redirect, final Card c) {
        final var combat = ai.getGame().getCombat();
        if (!combatWindow(ai) || !c.isInPlay() || !c.isCreature() || c.isPhasedOut() || c.getNetPower() < 3
                || c.getController() != combat.getAttackingPlayer() || !combat.isAttacking(c) || !redirect.canTarget(c)
                || ai.getGame().getCardsIn(ZoneType.Battlefield).stream().noneMatch(actual -> actual == c)
                || combat.getBandOfAttacker(c) == null || combat.getBandOfAttacker(c).getAttackers().size() != 1) { return false; }
        final GameEntity old = combat.getDefenderByAttacker(c);
        return old == ai || old instanceof Card pw && pw.isPlaneswalker() && pw.getController() == ai && pw.getOwner() == ai;
    }

    private static Player destination(final Player ai, final Card attacker, final Iterable<? extends GameEntity> options) {
        Player best = null;
        for (final GameEntity e : options) {
            if (!(e instanceof Player p) || !p.isOpponentOf(ai) || p == attacker.getController()
                    || !ai.getGame().getPlayers().contains(p)) { continue; }
            if (best == null || p.getLife() < best.getLife() || p.getLife() == best.getLife() && p.getId() < best.getId()) { best = p; }
        }
        return best;
    }

    private static Choice choice(final Player ai, final SpellAbility redirect) {
        if (!combatWindow(ai) || !known(ai, redirect.getHostCard())) { return null; }
        Choice best = null;
        for (final Card c : ai.getGame().getCombat().getAttackers()) {
            if (!eligible(ai, redirect, c)) { continue; }
            final Player defender = destination(ai, c, ai.getGame().getCombat().getDefenders());
            if (defender != null && (best == null || c.getNetPower() > best.attacker().getNetPower()
                    || c.getNetPower() == best.attacker().getNetPower() && c.getId() < best.attacker().getId())) { best = new Choice(c, defender); }
        }
        return best;
    }

    private static SpellAbility redirect(final SpellAbility spell, final Player ai) {
        for (final var t : spell.getHostCard().getTriggers()) {
            final SpellAbility ab = t.ensureAbility();
            if (ab != null && usesRedirect(ab)) { return ab.copy(ai); }
        }
        return null;
    }

    private static boolean fallback(final Player ai) {
        final var ph = ai.getGame().getPhaseHandler();
        return ph.is(PhaseType.END_OF_TURN) && !ph.isPlayerTurn(ai) && ph.getNextTurn() == ai;
    }

    private static boolean source(final Player ai, final SpellAbility sa) {
        return usesSpell(sa) && sa.getHostCard().getController() == ai && sa.getHostCard().getOwner() == ai
                && ai.getCardsIn(ZoneType.Hand).stream().anyMatch(c -> c == sa.getHostCard())
                && ai.getGame().getStack().isEmpty() && known(ai, sa.getHostCard());
    }

    public static AiAbilityDecision considerSpell(final Player ai, final SpellAbility sa) {
        PLANS.remove(sa);
        if (!source(ai, sa)) { return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi); }
        final SpellAbility redirect = redirect(sa, ai);
        final Choice c = redirect == null ? null : choice(ai, redirect);
        final boolean fallback = fallback(ai);
        if (c == null && !fallback) { return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi); }
        PLANS.put(sa, new Plan(new WeakReference<>(ai), new WeakReference<>(sa.getHostCard()), sa.getHostCard().getGameTimestamp(),
                new WeakReference<>(c == null ? null : c.attacker()), c == null ? 0 : c.attacker().getGameTimestamp(),
                new WeakReference<>(c == null ? null : c.defender()), fallback));
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    public static boolean preflight(final Player ai, final SpellAbility sa) {
        if (!usesSpell(sa) && !PLANS.containsKey(sa)) { return true; }
        final Plan p = PLANS.get(sa);
        if (p == null || p.player().get() != ai || p.source().get() != sa.getHostCard()
                || p.sourceStamp() != sa.getHostCard().getGameTimestamp() || !source(ai, sa) || !sa.canCastTiming(ai)) { return false; }
        if (p.fallback()) { return fallback(ai); }
        final SpellAbility ab = redirect(sa, ai);
        final Choice c = ab == null ? null : choice(ai, ab);
        return c != null && c.attacker() == p.attacker().get() && c.attacker().getGameTimestamp() == p.attackerStamp()
                && c.defender() == p.defender().get();
    }

    public static AiAbilityDecision considerRedirect(final Player ai, final SpellAbility sa) {
        sa.resetTargets();
        final Choice c = choice(ai, sa);
        if (c == null) { return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi); }
        sa.getTargets().add(c.attacker());
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    public static Player chooseDefender(final Player ai, final SpellAbility sa, final Iterable<? extends GameEntity> options, final Map<String,Object> params) {
        final Object object = params == null ? null : params.get("Attacker");
        if (!(object instanceof Card c) || sa.getTargetCard() != c || !known(ai, sa.getHostCard()) || !eligible(ai, sa, c)) { return null; }
        final List<GameEntity> legal = new ArrayList<>();
        for (final GameEntity option : options) { if (ai.getGame().getCombat().getDefenders().contains(option)) { legal.add(option); } }
        // Choosing a defender is not targeting that player; native options remain authoritative.
        return destination(ai, c, legal);
    }
}
