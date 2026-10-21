import forge.ai.*;
import forge.card.ColorSet;
import forge.card.mana.ManaCost;
import forge.card.mana.ManaCostShard;
import forge.game.ability.AbilityFactory;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.keyword.Keyword;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.game.mana.ManaCostBeingPaid;
import java.util.*;

class LandManaFamilyTests extends AdaptiveEnchantmentTests {
    static final String[][] BONUSES = {
        {"Wild Growth", "Plains", "W G"}, {"Wolfwillow Haven", "Plains", "W G"},
        {"Overgrowth", "Plains", "W G G"}, {"Fertile Ground", "Plains", "W U"},
        {"Dawn's Reflection", "Plains", "W U B"}, {"Market Festival", "Plains", "W U B"},
        {"Blighted Burgeoning", "Plains", "W U"}, {"Buried in the Garden", "Plains", "W U"},
        {"Glittering Frost", "Plains", "W U"}, {"Verdant Haven", "Plains", "W U"},
        {"Trace of Abundance", "Plains", "W U"}, {"Utopia Sprawl", "Forest", "G U"},
        {"Shimmerwilds Growth", "Plains", "W U"}
    };
    static final String[][] GRANTS = {
        {"Abundant Growth", "U"}, {"Urban Utopia", "U"}, {"Unbridled Growth", "U"},
        {"Find the Path", "G G"}, {"A-Find the Path", "U U"}, {"Gift of Paradise", "U U"},
        {"Grafted Growth", "U U"}, {"New Horizons", "U U"}, {"Sheltered Aerie", "U U"},
        {"Weirding Wood", "U U"}, {"Nature's Embrace", "U U"}
    };

    static SpellAbility payment(String cost) {
        return payment(cost, "Divination");
    }

    static SpellAbility payment(String cost, String hostName) {
        SpellAbility sa = AbilityFactory.getAbility("SP$ Draw | Cost$ " + cost + " | Defined$ You | NumCards$ 1",
                add(hostName, ai, ZoneType.Hand));
        sa.setActivatingPlayer(ai);
        return sa;
    }

    static void refresh() {
        game.getAction().checkStaticAbilities();
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
    }

    static Card attach(String name, Card land) {
        Card aura = add(name, ai, ZoneType.Battlefield);
        if (name.equals("Utopia Sprawl") || name.equals("Shimmerwilds Growth")) {
            aura.setChosenColors(List.of("Blue"));
        }
        aura.attachToEntity(land, null);
        refresh();
        check(aura.getAttachedTo() == land, name + ": native attachment succeeds");
        return aura;
    }

    static boolean canPay(String cost, SpellAbility sa) {
        return ComputerUtilMana.canPayManaCost(new ManaCostBeingPaid(new ManaCost(cost)), sa, ai, false);
    }

    static boolean pay(String cost, SpellAbility sa) {
        return ComputerUtilMana.payManaCost(new ManaCostBeingPaid(new ManaCost(cost)), sa, ai, false);
    }

    static void untap(Card land) {
        SpellAbility sa = spell("Early Harvest");
        sa.getTargets().add(ai);
        AbilityUtils.resolve(sa);
        check(!land.isTapped(), "native Untap effect untaps enchanted land");
    }

