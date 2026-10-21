package forge.ai;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCopyService;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

/** Bounded opt-in neutralization policy; native layers, attachment legality and payment remain authoritative. */
public final class DarksteelMutationAi {
    private DarksteelMutationAi() { }

    public static boolean useful(final Card target, final Card aura) {
        return useful(target, aura, false);
    }

    /** The Elk policy also requires a net downgrade without increasing either combat stat. */
    public static boolean useful(final Card target, final Card aura, final boolean requireNetDowngrade) {
        if (target.isEnchantedBy(aura.getName())) {
            return false;
        }
        final int combat = target.getNetCombatDamage();
        final int utility = ComputerUtilCard.evaluateCreature(target, false, false);
        final int total = requireNetDowngrade ? ComputerUtilCard.evaluateCreature(target) : 0;
        final int power = target.getNetPower(), toughness = target.getNetToughness();
        final boolean indestructible = target.hasKeyword(Keyword.INDESTRUCTIBLE);
        return preview(target, aura, copy -> {
            final int grantedProtection = !indestructible && copy.hasKeyword(Keyword.INDESTRUCTIBLE) ? 70 : 0;
            final int lostUtility = utility - ComputerUtilCard.evaluateCreature(copy, false, false) + grantedProtection;
            if (requireNetDowngrade && (copy.getNetPower() > power || copy.getNetToughness() > toughness
                    || ComputerUtilCard.evaluateCreature(copy) >= total)) { return false; }
            return combat - copy.getNetCombatDamage() >= 2 || lostUtility > 0;
        });
    }

    /** Preview an original Aura's layers without attaching live cards or consuming timestamps. */
    public static boolean preview(final Card target, final Card aura, final Predicate<Card> evaluate) {
        final Card copy = CardCopyService.getLKICopy(target);
        final Card attachment = CardCopyService.getLKICopy(aura);
        // Preview links only: attachToEntity would fire attachment events and triggers.
        attachment.setLastKnownZone(aura.getController().getZone(ZoneType.Battlefield));
        attachment.setEntityAttachedTo(copy);
        attachment.setLayerTimestamp(aura.getGame().getTimestamp() + 1);
        copy.addAttachedCard(attachment);
        final CardCollection preview = new CardCollection(copy);
        preview.add(attachment);
        try {
            aura.getGame().getAction().checkStaticAbilities(false, new HashSet<>(preview), preview);
            return evaluate.test(copy);
        } finally {
            aura.getGame().getAction().checkStaticAbilities(false);
        }
    }

    public static Card choose(final Player ai, final SpellAbility sa, final List<Card> legal) {
        final CardCollection useful = new CardCollection();
        for (Card card : legal) {
            if (card.isCreature() && card.getController().isOpponentOf(ai)
                    && useful(card, sa.getHostCard())) {
                useful.add(card);
            }
        }
        return ComputerUtilCard.getBestCreatureAI(useful);
    }
}
