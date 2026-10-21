package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.ability.effects.CharmEffect;
import forge.game.card.Card;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.lang.ref.WeakReference;
import java.util.*;

/** Bounded opposing artifact/enchantment removal; mode chains, costs and destruction remain native. */
public final class HullBreachAi {
    private record Choice(int mode, Card first, Card second) { }
    private record Plan(WeakReference<Player> player, WeakReference<Card> source, long sourceStamp,
                        int mode, WeakReference<Card> first, long firstStamp,
                        WeakReference<Card> second, long secondStamp) { }
    private static final Map<SpellAbility, Plan> PLANS = Collections.synchronizedMap(new WeakHashMap<>());
    private HullBreachAi() { }

    public static boolean uses(final SpellAbility sa) {
        if (sa == null || !sa.isSpell() || sa.getApi() != ApiType.Charm
                || !"HullBreach".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()))
                || !"DBDestroy1,DBDestroy2,DBDestroy3".equals(sa.getParam("Choices"))
                || !Set.of("SP", "Choices").containsAll(sa.getMapParams().keySet())) { return false; }
        final var modes = sa.getAdditionalAbilityList("Choices");
        if (modes == null || modes.size() != 3) { return false; }
        for (int i = 0; i < 2; i++) {
            final var m = modes.get(i);
            if (m.getApi() != ApiType.Destroy || m.getSubAbility() != null
                    || !(i == 0 ? "Artifact" : "Enchantment").equals(m.getParam("ValidTgts"))
                    || !Set.of("DB", "ValidTgts", "SpellDescription").containsAll(m.getMapParams().keySet())) { return false; }
        }
        final var a = modes.get(2); final var e = a.getSubAbility();
        if (e == null || a.getApi() != ApiType.Pump || e.getApi() != ApiType.Pump
                || !"Artifact".equals(a.getParam("ValidTgts")) || !"Enchantment".equals(e.getParam("ValidTgts"))
                || !"Destroy".equals(a.getParam("AILogic")) || !"Destroy".equals(e.getParam("AILogic"))
                || !Set.of("DB", "ValidTgts", "AILogic", "IsCurse", "SubAbility", "SpellDescription", "StackDescription").containsAll(a.getMapParams().keySet())
                || !Set.of("DB", "ValidTgts", "AILogic", "IsCurse", "SubAbility", "StackDescription").containsAll(e.getMapParams().keySet())) { return false; }
        final var d = e.getSubAbility();
        return d != null && d.getApi() == ApiType.Destroy && d.getSubAbility() == null
                && "Targeted".equals(d.getParam("Defined"))
                && Set.of("DB", "Defined").containsAll(d.getMapParams().keySet());
    }

    private static boolean known(final Player ai) { return knownDestruction(ai, false); }

    /** Shared public-state guard; only the native shield-counter template is optionally admitted. */
    static boolean knownDestruction(final Player ai, final boolean shieldCounters) {
        for (final Card c : ai.getGame().getCardsIn(ZoneType.STATIC_ABILITIES_SOURCE_ZONES)) {
            if (c.isPhasedOut()) { continue; }
            if (c.isFaceDown() || c.isCloned()) { return false; }
            for (final var t : c.getTriggers()) {
                if (t.isSuppressed() || !t.zonesCheck(c.getZone())) { continue; }
                final String mode = t.getMode().name();
                if (mode.contains("Target") || mode.contains("Cast") || mode.equals("AbilityResolves")
                        || mode.equals("AbilityTriggered") || mode.startsWith("Tap")
                        || mode.equals("Destroyed") || mode.equals("Unattached") || mode.startsWith("CounterRemoved")) { return false; }
                if (mode.startsWith("ChangesZone") && !"Battlefield".equals(t.getParam("Destination"))) { return false; }
            }
            for (final var r : c.getReplacementEffects()) {
                if (r.isSuppressed() || !r.zonesCheck(c.getZone()) || !r.requirementsCheck(ai.getGame())) { continue; }
                if (c.isImmutable() && "Commander Effect".equals(c.getName())) { continue; }
                if (r.getMode().name().equals("Moved") && "Card.Self".equals(r.getParam("ValidCard"))
                        && "Battlefield".equals(r.getParam("Destination"))) { continue; }
                if (shieldCounters && nativeShieldCounter(r)) { continue; }
                if (Set.of("Moved", "Destroy", "Tap", "GameLoss", "GameWin").contains(r.getMode().name())) { return false; }
            }
            // Omitted state-based legend/type/control choices must never authorize collateral losses.
            for (final var st : c.getStaticAbilities()) {
                if (!st.isSuppressed() && st.zonesCheck()) {
                    final boolean commandPermission = c.isImmutable() && "Commander Effect".equals(c.getName())
                            && "Continuous".equals(st.getParam("Mode")) && "True".equals(st.getParam("MayPlay"))
                            && "Card.IsCommander+YouOwn".equals(st.getParam("Affected"))
                            && "Command".equals(st.getParam("AffectedZone")) && "Command".equals(st.getParam("EffectZone"))
                            && Set.of("Mode", "MayPlay", "Affected", "AffectedZone", "EffectZone").containsAll(st.getMapParams().keySet());
                    if (st.getMode().stream().anyMatch(m -> m.name().equals("Continuous")) && !commandPermission
                            && !Set.of("Mode", "Affected", "AddPower", "AddToughness", "SetPower", "SetToughness",
                                "AddKeyword", "Description", "EffectZone", "AffectedZone", "CharacteristicDefining",
                                "Secondary", "AddAllCreatureTypes").containsAll(st.getMapParams().keySet())) { return false; }
                    if (st.getMode().stream().anyMatch(m -> m.name().startsWith("CantLose") || m.name().equals("CantWin"))) { return false; }
                    for (final String key : List.of("AddPower", "AddToughness", "SetPower", "SetToughness")) {
                        if (st.hasParam(key) && !st.getParam(key).matches("-?[0-9]+")) { return false; }
                    }
                }
                if (!st.isSuppressed() && st.zonesCheck() && (st.hasParam("AddType") || st.hasParam("RemoveType")
                        || st.hasParam("GainControl") || st.hasParam("RemoveAllAbilities")
                        || st.getMode().stream().anyMatch(m -> Set.of("GainControl", "IgnoreLegendRule").contains(m.name())))) { return false; }
            }
        }
        return true;
    }

    static boolean nativeShieldCounter(final forge.game.replacement.ReplacementEffect r) {
        final var effect = r.getOverridingAbility();
        return r.getMode().name().equals("Destroy") && "True".equals(r.getParam("ShieldCounter"))
                && "Card.Self".equals(r.getParam("ValidCard")) && "SpellAbility".equals(r.getParam("ValidCause"))
                && "Battlefield".equals(r.getParam("ActiveZones")) && "True".equals(r.getParam("Secondary"))
                && Set.of("Event", "ActiveZones", "ValidCard", "ValidCause", "Secondary", "ShieldCounter", "Description").containsAll(r.getMapParams().keySet())
                && effect != null && effect.getApi() == ApiType.RemoveCounter && effect.getSubAbility() == null
                && "Self".equals(effect.getParam("Defined")) && "Shield".equals(effect.getParam("CounterType"))
                && "1".equals(effect.getParam("CounterNum"))
                && Set.of("DB", "Defined", "CounterType", "CounterNum").containsAll(effect.getMapParams().keySet());
    }

    private static boolean window(final Player ai, final SpellAbility sa) {
        final Card source = sa.getHostCard();
        final var ph = ai.getGame().getPhaseHandler();
        return uses(sa) && !Thread.currentThread().isInterrupted() && !ai.getGame().EXPERIMENTAL_RESTORE_SNAPSHOT
                && ai.getGame().getStack().isEmpty() && (ph.is(PhaseType.MAIN1, ai) || ph.is(PhaseType.MAIN2, ai))
                && source.getOwner() == ai && source.getController() == ai && source.isInZone(ZoneType.Hand)
                && ai.getCardsIn(ZoneType.Hand).stream().anyMatch(c -> c == source)
                && sa.canPlay() && sa.checkRestrictions(ai) && sa.canCastTiming(ai) && known(ai);
    }

    private static boolean eligible(final Player ai, final Card c, final SpellAbility mode) {
        return c.isInPlay() && !c.isPhasedOut() && !c.isFaceDown() && c.getController().isOpponentOf(ai)
                && mode.canTarget(c) && c.canBeDestroyed() && c.getShieldCount() == 0
                && c.getCounters(forge.game.card.CounterEnumType.SHIELD) == 0
                && c.getAttachedCards().stream().noneMatch(a -> a.isAura() && !a.getController().isOpponentOf(ai))
                && !ComputerUtil.canRegenerate(ai, c) && c.getSpellAbilities().stream().noneMatch(s -> s.getApi() == ApiType.Regenerate)
                && c.getKeywords().stream().noneMatch(k -> k.getKeyword() == Keyword.WARD);
    }

    private static void clear(final SpellAbility sa) {
        sa.setChosenList(null); sa.setSubAbility(null); sa.resetTargets();
        for (final var m : sa.getAdditionalAbilityList("Choices")) {
            for (SpellAbility p = m; p != null; p = p.getSubAbility()) { p.resetTargets(); }
        }
    }

    private static void bind(final SpellAbility sa, final Player ai, final Choice c) {
        clear(sa);
        final var mode = sa.getAdditionalAbilityList("Choices").get(c.mode());
        for (SpellAbility p = mode; p != null; p = p.getSubAbility()) { p.setActivatingPlayer(ai); }
        mode.getTargets().add(c.first());
        if (c.second() != null) { mode.getSubAbility().getTargets().add(c.second()); }
        sa.setChosenList(new ArrayList<>(List.of(mode)));
        CharmEffect.chainAbilities(sa, sa.getChosenList());
    }

    private static boolean targets(final SpellAbility sa, final Choice c) {
        if (sa.getChosenList() == null || sa.getChosenList().size() != 1
                || sa.getChosenList().get(0) != sa.getAdditionalAbilityList("Choices").get(c.mode())
                || !sa.getTargets().isEmpty()) { return false; }
        final var raw = sa.getChosenList().get(0);
        SpellAbility expected = raw;
        for (SpellAbility actual = sa.getSubAbility(); actual != null || expected != null;
                actual = actual.getSubAbility(), expected = expected.getSubAbility()) {
            if (actual == null || expected == null || actual.getApi() != expected.getApi()) { return false; }
            final var params = new HashMap<>(expected.getMapParams());
            if (expected == raw) { params.putIfAbsent("StackDescription", "SpellDescription"); }
            if (!params.equals(actual.getMapParams())) { return false; }
        }
        if (!targetChain(raw, c)) { return false; }
        return targetChain(sa.getSubAbility(), c)
                && forge.game.staticability.StaticAbilityMustTarget.meetsMustTargetRestriction(sa);
    }

    private static boolean targetChain(SpellAbility part, final Choice c) {
        if (part == null || part.getTargets().size() != 1 || part.getTargetCard() != c.first()
                || !part.canTarget(c.first()) || !part.isTargetNumberValid()) { return false; }
        if (c.second() == null) { return part.getSubAbility() == null; }
        part = part.getSubAbility();
        return part != null && part.getTargets().size() == 1 && part.getTargetCard() == c.second()
                && part.canTarget(c.second()) && part.isTargetNumberValid()
                && part.getSubAbility() != null && part.getSubAbility().getApi() == ApiType.Destroy
                && part.getSubAbility().getTargets().isEmpty()
                && "Targeted".equals(part.getSubAbility().getParam("Defined"))
                && part.getSubAbility().getSubAbility() == null;
    }

    private static boolean forecast(final Player ai, final SpellAbility sa, final Choice c) {
        try {
            final GameCopier copier = new GameCopier(ai.getGame(), true);
            final var game = copier.makeCopy(null, ai); final Player payer = copier.find(ai);
            final var spell = sa.copy(copier.find(sa.getHostCard()), payer, false);
            final Choice picked = new Choice(c.mode(), copier.find(c.first()), c.second() == null ? null : copier.find(c.second()));
            bind(spell, payer, picked);
            if (!targets(spell, picked) || !spell.canPlay() || !spell.checkRestrictions(payer) || !spell.canCastTiming(payer)
                    || !CommandBeaconAi.canPayProjection(payer, spell)) { return false; }
            final List<Card> friendly = new ArrayList<>();
            for (final Card card : game.getCardsIn(ZoneType.Battlefield)) {
                if (!card.getController().isOpponentOf(payer)) { friendly.add(card); }
            }
            AbilityUtils.resolve(spell);
            game.getAction().checkStaticAbilities();
            if (picked.first().isInPlay() || picked.second() != null && picked.second().isInPlay()) { return false; }
            for (final Card card : friendly) {
                if (!card.isInPlay() || card.getController().isOpponentOf(payer)
                        || card.isCreature() && (card.getNetToughness() <= 0
                            || card.getDamage() >= card.getNetToughness() && !card.hasKeyword(Keyword.INDESTRUCTIBLE))) { return false; }
            }
            return !Thread.currentThread().isInterrupted();
        } catch (final RuntimeException unknownProjection) { return false; }
    }

    private static Choice choice(final Player ai, final SpellAbility sa) {
        if (!window(ai, sa)) { return null; }
        final var selection = sa.copy(ai); clear(selection);
        final var modes = selection.getAdditionalAbilityList("Choices");
        for (final var mode : modes) {
            for (SpellAbility p = mode; p != null; p = p.getSubAbility()) { p.setActivatingPlayer(ai); }
        }
        final List<Card> pool = new ArrayList<>();
        for (final Card c : ai.getGame().getCardsIn(ZoneType.Battlefield)) {
            if (eligible(ai, c, modes.get(0)) || eligible(ai, c, modes.get(1))) { pool.add(c); }
        }
        pool.sort(Comparator.comparingInt(Card::getId));
        final List<Card> ranked = new ArrayList<>();
        while (!pool.isEmpty()) { final Card best = ComputerUtilCard.getBestRemovalTargetAI(ai, pool); ranked.add(best); pool.remove(best); }
        // Lexicographic native priority: include the best permanent, then its best distinct partner.
        for (final Card first : ranked) { for (final Card second : ranked) {
            if (first == second) { continue; }
            Choice pair = null;
            if (eligible(ai, first, modes.get(2)) && eligible(ai, second, modes.get(2).getSubAbility())) { pair = new Choice(2, first, second); }
            else if (eligible(ai, second, modes.get(2)) && eligible(ai, first, modes.get(2).getSubAbility())) { pair = new Choice(2, second, first); }
            if (pair != null) { return forecast(ai, sa, pair) ? pair : null; }
        } }
        if (ranked.isEmpty()) { return null; }
        final Card first = ranked.get(0);
        final Choice single = new Choice(eligible(ai, first, modes.get(0)) ? 0 : 1, first, null);
        return forecast(ai, sa, single) ? single : null;
    }

    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        PLANS.remove(sa); clear(sa);
        final Choice c = choice(ai, sa);
        if (c == null) { return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi); }
        bind(sa, ai, c);
        PLANS.put(sa, new Plan(new WeakReference<>(ai), new WeakReference<>(sa.getHostCard()), sa.getHostCard().getGameTimestamp(),
                c.mode(), new WeakReference<>(c.first()), c.first().getGameTimestamp(), new WeakReference<>(c.second()),
                c.second() == null ? 0 : c.second().getGameTimestamp()));
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }

    public static boolean preflight(final Player ai, final SpellAbility sa) {
        if (!uses(sa) && !PLANS.containsKey(sa)) { return true; }
        final Plan p = PLANS.get(sa); final Choice c = choice(ai, sa);
        return p != null && c != null && p.player().get() == ai && p.source().get() == sa.getHostCard()
                && p.sourceStamp() == sa.getHostCard().getGameTimestamp() && p.mode() == c.mode()
                && p.first().get() == c.first() && p.firstStamp() == c.first().getGameTimestamp()
                && p.second().get() == c.second() && (c.second() == null || p.secondStamp() == c.second().getGameTimestamp())
                && targets(sa, c);
    }
}
