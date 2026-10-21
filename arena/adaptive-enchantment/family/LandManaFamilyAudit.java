import forge.ai.*;
import forge.game.card.*;
import forge.game.zone.ZoneType;
import forge.game.spellability.SpellAbility;
import forge.game.ability.AbilityFactory;
import forge.game.mana.ManaCostBeingPaid;
import forge.card.mana.ManaCost;
import java.nio.file.*;
import java.util.*;

class LandManaFamilyAudit extends AdaptiveEnchantmentTests {
    public static void main(String[] args) {
        forge.gui.GuiBase.setInterface(new forge.GuiDesktop());
        forge.model.FModel.initialize(null, null);
        forge.util.MyRandom.setRandom(new Random(7330));
        List<String> csv = new ArrayList<>();
        csv.add("card,profile,targetWilling,targetOwnLand,cost,canPay,error");
        String[][] rows = {
            new String[]{"Abundant Growth","Plains","U"},
            new String[]{"Bigger on the Inside","Plains","U U"},
            new String[]{"Blighted Burgeoning","Plains","W G"},
            new String[]{"Buried in the Garden","Plains","W G"},
            new String[]{"Contaminated Ground","Plains","U"},
            new String[]{"Convincing Mirage","Plains","U"},
            new String[]{"Dawn's Reflection","Plains","W U B"},
            new String[]{"Discreet Retreat","Plains","U U"},
            new String[]{"Elvish Guidance","Plains","W G G"},
            new String[]{"Evil Presence","Plains","U"},
            new String[]{"Fertile Ground","Plains","W G"},
            new String[]{"Find the Path","Plains","G G"},
            new String[]{"Gift of Paradise","Plains","U U"},
            new String[]{"Glittering Frost","Plains","W G"},
            new String[]{"Grafted Growth","Plains","U U"},
            new String[]{"Imprisoned in the Moon","Plains","U"},
            new String[]{"Lingering Mirage","Plains","U"},
            new String[]{"Lithoform Blight","Plains","U"},
            new String[]{"Lush Growth","Plains","U"},
            new String[]{"Market Festival","Plains","W U B"},
            new String[]{"Nature's Embrace","Plains","U U"},
            new String[]{"New Horizons","Plains","U U"},
            new String[]{"Nylea's Presence","Plains","U"},
            new String[]{"Overgrowth","Plains","W G G"},
            new String[]{"Phantasmal Terrain","Plains","U"},
            new String[]{"Runaway Growth","Plains","W G"},
            new String[]{"A-Find the Path","Plains","U U"},
            new String[]{"Sea's Claim","Plains","U"},
            new String[]{"Sheltered Aerie","Plains","U U"},
            new String[]{"Shimmerwilds Growth","Plains","W U"},
            new String[]{"Spreading Seas","Plains","U"},
            new String[]{"Tainted Well","Plains","U"},
            new String[]{"Trace of Abundance","Plains","W G"},
            new String[]{"Unbridled Growth","Plains","U"},
            new String[]{"Urban Utopia","Plains","U"},
            new String[]{"Utopia Sprawl","Forest","G U"},
            new String[]{"Verdant Haven","Plains","W G"},
            new String[]{"Weirding Wood","Plains","U U"},
            new String[]{"Wild Growth","Plains","W G"},
            new String[]{"Wolfwillow Haven","Plains","W G"},
            new String[]{"Song of the Dryads","Plains","U"},
            new String[]{"Elemental Resonance","Plains","U"}
        };
        for (String profile : List.of("", "adaptive-enchantment")) {
            AiOverrideProfile.setActive(profile);
            for (String[] row : rows) {
                try {
                    newGame();
                    Card nonbasic = add("Command Tower", ai, ZoneType.Battlefield);
                    add("Plains", ai, ZoneType.Battlefield);
                    add("Forest", ai, ZoneType.Battlefield);
                    add("Island", game.getPlayers().get(1), ZoneType.Battlefield);
                    SpellAbility auraSpell = spell(row[0]);
                    boolean target = willing(auraSpell);
                    Card chosen = auraSpell.getTargetCard();
                    boolean own = chosen != null && chosen.isLand() && chosen.getController() == ai;
                    newGame();
                    Card land = add(row[1], ai, ZoneType.Battlefield);
                    Card aura = add(row[0], ai, ZoneType.Battlefield);
                    // Diagnostic payment separates prechosen-color production from the ETB choice.
                    aura.setChosenColors(List.of("Blue"));
                    aura.attachToEntity(land, null);
                    if (row[0].equals("Elvish Guidance")) {
                        add("Llanowar Elves", ai, ZoneType.Battlefield);
                        add("Llanowar Elves", ai, ZoneType.Battlefield);
                    }
                    game.getAction().checkStaticAbilities();
                    game.getTriggerHandler().resetActiveTriggers();
                    game.copyLastState();
                    SpellAbility pay = AbilityFactory.getAbility("SP$ Draw | Cost$ " + row[2] + " | NumCards$ 1 | Defined$ You",
                            add("Divination", ai, ZoneType.Hand));
                    pay.setActivatingPlayer(ai);
                    boolean payable = ComputerUtilMana.canPayManaCost(new ManaCostBeingPaid(new ManaCost(row[2])), pay, ai, false);
                    csv.add(row[0]+","+profile+","+target+","+own+","+row[2]+","+payable+",");
                } catch (Exception ex) {
                    csv.add(row[0]+","+profile+",,,,,"+ex.getClass().getSimpleName());
                }
            }
        }
        try { Files.write(Path.of(args[0]), csv); } catch (java.io.IOException ex) { throw new RuntimeException(ex); }
        System.out.println("DONE " + (csv.size()-1) + " audit rows");
    }
}