    static void bonusCards() {
        for (String[] row : BONUSES) {
            AiOverrideProfile.setActive("");
            newGame();
            add("Plains", ai, ZoneType.Battlefield);
            add("Forest", ai, ZoneType.Battlefield);
            add("Island", game.getPlayers().get(1), ZoneType.Battlefield);
            SpellAbility casting = spell(row[0]);
            check(willing(casting) && casting.getTargetCard().getController() == ai,
                    row[0] + ": upstream targeting already chooses own legal land");
            newGame();
            Card land = add(row[1], ai, ZoneType.Battlefield);
            Card aura = attach(row[0], land);
            check(ManaAuraAi.supportsBonusAura(aura), row[0] + ": shared mechanical matcher accepts script");
            SpellAbility sa = payment(row[2]);
            check(!canPay(row[2], sa), row[0] + ": upstream lacks bonus-color source mapping");
            check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(aura), row[0] + ": default profile unchanged");
            AiOverrideProfile.setActive("abzan-armor");
            check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(aura) && !canPay(row[2], sa),
                    row[0] + ": Abzan profile does not acquire family behavior");
            AiOverrideProfile.setActive("adaptive-enchantment");
            check(AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(aura), row[0] + ": family opt-in admits card");
            SpellAbility mana = land.getManaAbilities().get(0);
            mana.setActivatingPlayer(ai);
            long rng = forge.util.MyRandom.callCount();
            ManaAuraAi.bonusColors(mana, ai);
            check(forge.util.MyRandom.callCount() == rng, row[0] + ": family recognition consumes no RNG");
            check(canPay(row[2], sa), row[0] + ": predictor recognizes full bonus");
            check(!canPay(row[2] + " 1", sa), row[0] + ": predictor rejects one extra mana");
            check(pay(row[2], sa) && land.isTapped(), row[0] + ": native payment spends real bonus");
            check(!canPay(row[2], sa), row[0] + ": tapped source cannot be reused");
            untap(land);
            check(canPay(row[2], sa) && pay(row[2], sa), row[0] + ": payment works again after native untap");
        }
    }

    static void grantCards() {
        for (String[] row : GRANTS) {
            for (String profile : List.of("", "adaptive-enchantment")) {
                AiOverrideProfile.setActive(profile);
                newGame();
                Card land = add("Plains", ai, ZoneType.Battlefield);
                Card aura = attach(row[0], land);
                check(!ManaAuraAi.supportsBonusAura(aura), row[0] + ": grant needs no bonus override");
                SpellAbility sa = payment(row[1]);
                check(canPay(row[1], sa) && pay(row[1], sa), row[0] + ": native grant payment, profile=" + profile);
                if (row[1].equals("U U")) {
                    untap(land);
                    check(!canPay("U B", sa), row[0] + ": any ONE color cannot be split, profile=" + profile);
                }
            }
        }
    }

    static void stack(String landName, String first, String second, String cost) {
        newGame();
        Card land = add(landName, ai, ZoneType.Battlefield);
        attach(first, land);
        attach(second, land);
        SpellAbility sa = payment(cost);
        check(canPay(cost, sa) && pay(cost, sa), first + " + " + second + ": predicted and actual payment");
        check(!canPay(cost, sa), first + " + " + second + ": one tap is consumed");
        untap(land);
        check(canPay(cost, sa) && pay(cost, sa), first + " + " + second + ": repeat after untap");
    }

    static void chosenColorEntry() {
        for (String name : List.of("Utopia Sprawl", "Shimmerwilds Growth")) {
            newGame();
            Card land = add("Forest", ai, ZoneType.Battlefield);
            for (int i = 0; i < 6; i++) { add("Counterspell", ai, ZoneType.Hand); }
            SpellAbility sa = spell(name);
            sa.getTargets().add(land);
            Card aura = sa.getHostCard();
            // Native Aura resolution attaches, enters, and runs the as-enters replacement.
            refresh();
            AbilityUtils.resolve(sa);
            aura = game.getCardState(aura, null);
            refresh();
            check(aura.getAttachedTo() == land, name + ": native resolution attaches Aura to Forest");
            check(aura.hasChosenColor() && "blue".equalsIgnoreCase(aura.getChosenColor()), name + ": native as-enters choice uses visible deck colors");
            SpellAbility cost = payment("G U");
            check(canPay("G U", cost) && pay("G U", cost), name + ": chosen blue is predicted and actually spent");
            untap(land);
            check(!canPay("G R", cost), name + ": chosen blue is not treated as any color");
        }
    }

    static void legalityAndDeferrals() {
        AiOverrideProfile.setActive("adaptive-enchantment");
        newGame();
        Card plains = add("Plains", ai, ZoneType.Battlefield);
        SpellAbility sprawl = spell("Utopia Sprawl");
        check(!willing(sprawl), "Forest-only enchant cannot target Plains");
        newGame();
        Card forest = add("Forest", ai, ZoneType.Battlefield);
        attach("Trace of Abundance", forest);
        SpellAbility growth = spell("Wild Growth");
        check(!willing(growth), "native shroud prevents second Aura targeting the land");
        newGame();
        plains = add("Plains", ai, ZoneType.Battlefield);
        Card aura = attach("Overgrowth", plains);
        game.getAction().moveToGraveyard(aura, null);
        refresh();
        SpellAbility cost = payment("W G G");
        check(!canPay("W G G", cost), "removed Aura contributes no stale bonus");
        for (String name : List.of("Elvish Guidance", "Runaway Growth", "Bigger on the Inside", "Discreet Retreat",
                "Lithoform Blight", "Song of the Dryads", "Elemental Resonance")) {
            newGame();
            aura = add(name, ai, ZoneType.Hand);
            check(!ManaAuraAi.supportsBonusAura(aura) && !AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(aura),
                    name + ": outside shared bonus family");
        }
        for (String global : List.of("Mana Flare", "Mana Reflection")) {
            newGame();
            plains = add("Plains", ai, ZoneType.Battlefield);
            attach("Wild Growth", plains);
            add(global, ai, ZoneType.Battlefield);
            refresh();
            SpellAbility mana = plains.getManaAbilities().get(0);
            mana.setActivatingPlayer(ai);
            check(!ManaAuraAi.bonusCanPay(mana, ai, ManaCostShard.GREEN),
                    global + ": mixed multiplier/replacement defers to native path");
        }
        newGame();
        plains = add("Plains", ai, ZoneType.Battlefield);
        aura = attach("Overgrowth", plains);
        aura.getTriggers().get(0).getMapParams().put("ConditionDefined", "You");
        check(!ManaAuraAi.supportsBonusAura(aura), "unrecognized trigger condition fails closed");

        for (String profile : List.of("", "adaptive-enchantment")) {
            AiOverrideProfile.setActive(profile);
            newGame();
            plains = add("Plains", ai, ZoneType.Battlefield);
            attach("Discreet Retreat", plains);
            SpellAbility illegal = payment("U U");
            check(!canPay("U U", illegal), "restricted grant rejects non-Outlaw spell, profile=" + profile);
            SpellAbility legal = payment("U U", "Spectral Sailor");
            check(canPay("U U", legal) && pay("U U", legal),
                    "restricted grant natively pays legal Outlaw spell, profile=" + profile);
        }
    }

    public static void main(String[] args) {
        forge.gui.GuiBase.setInterface(new forge.GuiDesktop());
        forge.model.FModel.initialize(null, null);
        forge.util.MyRandom.setRandom(new Random(7331));
        bonusCards();
        grantCards();
        AiOverrideProfile.setActive("adaptive-enchantment");
        stack("Forest", "Overgrowth", "Utopia Sprawl", "G G G U");
        stack("Plains", "Glittering Frost", "Verdant Haven", "W U B");
        stack("Plains", "Verdant Haven", "Glittering Frost", "W U B");
        stack("Plains", "Market Festival", "Overgrowth", "W U B G G");
        stack("Plains", "Overgrowth", "Market Festival", "W U B G G");
        stack("Plains", "Gift of Paradise", "Wild Growth", "U U G");
        newGame();
        Card land = add("Plains", ai, ZoneType.Battlefield);
        attach("Gift of Paradise", land);
        attach("Sheltered Aerie", land);
        SpellAbility sa = payment("U U U U");
        check(!canPay("U U U U", sa), "two granted tap abilities are alternatives, not additive mana");
        chosenColorEntry();
        legalityAndDeferrals();
        AiOverrideProfile.setActive("");
        System.out.println("DONE " + passed + " family checks");
    }
}
