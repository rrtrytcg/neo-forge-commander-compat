package forge.ai;

import java.util.List;

import forge.card.MagicColor;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/** Public-board color choices and a conservative cast forecast for Selective Obliteration. */
public final class SelectiveObliterationAi {
    private static final String CARD_NAME = "Selective Obliteration";
    private static final String LOGIC = "SelectiveObliteration";

    private SelectiveObliterationAi() { }

    public static boolean uses(final SpellAbility sa) {
        if (sa == null || sa.getHostCard() == null || !CARD_NAME.equals(sa.getHostCard().getName())) {
            return false;
        }
        return LOGIC.equals(AiOverrideProfile.get().getAiLogic(sa.getHostCard()));
    }

    public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
        if (!uses(sa)) { return null; }
        final boolean play = ComputerUtilCost.canPayCost(sa, ai, false) && shouldPlay(ai);
        return new AiAbilityDecision(play ? 100 : 0, play ? AiPlayDecision.WillPlay : AiPlayDecision.CantPlayAi);
    }

    /** Selects the best mono-colored battlefield group belonging to the player being asked. */
    public static String chooseColor(final SpellAbility sa, final List<String> options) {
        if (!uses(sa) || !sa.hasParam("Defined") || !"Remembered".equals(sa.getParam("Defined"))) {
            return null;
        }
        final List<Player> defined = AbilityUtils.getDefinedPlayers(sa.getHostCard(), "Remembered", sa);
        if (defined.isEmpty()) { return null; }
        return preferredColor(defined.get(0), options);
    }

    private static String preferredColor(final Player player, final List<String> options) {
        String best = null;
        int bestValue = Integer.MIN_VALUE;
        for (byte color : MagicColor.WUBRG) {
            final String colorName = MagicColor.toLongString(color);
            final String name = options.stream().filter(option -> option.equalsIgnoreCase(colorName)).findFirst().orElse(null);
            if (name == null) { continue; }
            final CardCollection permanents = new CardCollection();
            for (final Card card : player.getCardsIn(ZoneType.Battlefield)) {
                if (card.getColor().isMonoColor() && card.isOfColor(colorName)) {
                    permanents.add(card);
                }
            }
            final int value = ComputerUtilCard.evaluatePermanentList(permanents);
            if (best == null || value > bestValue) {
                best = name;
                bestValue = value;
            }
        }
        return best != null ? best : options.get(0);
    }

    private static boolean shouldPlay(final Player ai) {
        int friendlyLoss = 0;
        int opposingLoss = 0;
        final List<String> colors = List.of("White", "Blue", "Black", "Red", "Green");
        for (final Player player : ai.getGame().getPlayers()) {
            final String preserved = preferredColor(player, colors);
            final CardCollection lost = new CardCollection();
            for (final Card card : player.getCardsIn(ZoneType.Battlefield)) {
                if (!card.isColorless() && !(card.getColor().isMonoColor() && card.isOfColor(preserved))) {
                    lost.add(card);
                }
            }
            final int value = ComputerUtilCard.evaluatePermanentList(lost);
            if (player.isOpponentOf(ai)) { opposingLoss += value; }
            else { friendlyLoss += value; }
        }
        return opposingLoss > friendlyLoss;
    }
}
