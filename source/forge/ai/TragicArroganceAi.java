package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Bounded survivor planning for the four native ChooseEach categories. */
public final class TragicArroganceAi {
    private static final String[] TYPES = {"Artifact", "Creature", "Enchantment", "Planeswalker"};
    private static final Map<SpellAbility, Cursor> CURSORS = new WeakHashMap<>();

    private TragicArroganceAi() { }

    public static boolean uses(final SpellAbility sa) {
        return sa != null && "TragicArrogance".equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()));
    }

    public static int value(final Card card) {
        return ComputerUtilCard.evaluatePermanentList(new CardCollection(Collections.singletonList(card)));
    }

    private static int mask(final Card card) {
        int result = 0;
        for (int i = 0; i < TYPES.length; i++) {
            if (card.getType().hasStringType(TYPES[i])) { result |= 1 << i; }
        }
        return result;
    }

    private static SpellAbility sacrifice(final Player ai, final SpellAbility sa) {
        return sa.getHostCard().getFirstSpellAbility().getSubAbility().copy(ai);
    }

    public static final class Plan {
        public final Card[] choices;
        public final int savedValue;
        public final int savedCount;

        private Plan(final Card[] selected, final int score, final int count) {
            choices = selected;
            savedValue = score;
            savedCount = count;
        }

        public boolean keeps(final Card card) { return Arrays.asList(choices).contains(card); }
    }

    /** Sixteen coverage states, assigning each card any nonempty subset of its qualifying types once. */
    public static Plan plan(final Player ai, final Player controller, final SpellAbility sa) {
        final boolean friendly = !controller.isOpponentOf(ai);
        final SpellAbility cause = sacrifice(ai, sa);
        Plan[] states = new Plan[16];
        states[0] = new Plan(new Card[4], 0, 0);
        int required = 0;
        for (final Card card : controller.getCardsIn(ZoneType.Battlefield)) {
            final int types = mask(card);
            required |= types;
            if (types == 0) { continue; }
            final boolean vulnerable = !card.isLand() && card.canBeSacrificedBy(cause, true);
            final int weight = vulnerable ? value(card) : 0;
            final Plan[] next = states.clone();
            for (int covered = 0; covered < 16; covered++) {
                final Plan old = states[covered];
                if (old == null) { continue; }
                final int available = types & ~covered;
                for (int subset = available; subset > 0; subset = (subset - 1) & available) {
                    final Card[] choices = old.choices.clone();
                    for (int i = 0; i < 4; i++) {
                        if ((subset & (1 << i)) != 0) { choices[i] = card; }
                    }
                    final Plan candidate = new Plan(choices, old.savedValue + weight, old.savedCount + (vulnerable ? 1 : 0));
                    final int combined = covered | subset;
                    final Plan current = next[combined];
                    final int comparison = current == null ? 1 : Integer.compare(candidate.savedValue, current.savedValue);
                    final boolean better = current == null || (friendly ? comparison > 0 : comparison < 0)
                            || (comparison == 0 && (friendly ? candidate.savedCount > current.savedCount : candidate.savedCount < current.savedCount));
                    if (better) { next[combined] = candidate; }
                }
            }
            states = next;
        }
        return states[required];
    }

    public static synchronized AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        CURSORS.entrySet().removeIf(entry -> entry.getKey().getHostCard() == sa.getHostCard());
        if (!ComputerUtilCost.canPayCost(sa, ai, false)) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }
        final SpellAbility cause = sacrifice(ai, sa);
        int friendlyCount = 0, opposingCount = 0, friendlyValue = 0, opposingValue = 0;
        for (final Player player : ai.getGame().getPlayers()) {
            final Plan keep = plan(ai, player, sa);
            for (final Card card : player.getCardsIn(ZoneType.Battlefield)) {
                if (card.isLand() || keep.keeps(card) || !card.canBeSacrificedBy(cause, true)) { continue; }
                if (player.isOpponentOf(ai)) {
                    opposingCount++;
                    opposingValue += value(card);
                } else {
                    friendlyCount++;
                    friendlyValue += value(card);
                }
            }
        }
        return new AiAbilityDecision(opposingCount >= friendlyCount + 2 && opposingValue > friendlyValue ? 100 : 0,
                opposingCount >= friendlyCount + 2 && opposingValue > friendlyValue ? AiPlayDecision.WillPlay : AiPlayDecision.CantPlayAi);
    }

    private static final class Cursor {
        private final int playerId;
        private final WeakReference<SpellAbility> context;
        private final List<WeakReference<Card>> choices = new ArrayList<>();
        private int position;

        private Cursor(final Player player, final SpellAbility resolving, final Plan plan) {
            playerId = player.getId();
            context = new WeakReference<>(resolving);
            for (final Card card : plan.choices) {
                if (card != null) { choices.add(new WeakReference<>(card)); }
            }
        }
    }

    /** Native callbacks omit the category; preserve the script's fixed order, including repeated cards. */
    public static synchronized Card choose(final Player ai, final SpellAbility sa, final Iterable<Card> options) {
        final CardCollection legal = new CardCollection(options);
        if (legal.isEmpty()) { return null; }
        final Player player = legal.get(0).getController();
        final SpellAbility context = ai.getGame().getStack().isEmpty() ? null : ai.getGame().getStack().peekAbility();
        Cursor cursor = CURSORS.get(sa);
        if (cursor == null || cursor.playerId != player.getId() || cursor.context.get() != context) {
            cursor = new Cursor(player, context, plan(ai, player, sa));
            CURSORS.put(sa, cursor);
        }
        final Card chosen = cursor.position < cursor.choices.size() ? cursor.choices.get(cursor.position++).get() : null;
        if (cursor.position >= cursor.choices.size() || !legal.contains(chosen)) { CURSORS.remove(sa); }
        if (legal.contains(chosen)) { return chosen; }
        // Complete mandatory native choices even if an unexpected callback invalidates the plan.
        return player.isOpponentOf(ai) ? ComputerUtilCard.getWorstAI(legal) : ComputerUtilCard.getBestAI(legal);
    }
}
