package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.combat.CombatUtil;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.lang.ref.WeakReference;
import java.util.*;

/** Opt-in creature combat copies. All real copying, costs, attacks and blocks remain native. */
public final class MirageMirrorAi {
    private record Choice(Card target, Card attacker) { }
    private record Plan(WeakReference<Card> source, long sourceStamp, WeakReference<Card> target,
                        long targetStamp, WeakReference<Card> attacker, long attackerStamp) { }
    private record Use(WeakReference<Card> source, long stamp, int turn) { }
    private static final Map<SpellAbility, Plan> PLANS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<forge.game.Game, List<Use>> USED = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<Keyword> KEYWORDS = Set.of(Keyword.FLYING, Keyword.REACH, Keyword.TRAMPLE,
            Keyword.VIGILANCE, Keyword.HASTE, Keyword.DEFENDER, Keyword.FIRST_STRIKE, Keyword.DOUBLE_STRIKE,
            Keyword.DEATHTOUCH, Keyword.LIFELINK, Keyword.INDESTRUCTIBLE, Keyword.HEXPROOF, Keyword.SHROUD);
    private MirageMirrorAi() { }

    private static boolean tagged(final Card c) {
        return "MirageMirror".equals(AiOverrideProfile.get().getAiLogic(c)) && c.isArtifact() && !c.isCloned();
    }
    public static boolean usesSpell(final SpellAbility sa) {
        return sa != null && sa.isSpell() && sa.getApi() == ApiType.PermanentNoncreature && tagged(sa.getHostCard());
    }
    public static boolean usesActivation(final SpellAbility sa) {
        return sa != null && sa.isActivatedAbility() && sa.getApi() == ApiType.Clone && tagged(sa.getHostCard())
                && "UntilEndOfTurn".equals(sa.getParam("Duration")) && sa.getSubAbility() == null
                && "Artifact,Creature,Enchantment,Land".equals(sa.getParam("ValidTgts"))
                && sa.getPayCosts().isOnlyManaCost() && !sa.getPayCosts().hasXInAnyCostPart();
    }
    private static boolean supported(final Card c) {
        return c.isCreature() && !c.getType().isLegendary() && !c.isFaceDown() && !c.isCloned() && !c.isPhasedOut()
                && c.getTriggers().isEmpty() && c.getReplacementEffects().isEmpty() && c.getStaticAbilities().stream().allMatch(st -> st.getKeyword() != null && KEYWORDS.contains(st.getKeyword().getKeyword()))
                && c.getSpellAbilities().stream().noneMatch(SpellAbility::isAbility)
                && c.getKeywords().stream().allMatch(k -> KEYWORDS.contains(k.getKeyword()));
    }
    private static boolean commanderPermission(final Card source, final forge.game.staticability.StaticAbility st) {
        // Native command-zone permission affects commanders only; it cannot change this noncommander copy.
        return source.isImmutable() && "Commander Effect".equals(source.getName())
                && "Continuous".equals(st.getParam("Mode")) && "True".equals(st.getParam("MayPlay"))
                && "Card.IsCommander+YouOwn".equals(st.getParam("Affected"))
                && "Command".equals(st.getParam("AffectedZone")) && "Command".equals(st.getParam("EffectZone"))
                && Set.of("Mode", "MayPlay", "Affected", "AffectedZone", "EffectZone").containsAll(st.getMapParams().keySet());
    }
    private static boolean known(final Player ai) {
        for (final Card c : ai.getGame().getCardsIn(ZoneType.STATIC_ABILITIES_SOURCE_ZONES)) {
            if (c.isPhasedOut()) { continue; }
            if (c.isFaceDown()) { return false; }
            for (final var t : c.getTriggers()) {
                if (t.isSuppressed() || !t.zonesCheck(c.getZone())) { continue; }
                final String mode = t.getMode().name();
                if (mode.contains("Attack") || mode.contains("Block") || mode.contains("Damage") || mode.contains("Target")
                        || mode.contains("Cast") || mode.equals("AbilityResolves") || mode.equals("Taps") || mode.equals("TapAll")) { return false; }
                if (mode.startsWith("ChangesZone") && !"Card.Self".equals(t.getParam("ValidCard"))) { return false; }
            }
            for (final var r : c.getReplacementEffects()) {
                if (r.isSuppressed() || !r.zonesCheck(c.getZone()) || !r.requirementsCheck(ai.getGame())) { continue; }
                if (c.isImmutable() && "Commander Effect".equals(c.getName())) { continue; }
                // Unrelated loyalty/entry replacements do not affect a copy that never enters.
                if (r.getMode().name().equals("Moved") && "Card.Self".equals(r.getParam("ValidCard"))
                        && "Battlefield".equals(r.getParam("Destination"))) { continue; }
                if (r.getMode().name().contains("Damage") || r.getMode().name().equals("Moved") || r.getMode().name().equals("Tap")) { return false; }
            }
            if (c.getStaticAbilities().stream().anyMatch(st -> !commanderPermission(c, st) && !st.isSuppressed() && st.zonesCheck()
                    && (st.getMode().stream().anyMatch(m -> Set.of("Continuous", "AssignCombatDamageAsUnblocked", "CombatDamageToughness",
                        "CombatDamageNegatePower", "AssignNoCombatDamage", "Panharmonicon", "DisableTriggers").contains(m.name()))))) { return false; }
            if (c.getSpellAbilities().stream().anyMatch(s -> s.getApi() == ApiType.Regenerate)) { return false; }
        }
        return true;
    }
    private static boolean window(final Player ai, final SpellAbility sa) {
        if (Thread.currentThread().isInterrupted() || ai.getGame().EXPERIMENTAL_RESTORE_SNAPSHOT
                || !ai.getGame().getStack().isEmpty() || sa.getHostCard().getController() != ai || !known(ai)) { return false; }
        final var ph = ai.getGame().getPhaseHandler();
        final Card source = sa.getHostCard();
        if (usesSpell(sa)) {
            return ph.is(PhaseType.MAIN2, ai) && ai.getCardsIn(ZoneType.Hand).stream().anyMatch(c -> c == source)
                    && sa.canPlay() && sa.checkRestrictions(ai) && sa.canCastTiming(ai);
        }
        final List<Use> history = USED.get(ai.getGame());
        return usesActivation(sa) && source.isInPlay() && !source.isTapped() && !source.isPhasedOut()
                && ai.getGame().getCardsIn(ZoneType.Battlefield).stream().anyMatch(c -> c == source)
                && (history == null || history.stream().noneMatch(u -> u.source().get() == source
                    && u.stamp() == source.getGameTimestamp() && u.turn() == ph.getTurn()))
                && sa.canPlay() && sa.checkRestrictions(ai)
                && (ph.is(PhaseType.COMBAT_BEGIN, ai) || !ph.isPlayerTurn(ai)
                    && ph.is(PhaseType.COMBAT_DECLARE_ATTACKERS) && ai.getGame().getCombat() != null);
    }
    private static boolean aimedAtUs(final Player ai, final Card attacker) {
        final var combat = ai.getGame().getCombat();
        if (combat == null || !combat.isAttacking(attacker) || !supported(attacker)
                || combat.getBandOfAttacker(attacker) == null || combat.getBandOfAttacker(attacker).getAttackers().size() != 1) { return false; }
        final var defender = combat.getDefenderByAttacker(attacker);
        return defender == ai || defender instanceof Card c && c.isPlaneswalker() && c.getController() == ai && c.getOwner() == ai;
    }
    private static int tradeValue(final Card card) {
        // Native valuation gives an untapped bonus; compare the two bodies on equal footing.
        // This helper is used only on private projected cards.
        final boolean tapped = card.isTapped();
        card.setTapped(false);
        try { return ComputerUtilCard.evaluateCreature(card); }
        finally { card.setTapped(tapped); }
    }
    private static Choice choice(final Player ai, final SpellAbility sa) {
        if (!window(ai, sa)) { return null; }
        final boolean casting = usesSpell(sa), offense = ai.getGame().getPhaseHandler().isPlayerTurn(ai);
        final List<Card> targets = new ArrayList<>();
        for (final Card c : ai.getGame().getCardsIn(ZoneType.Battlefield)) { if (supported(c) && c != sa.getHostCard()) { targets.add(c); } }
        targets.sort(Comparator.comparingInt((Card c) -> ComputerUtilCard.evaluateCreature(c)).reversed().thenComparingInt(Card::getId));
        for (final Card target : targets) {
            try {
                final GameCopier copier = new GameCopier(ai.getGame(), true);
                final var projected = copier.makeCopy(null, ai);
                final Player payer = copier.find(ai);
                final Card source = copier.find(sa.getHostCard()), copiedTarget = copier.find(target);
                final SpellAbility cost = sa.copy(source, payer, false);
                if (!CommandBeaconAi.canPayProjection(payer, cost)) { continue; }
                if (casting) { source.getZone().remove(source); payer.getZone(ZoneType.Battlefield).add(source); }
                final SpellAbility activation = source.getSpellAbilities().stream().filter(s -> s.getApi() == ApiType.Clone).findFirst().orElse(null);
                if (activation == null) { continue; }
                activation.setActivatingPlayer(payer); activation.resetTargets();
                if (!activation.canTarget(copiedTarget)) { continue; }
                activation.getTargets().add(copiedTarget);
                AbilityUtils.resolve(activation);
                projected.getAction().checkStaticAbilities();
                if (!source.isCreature() || source.getNetToughness() <= 0 || source.getDamage() >= source.getLethal()
                        && source.getDamage() > 0 && !source.hasKeyword(Keyword.INDESTRUCTIBLE)) { continue; }
                if (casting || offense) {
                    if (source.getNetPower() >= 4 && (casting || CombatUtil.canAttack(source))) { return new Choice(target, null); }
                } else {
                    final var combat = projected.getCombat();
                    if (combat == null) { continue; }
                    for (final Card incoming : ai.getGame().getCombat().getAttackers()) {
                        if (!aimedAtUs(ai, incoming) || !ai.getGame().getCombat().getBlockers(incoming).isEmpty()) { continue; }
                        final Card attacker = copier.find(incoming);
                        if (!CombatUtil.canBlock(attacker, source, combat)) { continue; }
                        combat.addBlocker(attacker, source);
                        final boolean survives = !ComputerUtilCombat.blockerWouldBeDestroyed(payer, source, combat);
                        final boolean trade = ComputerUtilCombat.attackerWouldBeDestroyed(payer, attacker, combat)
                                && tradeValue(attacker) >= tradeValue(source);
                        combat.removeFromCombat(source);
                        if (survives || trade) { return new Choice(target, incoming); }
                    }
                }
            } catch (final RuntimeException unsupportedProjection) { /* uncertainty holds */ }
        }
        return null;
    }
    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        PLANS.remove(sa);
        if (usesActivation(sa)) { sa.resetTargets(); }
        final Choice c = choice(ai, sa);
        if (c == null || Thread.currentThread().isInterrupted()) { return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi); }
        if (usesActivation(sa)) { sa.getTargets().add(c.target()); }
        PLANS.put(sa, new Plan(new WeakReference<>(sa.getHostCard()), sa.getHostCard().getGameTimestamp(),
                new WeakReference<>(c.target()), c.target().getGameTimestamp(), new WeakReference<>(c.attacker()),
                c.attacker() == null ? 0 : c.attacker().getGameTimestamp()));
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }
    public static boolean preflight(final Player ai, final SpellAbility sa) {
        if (!usesSpell(sa) && !usesActivation(sa) && !PLANS.containsKey(sa)) { return true; }
        final Plan p = PLANS.get(sa);
        if (p == null || p.source().get() != sa.getHostCard() || p.sourceStamp() != sa.getHostCard().getGameTimestamp()) { return false; }
        final Choice c = choice(ai, sa);
        return c != null && c.target() == p.target().get() && c.target().getGameTimestamp() == p.targetStamp()
                && c.attacker() == p.attacker().get() && (c.attacker() == null || c.attacker().getGameTimestamp() == p.attackerStamp())
                && (!usesActivation(sa) || sa.getTargetCard() == c.target() && sa.isTargetNumberValid());
    }
    public static void played(final Player ai, final SpellAbility sa) {
        if (usesActivation(sa)) {
            final int turn = ai.getGame().getPhaseHandler().getTurn();
            final List<Use> history = USED.computeIfAbsent(ai.getGame(), g -> new ArrayList<>());
            history.removeIf(u -> u.source().get() == null || u.turn() != turn);
            history.add(new Use(new WeakReference<>(sa.getHostCard()), sa.getHostCard().getGameTimestamp(), turn));
        }
    }
}
