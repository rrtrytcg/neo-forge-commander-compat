import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.game.card.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.*;

/** Native prediction/payment on original Lens scripts; deliberately not an alternative payer. */
public class PrismaticLensTests extends FilterLandsTests {
    static Card lens;
    static void setup(String profile,String rock,String... lands){
        AiOverrideProfile.setActive(profile);newGame();
        game.getPhaseHandler().devModeSet(forge.game.phase.PhaseType.MAIN2,ai);
        lens=add(rock,ai,ZoneType.Battlefield);lens.setSickness(false);
        for(String land:lands){add(land,ai,ZoneType.Battlefield);}
        for(int i=0;i<20;i++){add("Wastes",ai,ZoneType.Library);}
        game.getAction().checkStaticAbilities();game.copyLastState();
    }
    static void pay(String name,String color,int lands,boolean filtered){
        SpellAbility sa=spellInHand(name);
        check(canPay(sa),name+": native prediction");
        check(!lens.isTapped() && ai.getLandsInPlay().stream().noneMatch(Card::isTapped),name+": prediction preserves resources");
        check(ComputerUtil.handlePlayingSpellAbility(ai,sa,null),name+": native cast/payment");
        check(lens.isTapped(),name+": Lens tapped exactly once");
        check(ai.getLandsInPlay().stream().filter(Card::isTapped).count()==lands,name+": exact supporting lands");
        check(sa.getPayingMana().stream().anyMatch(m->m.toString().equals(color)),name+": actual required mana "+color);
        check(sa.getPayingMana().stream().anyMatch(m->m.getSourceCard().getId()==lens.getId() && m.getManaAbility().isAnyMana())==filtered,name+": correct native Lens mode");
        check(ai.getManaPool().totalMana()==0,name+": no invented leftover mana");
        game.getStack().resolveStack();game.getAction().checkStateEffects(true);
        check(game.getCardState(sa.getHostCard(),null).isInZone(ZoneType.Battlefield),name+": original permanent resolves");
    }
    public static void main(String[] args){
        GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);forge.util.MyRandom.setRandom(new Random(105001));
        for(String profile:new String[]{"","abzan-armor","call-the-coppercoats"}){
            setup(profile,"Prismatic Lens","Wastes");
            check(ComputerUtilMana.getAIPlayableMana(lens).size()==1,profile+": original colorless mode only");
            check(!canPay(spellInHand("Savannah Lions")),profile+": no colored filtering");
            check(ComputerUtilCard.isCardRemAIDeck(lens),profile+": original script warning retained");
            check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(lens),profile+": original admission retained");
        }
        setup("prismatic-lens","Prismatic Lens","Wastes");
        check(ComputerUtilMana.getAIPlayableMana(lens).size()==2,"opt-in exposes both modes");
        check(AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(lens),"profile admits Lens");
        check(ComputerUtilCard.isCardRemAIDeck(lens),"original warning remains in opt-in");
        pay("Savannah Lions","W",1,true);
        // Each missing color is actually generated and paid through the native engine.
        String[] names={"Storm Crow","Walking Corpse","Goblin Piker","Grizzly Bears"};
        String[] colors={"U","B","R","G"};
        for(int i=0;i<names.length;i++){setup("prismatic-lens","Prismatic Lens","Wastes","Wastes");pay(names[i],colors[i],2,true);}
        setup("prismatic-lens","Prismatic Lens","Wastes");pay("Mind Stone","C",1,false);
        setup("prismatic-lens","Prismatic Lens","Wastes","Wastes");pay("Matter Reshaper","C",2,false);
        setup("prismatic-lens","Prismatic Lens");check(!canPay(spellInHand("Savannah Lions")),"cannot pay filter with Lens itself");
        setup("prismatic-lens","Prismatic Lens","Wastes");check(!canPay(spellInHand("Storm Crow")),"conversion does not add net mana");
        setup("prismatic-lens","Prismatic Lens","Wastes","Wastes");check(!canPay(spellInHand("Kalonian Tusker")),"cannot use filter twice for GG");
        setup("prismatic-lens","Prismatic Lens","Forest","Wastes");pay("Kalonian Tusker","G",2,true);
        setup("prismatic-lens","Prismatic Lens","Forest");check(!canPay(spellInHand("Kalonian Tusker")),"Forest cannot fund filter and still pay second green");
        setup("prismatic-lens","Prismatic Lens","Wastes","Wastes");lens.setTapped(true);check(!canPay(spellInHand("Grizzly Bears")),"tapped Lens unavailable");
        lens.setTapped(false);pay("Grizzly Bears","G",2,true);
        lens.setTapped(false);for(Card c:ai.getLandsInPlay()){c.setTapped(false);}for(AiCardMemory.MemorySet set:AiCardMemory.MemorySet.values()){AiCardMemory.clearMemorySet(ai,set);}
        pay("Storm Crow","U",2,true);
        setup("prismatic-lens","Prophetic Prism","Wastes","Wastes");
        check(ComputerUtilMana.getAIPlayableMana(lens).isEmpty(),"Prism remains outside profile boundary");
        check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(lens),"Prism not unlocked by mechanic work");
        setup("prismatic-lens","Prismatic Lens","Plains","Wastes"); SpellAbility direct=spellInHand("Savannah Lions"); check(ComputerUtil.handlePlayingSpellAbility(ai,direct,null),"native direct white payment");check(!lens.isTapped(),"free matching color preserves Lens");
        setup("prismatic-lens","Prismatic Lens","Wastes");Card second=add("Prismatic Lens",ai,ZoneType.Battlefield);check(!canPay(spellInHand("Kalonian Tusker")),"two filters cannot invent a second funding mana");
        add("Wastes",ai,ZoneType.Battlefield);pay("Kalonian Tusker","G",2,true);check(second.isTapped(),"two separately funded Lens filters pay GG");
        setup("prismatic-lens","Prismatic Lens","Wastes","Wastes");Card silence=add("Stony Silence",game.getPlayers().get(1),ZoneType.Battlefield);game.getAction().checkStaticAbilities();
        check(!canPay(spellInHand("Grizzly Bears")),"native artifact activation prohibition respected");
        game.getAction().moveToGraveyard(silence,null);game.getAction().checkStaticAbilities();pay("Grizzly Bears","G",2,true);
        setup("prismatic-lens","Prismatic Lens","Wastes","Wastes","Wastes");add("Thalia, Guardian of Thraben",game.getPlayers().get(1),ZoneType.Battlefield);game.getAction().checkStaticAbilities();
        SpellAbility draw=spellInHand("Divination");check(!ComputerUtilCost.canPayCost(draw,ai,false),"native spell tax prevents underfunded filtered cast");
        add("Wastes",ai,ZoneType.Battlefield);check(ComputerUtilCost.canPayCost(draw,ai,false),"native tax forecast accepts fourth funding land");
        check(ComputerUtil.handlePlayingSpellAbility(ai,draw,null),"native taxed colored spell payment");check(draw.getPayingMana().size()==4 && draw.getPayingMana().stream().anyMatch(m->m.toString().equals("U")),"actual tax plus blue paid");
        int library=ai.getCardsIn(ZoneType.Library).size();game.getStack().resolveStack();check(ai.getCardsIn(ZoneType.Library).size()==library-2,"original taxed draw resolves");
        setup("prismatic-lens","Prismatic Lens");SpellAbility filter=filterAbility(lens);filter.setActivatingPlayer(ai);
        ai.getManaPool().addMana(new forge.game.mana.Mana((byte)forge.card.mana.ManaAtom.COLORLESS,lens,lens.getManaAbilities().get(0).getManaPart(),ai));
        check(canPay(spellInHand("Savannah Lions")),"floating mana may fund filter without land");
        check(ComputerUtil.handlePlayingSpellAbility(ai,ai.getCardsIn(ZoneType.Hand).getFirst().getFirstSpellAbility(),null),"native floating-mana funding");
        check(lens.isTapped() && ai.getManaPool().totalMana()==0,"floating funding consumed without reuse");
        setup("prismatic-lens","Prismatic Lens");
        for(String shape:new String[]{"Cost$ 2 T | Produced$ Any", "Cost$ 1 T | Produced$ Any | Amount$ 2", "Cost$ 1 T Sac<1/CARDNAME> | Produced$ Any", "Cost$ 1 | Produced$ Any", "Cost$ 1 T | Produced$ Chosen", "Cost$ 1 T | Produced$ Combo W U"}){
            SpellAbility odd=forge.game.ability.AbilityFactory.getAbility("AB$ Mana | "+shape,lens);lens.addSpellAbility(odd);
            check(!ComputerUtilMana.getAIPlayableMana(lens).contains(odd),"unrelated shape excluded: "+shape);
        }
        // Test-only profile lives solely in the isolated candidate runtime, never in installed assets.
        java.io.File fixture=AiOverrideProfile.profileFile("prismatic-filter-fixture");
        check(!fixture.exists(),"test profile cannot overwrite existing asset");
        try{
            java.nio.file.Files.writeString(fixture.toPath(),"oneForOneManaFilters=Prophetic Prism\n");
            setup("prismatic-filter-fixture","Prophetic Prism","Wastes","Wastes");
            check(ComputerUtilMana.getAIPlayableMana(lens).size()==1,"same structural mechanic recognizes Prism when explicitly opted in");
            check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(lens),"capability does not implicitly unlock Prism");
            pay("Grizzly Bears","G",2,true);
        }catch(java.io.IOException e){throw new RuntimeException(e);}finally{fixture.delete();}
        AiOverrideProfile.setActive("");System.out.println("DONE "+passed+" checks");
    }
}




