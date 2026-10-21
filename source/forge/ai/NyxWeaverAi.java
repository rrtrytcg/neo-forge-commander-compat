package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameStateEvaluator;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.cost.CostExile;
import forge.game.cost.CostPartMana;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.lang.ref.WeakReference;
import java.util.*;

/** Opt-in cast/recovery decisions. The original mandatory mill and all live costs/movement stay native. */
public final class NyxWeaverAi {
    private record Choice(Card target, String reason) { }
    private record Plan(WeakReference<Player> player, WeakReference<Card> source, long sourceStamp,
                        WeakReference<Card> target, long targetStamp, String reason) { }
    private static final Map<SpellAbility, Plan> PLANS = Collections.synchronizedMap(new WeakHashMap<>());
    private NyxWeaverAi() { }

    public static boolean applies(final SpellAbility sa) {
        return sa != null && "NyxWeaver".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()));
    }

    private static boolean mill(final forge.game.trigger.Trigger t) {
        final var sa = t.ensureAbility();
        return "Phase".equals(t.getMode().name()) && "Upkeep".equals(t.getParam("Phase"))
                && "You".equals(t.getParam("ValidPlayer")) && "Battlefield".equals(t.getParam("TriggerZones"))
                && "TrigMill".equals(t.getParam("Execute"))
                && Set.of("Mode", "Phase", "ValidPlayer", "TriggerZones", "Execute", "TriggerDescription").containsAll(t.getMapParams().keySet())
                && sa != null && sa.getSubAbility() == null && sa.getTargets().isEmpty()
                && Map.of("DB", "Mill", "Defined", "You", "NumCards", "2").equals(sa.getMapParams());
    }

    private static boolean original(final SpellAbility sa) {
        final Card c = sa.getHostCard();
        if (c.isCloned() || c.isFaceDown() || c.isCommander() || c.getTriggers().size() != 1 || !mill(c.getTriggers().get(0))
                || sa.getPayCosts() == null || sa.getSubAbility() != null) { return false; }
        if (sa.isSpell()) { return sa.getApi() == ApiType.PermanentCreature && sa.getTargets().isEmpty()
                && sa.getPayCosts().hasOnlySpecificCostType(CostPartMana.class); }
        final var exile = sa.getPayCosts().getCostPartByType(CostExile.class);
        return sa.getApi() == ApiType.ChangeZone && "Graveyard".equals(sa.getParam("Origin"))
                && "Hand".equals(sa.getParam("Destination")) && "Card.YouCtrl".equals(sa.getParam("ValidTgts"))
                && Set.of("AB", "Cost", "Origin", "Destination", "ValidTgts", "TgtPrompt", "SpellDescription").containsAll(sa.getMapParams().keySet())
                && exile != null && exile.payCostFromSource() && "1".equals(exile.getAmount())
                && exile.getFrom().equals(List.of(ZoneType.Battlefield))
                && sa.getPayCosts().getCostParts().stream().allMatch(p -> p instanceof CostPartMana || p == exile);
    }

    private static boolean commanderReturn(final Card c, final forge.game.replacement.ReplacementEffect r) {
        final var s = r.getOverridingAbility();
        return c.isImmutable() && "Commander Effect".equals(c.getName()) && r.getMode().name().equals("Moved")
                && "Card.IsCommander+YouOwn".equals(r.getParam("ValidCard")) && "True".equals(r.getParam("CommanderMoveReplacement"))
                && "True".equals(r.getParam("Secondary")) && "True".equals(r.getParam("Optional"))
                && "You".equals(r.getParam("OptionalDecider")) && "Hand,Library".equals(r.getParam("Destination"))
                && Set.of("Event", "ValidCard", "CommanderMoveReplacement", "Secondary", "Optional", "OptionalDecider", "Destination", "Description").containsAll(r.getMapParams().keySet())
                && s != null && s.getApi() == ApiType.ChangeZone && s.getSubAbility() == null && s.getTargets().isEmpty()
                && Map.of("DB", "ChangeZone", "Origin", "Battlefield,Graveyard,Exile,Library,Hand", "Destination", "Command", "Defined", "ReplacedCard").equals(s.getMapParams());
    }

    private static boolean libraryEngine(final SpellAbility sa, final Set<SpellAbility> seen) {
        if (sa == null || !seen.add(sa)) { return false; }
        if (sa.getApi() != null && Set.of(ApiType.Draw, ApiType.Mill, ApiType.Dig, ApiType.DigUntil, ApiType.Reveal).contains(sa.getApi())
                || libraryEngine(sa.getSubAbility(), seen)) { return true; }
        for (final var s : sa.getAdditionalAbilities().values()) { if (libraryEngine(s, seen)) { return true; } }
        for (final var list : sa.getAdditionalAbilityLists().values()) { for (final var s : list) { if (libraryEngine(s, seen)) { return true; } } }
        return false;
    }

    private static boolean known(final Player ai) {
        for (final Card c : ai.getGame().getCardsIn(ZoneType.STATIC_ABILITIES_SOURCE_ZONES)) {
            if (c.isPhasedOut()) { continue; }
            if (c.isFaceDown()) { return false; }
            for (final var t : c.getTriggers()) {
                if (t.isSuppressed() || !t.zonesCheck(c.getZone())) { continue; }
                if ("Nyx Weaver".equals(c.getName()) && mill(t)) { continue; }
                final String mode = t.getMode().name();
                if (mode.startsWith("ChangesZone") || mode.contains("Cast") || mode.equals("AbilityResolves")
                        || mode.equals("AbilityTriggered") || mode.startsWith("Tap") || mode.contains("Life")
                        || libraryEngine(t.ensureAbility(), Collections.newSetFromMap(new IdentityHashMap<>()))) { return false; }
            }
            for (final var r : c.getReplacementEffects()) {
                if (r.isSuppressed() || !r.zonesCheck(c.getZone()) || !r.requirementsCheck(ai.getGame()) || commanderReturn(c, r)) { continue; }
                if (Set.of("Moved", "Tap", "ProduceMana", "Draw", "DrawCards", "Mill", "LifeReduced", "PayLife", "GainLife").contains(r.getMode().name())) { return false; }
            }
            for (final var st : c.getStaticAbilities()) {
                if (!st.isSuppressed() && st.zonesCheck() && (st.hasParam("GainControl") || st.hasParam("AddType")
                        || st.hasParam("RemoveType") || st.hasParam("RemoveAllAbilities") || st.getMode().stream().anyMatch(m ->
                            m.name().contains("Life") || m.name().contains("GainControl") || m.name().equals("IgnoreLegendRule")))) { return false; }
            }
            for (final var mana : c.getManaAbilities()) { if (c.isInPlay() && mana.getSubAbility() != null) { return false; } }
        }
        return true;
    }

    private static boolean base(final Player ai, final SpellAbility sa) {
        if (!applies(sa) || !original(sa) || Thread.currentThread().isInterrupted() || ai.getGame().EXPERIMENTAL_RESTORE_SNAPSHOT
                || !known(ai) || sa.getHostCard().getOwner() != ai || sa.getHostCard().getController() != ai
                || !sa.canPlay() || !sa.canCastTiming(ai) || !sa.checkRestrictions(ai)) { return false; }
        for (final Player p : ai.getGame().getPlayers()) { if (!p.isOpponentOf(ai) && (p.getLife() < 5 || p.hasLost())) { return false; } }
        return true;
    }

    private static boolean castable(final Player ai, final SpellAbility sa) {
        if (!base(ai, sa) || !sa.isSpell() || !ai.getGame().getPhaseHandler().is(PhaseType.MAIN2, ai)
                || !ai.getGame().getStack().isEmpty() || !sa.getHostCard().isInZone(ZoneType.Hand)
                || ai.getCardsIn(ZoneType.Hand).stream().noneMatch(c -> c == sa.getHostCard())
                || ai.getCardsIn(ZoneType.Library).size() < 8 || ai.isCardInPlay("Nyx Weaver")) { return false; }
        final var copier = new GameCopier(ai.getGame(), true); copier.makeCopy(null, ai);
        return CommandBeaconAi.canPayProjection(copier.find(ai), sa.copy(copier.find(sa.getHostCard()), copier.find(ai), false));
    }

    private static String reason(final Player ai, final SpellAbility sa) {
        final var ph = ai.getGame().getPhaseHandler(); final Card source = sa.getHostCard();
        final boolean declared = ai.getGame().getCombat() != null && ph.is(PhaseType.COMBAT_DECLARE_BLOCKERS)
                && ai.getGame().getCombat().getAttackers().stream().allMatch(c -> ai.getGame().getCombat().getBandOfAttacker(c) != null
                    && ai.getGame().getCombat().getBandOfAttacker(c).isBlocked() != null);
        if ((declared || !ai.getGame().getStack().isEmpty()) && DoomedCreatureAi.isDoomed(ai, source, !declared)
                && !ComputerUtilCombat.willOpposingCreatureDieInCombat(ai, source, ai.getGame().getCombat())
                && !ComputerUtilCombat.isDangerousToSacInCombat(ai, source, ai.getGame().getCombat())) { return "doomed"; }
        if (!ai.getGame().getStack().isEmpty()) { return ""; }
        final boolean main = ph.is(PhaseType.MAIN2, ai);
        if (ai.getCardsIn(ZoneType.Library).size() <= 4 && (main || !ph.isPlayerTurn(ai)
                && ph.is(PhaseType.END_OF_TURN) && ph.getNextTurn() == ai)) { return "library"; }
        return main ? "value" : "";
    }

    private static boolean usable(final Player ai, final SpellAbility sa, final Card c) {
        return c != null && c.getOwner() == ai && c.getController() == ai && !c.isCommander() && !c.isFaceDown()
                && c.isInZone(ZoneType.Graveyard) && ai.getCardsIn(ZoneType.Graveyard).stream().anyMatch(x -> x == c)
                && sa.canTarget(c) && (c.isLand() || !ComputerUtilCard.isCardRemAIDeck(c) || AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(c));
    }

    private static boolean forecast(final Player ai, final SpellAbility sa, final Card target, final boolean followup) {
        final var copier = new GameCopier(ai.getGame(), true); final var game = copier.makeCopy(null, ai);
        final Player payer = copier.find(ai); final Card source = copier.find(sa.getHostCard()); final Card returned = copier.find(target);
        final var action = sa.copy(source, payer, false); action.resetTargets(); action.getTargets().add(returned);
        final var adjusted = forge.game.cost.CostAdjustment.adjust(action.getPayCosts(), action, false);
        if (adjusted.getCostParts().stream().anyMatch(c -> !(c instanceof CostPartMana) && !(c instanceof CostExile))
                || !CommandBeaconAi.canPayProjection(payer, action) || !CommandBeaconAi.payManaProjection(payer, action)
                || !action.getPayCosts().getCostPartByType(CostExile.class).canPay(action, payer, false)) { return false; }
        final var board = new ArrayList<>(game.getCardsIn(ZoneType.Battlefield));
        final var exiled = game.getAction().exile(source, action, null);
        if (exiled == null || !exiled.isInZone(ZoneType.Exile)) { return false; }
        AbilityUtils.resolve(action); game.getAction().checkStaticAbilities();
        final Card current = game.getCardState(returned, null);
        if (current == null || !current.isInZone(ZoneType.Hand)) { return false; }
        for (final Card c : board) {
            if (c == source) { continue; }
            final Card now = game.getCardState(c, null);
            if (now == null || !now.isInPlay() || now.getController() != c.getController()
                    || now.isCreature() && (now.getNetToughness() <= 0 || now.getDamage() >= now.getNetToughness()
                        && !now.hasKeyword(forge.game.keyword.Keyword.INDESTRUCTIBLE))) { return false; }
        }
        for (final Player p : ai.getGame().getPlayers()) { if (!p.isOpponentOf(ai)
                && (copier.find(p).getLife() < 5 || copier.find(p).hasLost() || copier.find(p).getLife() != p.getLife())) { return false; } }
        if (!followup) { return !Thread.currentThread().isInterrupted(); }
        for (final SpellAbility spell : current.getSpellAbilities()) {
            if (!spell.isSpell() || !spell.getPayCosts().isOnlyManaCost() || spell.getPayCosts().hasXInAnyCostPart()) { continue; }
            final var cast = spell.copy(payer);
            if (cast.canPlay() && cast.checkRestrictions(payer) && cast.canCastTiming(payer)
                    && CommandBeaconAi.canPayProjection(payer, cast)) { return !Thread.currentThread().isInterrupted(); }
        }
        return false;
    }

    private static Choice choice(final Player ai, final SpellAbility sa) {
        if (!base(ai, sa) || sa.isSpell() || sa.getHostCard().hasCardAttachments() || !sa.getHostCard().isInPlay() || sa.getHostCard().isPhasedOut()
                || ai.getCardsIn(ZoneType.Battlefield).stream().noneMatch(c -> c == sa.getHostCard())) { return null; }
        final String why = reason(ai, sa); if (why.isEmpty()) { return null; }
        Card best = null; final var selected = sa.copy(ai); selected.resetTargets();
        for (final Card c : ai.getCardsIn(ZoneType.Graveyard)) {
            if (!usable(ai, selected, c) || why.equals("value") && (c.isLand() || c.getCMC() < 4)
                    || !forecast(ai, selected, c, why.equals("value"))) { continue; }
            // Retain native recovery's creature-first, nonland-next priorities. Stable IDs replace its random land fallback.
            final int group = c.isCreature() ? 2 : c.isLand() ? 0 : 1;
            final int bestGroup = best == null ? -1 : best.isCreature() ? 2 : best.isLand() ? 0 : 1;
            final int score = score(c), bestScore = best == null ? -1 : score(best);
            if (best == null || group > bestGroup || group == bestGroup && (score > bestScore || score == bestScore && c.getId() < best.getId())) { best = c; }
        }
        return best == null ? null : new Choice(best, why);
    }

    private static int score(final Card c) { return c.isCreature() ? ComputerUtilCard.evaluateCreature(c)
            : c.isLand() ? GameStateEvaluator.evaluateLand(c) : c.getCMC(); }

    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        PLANS.remove(sa);
        try {
            final Choice picked = sa.isSpell() ? (castable(ai, sa) ? new Choice(null, "spell") : null) : choice(ai, sa);
            if (picked != null) {
                if (!sa.isSpell()) { sa.resetTargets(); sa.getTargets().add(picked.target());
                    if (!sa.isTargetNumberValid() || !forge.game.staticability.StaticAbilityMustTarget.meetsMustTargetRestriction(sa)) { return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi); } }
                PLANS.put(sa, new Plan(new WeakReference<>(ai), new WeakReference<>(sa.getHostCard()), sa.getHostCard().getGameTimestamp(),
                        new WeakReference<>(picked.target()), picked.target() == null ? 0 : picked.target().getGameTimestamp(), picked.reason()));
                return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
            }
        } catch (final RuntimeException unknown) { }
        if (!sa.isSpell()) { sa.resetTargets(); }
        return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }

    public static boolean preflight(final Player ai, final SpellAbility sa) {
        if (sa.isTrigger()) { return true; } // Mandatory upkeep mill remains entirely native.
        if (!applies(sa) && !PLANS.containsKey(sa)) { return true; }
        final Plan plan = PLANS.get(sa);
        if (plan == null || plan.player().get() != ai || plan.source().get() != sa.getHostCard()
                || plan.sourceStamp() != sa.getHostCard().getGameTimestamp()) { return false; }
        try {
            if (sa.isSpell()) { return plan.reason().equals("spell") && castable(ai, sa); }
            final Card target = plan.target().get(); final Choice fresh = choice(ai, sa);
            return target != null && target.getGameTimestamp() == plan.targetStamp() && sa.getTargets().size() == 1
                    && sa.getTargetCard() == target && sa.isTargetNumberValid()
                    && forge.game.staticability.StaticAbilityMustTarget.meetsMustTargetRestriction(sa)
                    && fresh != null && fresh.target() == target && fresh.reason().equals(plan.reason());
        } catch (final RuntimeException unknown) { return false; }
    }
}
