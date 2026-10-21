package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.cost.CostPartMana;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.lang.ref.WeakReference;
import java.util.*;

/** Bounded, opt-in mass destruction. Original native costs, destruction and life loss remain authoritative. */
public final class DeadlyTempestAi {
    private record Plan(WeakReference<Player> player, WeakReference<Card> source, long stamp) { }
    private record Victim(Card card, Player controller, int value) { }
    private static final Map<SpellAbility, Plan> PLANS = Collections.synchronizedMap(new WeakHashMap<>());
    private DeadlyTempestAi() { }

    public static boolean applies(final SpellAbility sa) {
        return sa != null && "DeadlyTempest".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()));
    }

    public static boolean uses(final SpellAbility sa) { return applies(sa) && original(sa); }

    private static boolean original(final SpellAbility sa) {
        if (!sa.isSpell() || sa.getApi() != ApiType.DestroyAll || !"Creature".equals(sa.getParam("ValidCards"))
                || !"True".equals(sa.getParam("RememberDestroyed")) || !"DBRepeat".equals(sa.getParam("SubAbility"))
                || !Set.of("SP", "ValidCards", "RememberDestroyed", "SubAbility", "SpellDescription").containsAll(sa.getMapParams().keySet())
                || !"RememberedLKI$Valid Card.RememberedPlayerCtrl".equals(sa.getSVar("X"))
                || !sa.getTargets().isEmpty() || sa.getPayCosts() == null
                || !sa.getPayCosts().hasOnlySpecificCostType(CostPartMana.class)) { return false; }
        final var repeat = sa.getSubAbility();
        if (repeat == null || repeat.getApi() != ApiType.RepeatEach || !repeat.getTargets().isEmpty()
                || !Map.of("DB", "RepeatEach", "RepeatPlayers", "Player", "RepeatSubAbility", "DBLoseLife", "SubAbility", "DBCleanup").equals(repeat.getMapParams())) { return false; }
        final var loss = repeat.getAdditionalAbility("RepeatSubAbility");
        final var cleanup = repeat.getSubAbility();
        return loss != null && loss.getApi() == ApiType.LoseLife && loss.getSubAbility() == null && loss.getTargets().isEmpty()
                && Map.of("DB", "LoseLife", "LifeAmount", "X", "Defined", "Player.IsRemembered").equals(loss.getMapParams())
                && cleanup != null && cleanup.getApi() == ApiType.Cleanup && cleanup.getSubAbility() == null && cleanup.getTargets().isEmpty()
                && Map.of("DB", "Cleanup", "ClearRemembered", "True").equals(cleanup.getMapParams());
    }

    private static boolean lifeEngine(final SpellAbility sa, final Set<SpellAbility> seen) {
        if (sa == null || !seen.add(sa)) { return false; }
        if (sa.getApi() != null && Set.of(ApiType.LoseLife, ApiType.GainLife, ApiType.SetLife, ApiType.ExchangeLife, ApiType.ExchangeLifeVariant).contains(sa.getApi())
                || lifeEngine(sa.getSubAbility(), seen)) { return true; }
        for (final var extra : sa.getAdditionalAbilities().values()) { if (lifeEngine(extra, seen)) { return true; } }
        for (final var list : sa.getAdditionalAbilityLists().values()) {
            for (final var extra : list) { if (lifeEngine(extra, seen)) { return true; } }
        }
        return false;
    }

    private static boolean known(final Player ai) {
        if (!HullBreachAi.knownDestruction(ai, true)) { return false; }
        for (final Card c : ai.getGame().getCardsIn(ZoneType.STATIC_ABILITIES_SOURCE_ZONES)) {
            if (c.isPhasedOut()) { continue; }
            // An active regeneration effect or a potential regeneration choice is deferred.
            // A response after the real cast remains entirely native.
            if (c.getShieldCount() > 0 || c.getSpellAbilities().stream().anyMatch(s -> s.getApi() == ApiType.Regenerate)) { return false; }
            if (c.isCreature() && c.isInPlay() && (c.getAttachedCards().stream().anyMatch(Card::isAura)
                    || c.getStaticAbilities().stream().anyMatch(s -> !s.isSuppressed() && s.zonesCheck()
                        && s.getMode().stream().anyMatch(m -> m.name().equals("Continuous"))))) { return false; }
            for (final var st : c.getStaticAbilities()) {
                if (!st.isSuppressed() && st.zonesCheck() && st.getMode().stream().anyMatch(m ->
                        Set.of("CantChangeLife", "CantGainLife", "CantPayLife").contains(m.name()))) { return false; }
            }
            for (final var t : c.getTriggers()) {
                if (t.isSuppressed() || !t.zonesCheck(c.getZone())) { continue; }
                if (t.getMode().name().contains("Life") || t.getMode().name().startsWith("Damage")
                        || lifeEngine(t.ensureAbility(), Collections.newSetFromMap(new IdentityHashMap<>()))) { return false; }
            }
            for (final var r : c.getReplacementEffects()) {
                if (r.isSuppressed() || !r.zonesCheck(c.getZone()) || !r.requirementsCheck(ai.getGame())) { continue; }
                if (Set.of("LifeReduced", "PayLife", "GainLife", "DamageDone", "DealtDamage").contains(r.getMode().name())) {
                    // Shield-counter damage prevention cannot affect this spell's non-damage life loss.
                    if (r.getMode().name().equals("DamageDone") && c.getCounters(forge.game.card.CounterEnumType.SHIELD) > 0
                            && "Card.Self".equals(r.getParam("ValidTarget")) && "True".equals(r.getParam("AlwaysReplace"))
                            && "True".equals(r.getParam("PreventionEffect")) && "True".equals(r.getParam("Secondary"))
                            && "Battlefield".equals(r.getParam("ActiveZones"))
                            && Set.of("Event", "ActiveZones", "ValidTarget", "PreventionEffect", "AlwaysReplace", "Secondary", "Description").containsAll(r.getMapParams().keySet())
                            && r.getOverridingAbility() != null && r.getOverridingAbility().getSubAbility() == null
                            && Map.of("DB", "RemoveCounter", "Defined", "Self", "CounterType", "Shield", "CounterNum", "1").equals(r.getOverridingAbility().getMapParams())) { continue; }
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean window(final Player ai, final SpellAbility sa) {
        final Card source = sa.getHostCard();
        return uses(sa) && !Thread.currentThread().isInterrupted() && !ai.getGame().EXPERIMENTAL_RESTORE_SNAPSHOT
                && ai.getGame().getStack().isEmpty() && ai.getGame().getPhaseHandler().is(PhaseType.MAIN2, ai)
                && source.getOwner() == ai && source.getController() == ai && source.isInZone(ZoneType.Hand)
                && ai.getCardsIn(ZoneType.Hand).stream().anyMatch(c -> c == source)
                && sa.canPlay() && sa.checkRestrictions(ai) && sa.canCastTiming(ai) && known(ai);
    }

    private static boolean safe(final Player ai, final SpellAbility sa) {
        try {
            if (!window(ai, sa)) { return false; }
            final GameCopier copier = new GameCopier(ai.getGame(), true);
            final var game = copier.makeCopy(null, ai); final Player payer = copier.find(ai);
            final var spell = sa.copy(copier.find(sa.getHostCard()), payer, false);
            if (!forge.game.cost.CostAdjustment.adjust(spell.getPayCosts(), spell, false).hasOnlySpecificCostType(CostPartMana.class)) { return false; }
            if (!spell.canPlay() || !spell.checkRestrictions(payer) || !spell.canCastTiming(payer)
                    || !CommandBeaconAi.canPayProjection(payer, spell) || !CommandBeaconAi.payManaProjection(payer, spell)) { return false; }
            final List<Victim> creatures = new ArrayList<>();
            final List<Card> others = new ArrayList<>();
            for (final Card c : game.getCardsIn(ZoneType.Battlefield)) {
                if (c.isCreature() && !c.isPhasedOut()) { creatures.add(new Victim(c, c.getController(), ComputerUtilCard.evaluateCreature(c))); }
                else { others.add(c); }
            }
            final Map<Player,Integer> life = new HashMap<>();
            for (final Player p : game.getPlayers()) { life.put(p, p.getLife()); }
            AbilityUtils.resolve(spell);
            game.getAction().checkStaticAbilities();
            int enemy = 0, friendly = 0; long enemyValue = 0, friendlyValue = 0;
            final Map<Player,Integer> deaths = new HashMap<>();
            for (final Victim v : creatures) {
                final Card current = game.getCardState(v.card(), null);
                if (current != null && current.isInPlay()) {
                    if (current.getController() != v.controller() || current.getNetToughness() <= 0
                            || current.getDamage() >= current.getNetToughness() && !current.hasKeyword(Keyword.INDESTRUCTIBLE)) { return false; }
                    continue;
                }
                // Native destroyed tokens can already be absent from the current-card index.
                // Their original graveyard transition and the native life-loss chain must still agree.
                if (current == null ? !(v.card().isToken() && v.card().isInZone(ZoneType.Graveyard))
                        : !current.isInZone(ZoneType.Graveyard)) { return false; }
                deaths.merge(v.controller(), 1, Integer::sum);
                if (v.controller().isOpponentOf(payer)) { enemy++; enemyValue += v.value(); }
                else { friendly++; friendlyValue += v.value(); }
            }
            for (final Card c : others) { if (!c.isInPlay()) { return false; } }
            for (final Player p : ai.getGame().getPlayers()) {
                final Player projected = copier.find(p);
                // Original native chain must agree with the public last-known-controller casualty count.
                final int expected = life.get(projected) - deaths.getOrDefault(projected, 0);
                if (projected.getLife() != expected) { return false; }
                if (!p.isOpponentOf(ai) && (projected.getLife() < 5 || projected.hasLost())) { return false; }
            }
            return enemy >= friendly + 2 && enemyValue > friendlyValue && !Thread.currentThread().isInterrupted();
        } catch (final RuntimeException unknownProjection) { return false; }
    }

    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        PLANS.remove(sa);
        final boolean play = safe(ai, sa);
        if (play) { PLANS.put(sa, new Plan(new WeakReference<>(ai), new WeakReference<>(sa.getHostCard()), sa.getHostCard().getGameTimestamp())); }
        return new AiAbilityDecision(play ? 100 : 0, play ? AiPlayDecision.WillPlay : AiPlayDecision.CantPlayAi);
    }

    public static boolean preflight(final Player ai, final SpellAbility sa) {
        if (!applies(sa) && !PLANS.containsKey(sa)) { return true; }
        final Plan p = PLANS.get(sa);
        return p != null && p.player().get() == ai && p.source().get() == sa.getHostCard()
                && p.stamp() == sa.getHostCard().getGameTimestamp() && safe(ai, sa);
    }
}
