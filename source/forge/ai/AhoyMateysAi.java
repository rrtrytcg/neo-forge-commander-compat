package forge.ai;

import forge.game.ability.AbilityKey;
import forge.game.ability.ApiType;
import forge.game.ability.effects.CharmEffect;
import forge.game.card.*;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.replacement.*;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;

import java.util.*;

/** The owner's simple Ahoy Mateys rules, activated only by per-card profile logic. */
public final class AhoyMateysAi {
    private AhoyMateysAi() { }

    public static boolean uses(final SpellAbility sa, final String logic) {
        return sa != null && logic.equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()));
    }

    private static AiAbilityDecision decision(final boolean yes) {
        return new AiAbilityDecision(yes ? 100 : 0, yes ? AiPlayDecision.WillPlay : AiPlayDecision.CantPlayAi);
    }

    private static boolean active(final ReplacementEffect re) {
        return !re.isSuppressed() && re.zonesCheck(re.getHostCard().getGame().getZoneOf(re.getHostCard()));
    }

    private static boolean drawSafe(final Player ai, final int amount) {
        if (ai.getCardsIn(ZoneType.Library).size() < amount || !ai.canDraw(amount)) {
            return false;
        }
        // Do not promise a draw count or decking safety through unmodelled draw replacements.
        for (final Card card : ai.getGame().getCardsIn(ZoneType.STATIC_ABILITIES_SOURCE_ZONES)) {
            for (final ReplacementEffect re : card.getReplacementEffects()) {
                if (active(re) && (re.getMode() == ReplacementType.Draw || re.getMode() == ReplacementType.DrawCards)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasAdmiral(final Player ai) {
        for (final Card card : ai.getCardsIn(ZoneType.Battlefield, ZoneType.Command)) {
            if ("Admiral Brass, Unsinkable".equals(card.getName())) {
                return true;
            }
        }
        return false;
    }

    /** Preferred legal discards only. Mandatory remaining choices use the controller's native fallback. */
    public static CardCollection lootingDiscards(final Player ai, final Iterable<Card> options,
            final SpellAbility sa, final int maximum) {
        final CardCollection result = new CardCollection();
        final List<Card> pirates = new ArrayList<>();
        final List<Card> lands = new ArrayList<>();
        for (final Card card : options) {
            if (card.equals(sa.getHostCard()) || !card.canBeDiscardedBy(sa, true)) {
                continue;
            }
            if (card.isLand()) {
                lands.add(card);
            } else if (card.isCreature() && card.getType().hasSubtype("Pirate") && hasAdmiral(ai)) {
                final SpellAbility permanent = card.getSpellPermanent();
                if (permanent != null && !ComputerUtilMana.canPayManaCost(permanent.copy(ai), ai, 0, false)) {
                    pirates.add(card);
                }
            }
        }
        final int keepLands = Math.max(0, 5 - ai.getLandsInPlay().size()) + 1;
        final int excess = Math.max(0, lands.size() - keepLands);
        for (int i = 0; i < excess && result.size() < maximum; i++) {
            result.add(lands.get(i));
        }
        // Highest mana value first, preserving hand order on ties; at most one Pirate per Looting.
        pirates.sort(Comparator.comparingInt((Card c) -> c.getCMC()).reversed());
        if (!pirates.isEmpty() && result.size() < maximum) {
            result.add(pirates.get(0));
        }
        return result;
    }

    public static AiAbilityDecision looting(final Player ai, final SpellAbility sa) {
        return decision(drawSafe(ai, 2) && lootingDiscards(ai, ai.getCardsIn(ZoneType.Hand), sa, 2).size() >= 2);
    }

    /** Public hand counts only: protected players do not contribute to the expected discard maximum. */
    public static int windfallDraw(final Player ai, final SpellAbility sa) {
        int most = 0;
        for (final Player player : ai.getGame().getPlayers()) {
            if (!player.canDiscardBy(sa, true)) {
                continue;
            }
            int count = player.getCardsIn(ZoneType.Hand).size();
            if (player.getCardsIn(ZoneType.Hand).contains(sa.getHostCard())) {
                count--;
            }
            most = Math.max(most, count);
        }
        return most;
    }

    public static AiAbilityDecision windfall(final Player ai, final SpellAbility sa) {
        final int ours = ai.getCardsIn(ZoneType.Hand).size()
                - (ai.getCardsIn(ZoneType.Hand).contains(sa.getHostCard()) ? 1 : 0);
        final int draw = windfallDraw(ai, sa);
        return decision(ours <= 2 && draw >= 4 && drawSafe(ai, draw));
    }

    // Only exact, order-independent doubling and full-prevention templates are safe to aggregate.
    // Native matching checks source/controller, target, active zones, suppression, and prevention bans.
    private static boolean doubleDamage(final ReplacementEffect re) {
        final SpellAbility effect = re.getOverridingAbility();
        return effect != null && effect.getApi() == ApiType.ReplaceEffect && effect.getSubAbility() == null
                && "DamageAmount".equals(effect.getParam("VarName"))
                && "ReplaceCount$DamageAmount/Twice".equals(effect.getSVar(effect.getParamOrDefault("VarValue", "")))
                && Set.of("DB", "VarName", "VarValue").containsAll(effect.getMapParams().keySet())
                && Set.of("Event", "ActiveZones", "ValidSource", "ValidTarget", "ReplaceWith", "Description", "Secondary", "Layer")
                        .containsAll(re.getMapParams().keySet());
    }

    private static boolean fullPrevention(final ReplacementEffect re) {
        return "True".equals(re.getParam("Prevent"))
                && Set.of("Event", "ActiveZones", "ValidSource", "ValidTarget", "Prevent", "Description", "Secondary", "Layer", "IsCombat")
                        .containsAll(re.getMapParams().keySet());
    }

    private static boolean damageKnown(final Player ai) {
        for (final Card card : ai.getGame().getCardsIn(ZoneType.STATIC_ABILITIES_SOURCE_ZONES)) {
            for (final ReplacementEffect re : card.getReplacementEffects()) {
                if (!active(re)) {
                    continue;
                }
                if (re.getMode() == ReplacementType.LifeReduced || re.getMode() == ReplacementType.DealtDamage
                        || re.getMode() == ReplacementType.DamageDone && !doubleDamage(re) && !fullPrevention(re)) {
                    return false;
                }
            }
        }
        for (final Card creature : CardLists.filter(ai.getGame().getCardsIn(ZoneType.Battlefield), Card::isCreature)) {
            if (creature.hasKeyword(Keyword.INFECT) || creature.hasKeyword(Keyword.LIFELINK)) {
                return false;
            }
        }
        return true;
    }

    /** -1 means unknown. The cause remains Rakdos Charm; each creature is the actual damage source. */
    public static int charmDamage(final Player target, final SpellAbility cause) {
        if (!damageKnown(target)) {
            return -1;
        }
        int total = 0;
        for (final Card creature : target.getCreaturesInPlay()) {
            if (creature.isPhasedOut()) {
                continue;
            }
            final Map<AbilityKey, Object> params = AbilityKey.mapFromAffected(target);
            params.put(AbilityKey.DamageSource, creature);
            params.put(AbilityKey.DamageAmount, 1);
            params.put(AbilityKey.IsCombat, false);
            params.put(AbilityKey.NoPreventDamage, !creature.canDamagePrevented(false));
            params.put(AbilityKey.Cause, cause);
            int damage = 1;
            for (final ReplacementLayer layer : ReplacementLayer.values()) {
                for (final ReplacementEffect re : target.getGame().getReplacementHandler()
                        .getReplacementList(ReplacementType.DamageDone, params, layer)) {
                    if (fullPrevention(re) && creature.canDamagePrevented(false)) {
                        damage = 0;
                    } else if (doubleDamage(re)) {
                        damage = (int) Math.min(Integer.MAX_VALUE, (long) damage * 2);
                    } else if (!fullPrevention(re)) {
                        return -1;
                    }
                }
            }
            total = (int) Math.min(Integer.MAX_VALUE, (long) total + damage);
        }
        return target.canLoseLife() ? total : 0;
    }

    private static boolean lethalDamage(final Player ai, final SpellAbility mode) {
        final int ownDamage = charmDamage(ai, mode);
        if (ownDamage < 0 || ownDamage >= ai.getLife()) {
            return false;
        }
        for (final Player opponent : ai.getOpponents()) {
            final int damage = charmDamage(opponent, mode);
            if (damage > 0 && damage >= opponent.getLife() && !opponent.cantLoseForZeroOrLessLife()) {
                return true;
            }
        }
        return false;
    }

    private static Player reactiveGraveyard(final Player ai, final SpellAbility exile) {
        for (final SpellAbilityStackInstance item : ai.getGame().getStack()) {
            final SpellAbility pending = item.getSpellAbility();
            if (pending.getActivatingPlayer() == null || !pending.getActivatingPlayer().isOpponentOf(ai)) {
                continue;
            }
            boolean returnsFromExile = false;
            for (SpellAbility part = pending; part != null; part = part.getSubAbility()) {
                returnsFromExile |= "Exile".equals(part.getParam("Origin"))
                        && "Battlefield".equals(part.getParam("Destination"));
            }
            for (SpellAbility part = pending; part != null; part = part.getSubAbility()) {
                for (final Card target : part.getTargets().getTargetCards()) {
                    if (target.isInZone(ZoneType.Graveyard) && target.getOwner().isOpponentOf(ai)
                            && exile.canTarget(target.getOwner())) {
                        return target.getOwner();
                    }
                }
                // Public mass recursion with no individual card targets (e.g. Living Death).
                if (part.getParamOrDefault("Origin", "").contains("Graveyard")
                        && ("Battlefield".equals(part.getParam("Destination"))
                        || "Exile".equals(part.getParam("Destination")) && returnsFromExile) && !part.usesTargeting()) {
                    for (final Player opponent : ai.getOpponents()) {
                        if (exile.canTarget(opponent) && !CardLists.getValidCards(opponent.getCardsIn(ZoneType.Graveyard),
                                part.getParamOrDefault("ChangeType", "Card"), pending.getActivatingPlayer(),
                                pending.getHostCard(), part).isEmpty()) {
                            return opponent;
                        }
                    }
                }
            }
        }
        return null;
    }

    public static AiAbilityDecision rakdosCharm(final Player ai, final SpellAbility sa) {
        sa.setSubAbility(null);
        sa.setChosenList(null);
        AbilitySub exile = null;
        AbilitySub destroy = null;
        AbilitySub damage = null;
        for (final AbilitySub option : CharmEffect.makePossibleOptions(sa)) {
            final AbilitySub mode = (AbilitySub) option.copy(ai);
            mode.resetTargets();
            if (mode.getApi() == ApiType.ChangeZoneAll) { exile = mode; }
            if (mode.getApi() == ApiType.Destroy) { destroy = mode; }
            if (mode.getApi() == ApiType.RepeatEach) { damage = mode; }
        }
        AbilitySub chosen = null;
        if (damage != null && lethalDamage(ai, damage)) {
            chosen = damage;
        }
        if (chosen == null && exile != null) {
            final Player reactive = reactiveGraveyard(ai, exile);
            if (reactive != null) {
                exile.getTargets().add(reactive);
                chosen = exile;
            }
        }
        if (chosen == null && destroy != null) {
            final List<Card> artifacts = new ArrayList<>();
            for (final Card card : ai.getOpponents().getCardsIn(ZoneType.Battlefield)) {
                if (card.isArtifact() && !card.isToken() && card.canBeDestroyed() && destroy.canTarget(card)) {
                    artifacts.add(card);
                }
            }
            final Card best = ComputerUtilCard.getBestArtifactAI(artifacts);
            if (best != null) {
                destroy.getTargets().add(best);
                chosen = destroy;
            }
        }
        if (chosen == null && exile != null) {
            Player best = null;
            int largest = -1;
            for (final Player opponent : ai.getOpponents()) {
                final int count = CardLists.filter(opponent.getCardsIn(ZoneType.Graveyard), c -> !c.isLand()).size();
                if (count >= 4 && count > largest && exile.canTarget(opponent)) {
                    largest = count;
                    best = opponent;
                }
            }
            if (best != null) {
                exile.getTargets().add(best);
                chosen = exile;
            }
        }
        if (chosen == null) {
            return decision(false);
        }
        sa.setChosenList(new ArrayList<>(List.of(chosen)));
        CharmEffect.chainAbilities(sa, sa.getChosenList());
        return decision(true);
    }
}
