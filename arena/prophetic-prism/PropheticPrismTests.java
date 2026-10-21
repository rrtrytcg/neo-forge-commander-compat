import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.game.card.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.*;

/** Original Prism spell, entry trigger and native payments; no replacement resolver. */
public class PropheticPrismTests extends PrismaticLensTests {
    static SpellAbility spell;
    static void fresh(int library,int mana){
        String[] lands=new String[mana];Arrays.fill(lands,"Wastes");setup("prophetic-prism","Prophetic Prism",lands);
        lens=game.getAction().moveToHand(lens,null);spell=lens.getFirstSpellAbility();spell.setActivatingPlayer(ai);
        while(ai.getCardsIn(ZoneType.Library).size()>library){ai.getZone(ZoneType.Library).remove(ai.getCardsIn(ZoneType.Library).getFirst());}
        refresh();
    }
    static void refresh(){game.getAction().checkStaticAbilities();game.getTriggerHandler().resetActiveTriggers();game.copyLastState();}
    static boolean willing(){return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai,spell).willingToPlay();}
    static boolean nativeETB(){return ((forge.ai.PlayerControllerAi)ai.getController()).getAi().checkETBEffects(lens,spell,null);}
    static SpellAbility select(){List<SpellAbility> list=((forge.ai.PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();return list==null?null:list.stream().filter(a->a.getHostCard()==lens).findFirst().orElse(null);}
    static Card risk(String name,ZoneType zone){Card c=add(name,game.getPlayers().get(1),zone);refresh();return c;}
    static void cast(String label,int mana){
        SpellAbility selected=select();check(selected!=null,label+": native controller selects Prism");spell=selected;
        check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null),label+": native cast/payment");
        check(spell.getPayingMana().size()==mana,label+": adjusted native cost paid");
        int library=ai.getCardsIn(ZoneType.Library).size(),hand=ai.getCardsIn(ZoneType.Hand).size(),life=ai.getLife();
        game.getStack().resolveStack();lens=game.getCardState(lens,null);refresh();
        check(lens.isInZone(ZoneType.Battlefield),label+": original artifact resolves");
        check(ai.getCardsIn(ZoneType.Library).size()==library,label+": artifact resolution itself has not drawn");
        game.getTriggerHandler().runWaitingTriggers();game.getStack().addAllTriggeredAbilitiesToStack();
        check(!game.getStack().isEmpty(),label+": original mandatory draw trigger stacked");
        check(!CantripPermanentAi.uses(game.getStack().peekAbility()),label+": policy never vetoes the mandatory draw trigger");
        int guard=0;while(!game.getStack().isEmpty() && guard++<4){game.getStack().resolveStack();}
        check(game.getStack().isEmpty() && ai.getCardsIn(ZoneType.Library).size()==library-1 && ai.getCardsIn(ZoneType.Hand).size()==hand+1,label+": exact native entry draw");
        check(ai.getLife()==life && ai.getCardsIn(ZoneType.Library).size()>=4,label+": life and four-card reserve preserved");
    }
    public static void main(String[] args){
        GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);forge.util.MyRandom.setRandom(new Random(106001));
        fresh(5,2);check(CantripPermanentAi.uses(spell),"profile gates ordinary permanent cantrip");
        check(CantripPermanentAi.safe(ai,spell) && willing(),"five-card library permits one draw");
        check(nativeETB(),"native ETB evaluation already accepts safe draw");cast("boundary draw",2);
        for(int library=0;library<5;library++){
            fresh(library,2);check(!CantripPermanentAi.safe(ai,spell),"library reserve hold "+library);
            check(!willing() && select()==null,"native selection respects reserve "+library);
            check(!CantripPermanentAi.preflight(ai,spell),"preflight respects reserve "+library);
        }
        fresh(4,2);AiOverrideProfile.setActive("");check(!nativeETB(),"native ETB gate already rejects low library without policy");
        for(String profile:new String[]{"","prismatic-lens","call-the-coppercoats"}){
            fresh(0,2);AiOverrideProfile.setActive(profile);
            check(!CantripPermanentAi.uses(spell) && CantripPermanentAi.preflight(ai,spell),profile+": new veto inactive");
            check(ComputerUtilCard.isCardRemAIDeck(lens) && !AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(lens),profile+": original admission retained");
            check(ComputerUtilMana.getAIPlayableMana(lens).isEmpty(),profile+": Prism filtering remains excluded");
        }
        fresh(20,2);check(ComputerUtilCard.isCardRemAIDeck(lens) && AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(lens),"profile admits Prism without changing script warning");
        for(String name:new String[]{"Underworld Dreams","Fate Unraveler","Notion Thief","Alms Collector","Consecrated Sphinx","Panharmonicon","Elesh Norn, Mother of Machines","Strict Proctor","Etherium Sculptor","Rest in Peace","Grafdigger's Cage","Vedalken Archmage"}){
            fresh(20,3);Card c=risk(name,ZoneType.Battlefield);
            boolean uncertain=!Set.of("Etherium Sculptor","Grafdigger's Cage","Rest in Peace").contains(name);
            check(CantripPermanentAi.safe(ai,spell)!=uncertain,"public interaction policy "+name);
            if(uncertain){check(!willing() && !CantripPermanentAi.preflight(ai,spell),"selection and fresh veto hold "+name);}
            else{check(willing(),"native unrelated static remains allowed "+name);}
        }
        fresh(20,2);risk("Maralen of the Mornsong",ZoneType.Battlefield);check(!ai.canDraw() && !willing(),"native cannot-draw prohibition");
        fresh(20,2);Card inactive=risk("Underworld Dreams",ZoneType.Graveyard);check(willing(),"inactive graveyard draw trigger ignored");
        inactive=game.getAction().moveToPlay(inactive,null,forge.game.ability.AbilityKey.newMap());inactive.setPhasedOut(ai);refresh();check(willing(),"phased-out draw trigger ignored");
        fresh(20,2);Card r=risk("Notion Thief",ZoneType.Battlefield);r.setPhasedOut(ai);refresh();check(willing(),"phased-out draw replacement ignored");
        fresh(20,2);cast("ordinary draw",2);
        // Existing filtering works after the entry draw; it is not a new mana implementation.
        for(Card c:ai.getLandsInPlay()){c.setTapped(false);}for(AiCardMemory.MemorySet set:AiCardMemory.MemorySet.values()){AiCardMemory.clearMemorySet(ai,set);}
        pay("Grizzly Bears","G",2,true);
        lens.setTapped(false);for(Card c:ai.getLandsInPlay()){c.setTapped(false);}for(AiCardMemory.MemorySet set:AiCardMemory.MemorySet.values()){AiCardMemory.clearMemorySet(ai,set);}
        pay("Storm Crow","U",2,true);
        fresh(20,3);risk("Thalia, Guardian of Thraben",ZoneType.Battlefield);cast("native spell tax",3);
        fresh(20,1);add("Etherium Sculptor",ai,ZoneType.Battlefield);refresh();cast("native spell reduction",1);
        fresh(20,1);check(select()==null && !ComputerUtilCost.canPayCost(spell,ai,false),"insufficient native casting mana");
        for(String change:new String[]{"library","draw prohibition","damage on draw","replacement","extra trigger"}){
            fresh(5,2);SpellAbility selected=select();check(selected!=null,"safe selection before "+change);spell=selected;
            if(change.equals("library")){ai.getZone(ZoneType.Library).remove(ai.getCardsIn(ZoneType.Library).getFirst());}
            if(change.equals("draw prohibition")){risk("Maralen of the Mornsong",ZoneType.Battlefield);}
            if(change.equals("damage on draw")){risk("Underworld Dreams",ZoneType.Battlefield);}
            if(change.equals("replacement")){risk("Notion Thief",ZoneType.Battlefield);}
            if(change.equals("extra trigger")){risk("Panharmonicon",ZoneType.Battlefield);}
            refresh();check(!ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"fresh casting veto "+change);
            check(lens.isInZone(ZoneType.Hand) && game.getStack().isEmpty() && ai.getLandsInPlay().stream().noneMatch(Card::isTapped),"veto precedes stack and payment mutations "+change);
        }
        fresh(5,2);SpellAbility selected=select();ai.getZone(ZoneType.Library).remove(ai.getCardsIn(ZoneType.Library).getFirst());add("Wastes",ai,ZoneType.Library);spell=selected;check(CantripPermanentAi.preflight(ai,spell),"fresh still-safe library permits cast");
        fresh(20,2);risk("Underworld Dreams",ZoneType.Hand);risk("Notion Thief",ZoneType.Library);check(CantripPermanentAi.safe(ai,spell),"opponent hidden identities do not affect guard");
        Random expected=new Random(106009);forge.util.MyRandom.setRandom(new Random(106009));boolean stable=true;for(int i=0;i<50;i++){stable &= CantripPermanentAi.safe(ai,spell);}check(stable,"repeated read-only safety evaluation is stable");check(forge.util.MyRandom.getRandom().nextLong()==expected.nextLong(),"new policy consumes no evaluation RNG");
        String[] names={"Savannah Lions","Storm Crow","Walking Corpse","Goblin Piker","Grizzly Bears"};String[] colors={"W","U","B","R","G"};
        for(int i=0;i<names.length;i++){setup("prophetic-prism","Prophetic Prism","Wastes","Wastes");pay(names[i],colors[i],names[i].equals("Savannah Lions")?1:2,true);}
        setup("prophetic-prism","Prophetic Prism","Wastes");check(!canPay(spellInHand("Storm Crow")),"Prism conversion does not increase available mana");
        add("Prophetic Prism",ai,ZoneType.Battlefield);check(!canPay(spellInHand("Kalonian Tusker")),"two Prisms need two separate funding mana for GG");
        add("Wastes",ai,ZoneType.Battlefield);check(canPay(spellInHand("Kalonian Tusker")),"two independently funded Prisms forecast GG");
        SpellAbility gg=ai.getCardsIn(ZoneType.Hand).stream().filter(c->c.getName().equals("Kalonian Tusker")).findFirst().get().getFirstSpellAbility();gg.setActivatingPlayer(ai);
        check(ComputerUtil.handlePlayingSpellAbility(ai,gg,null) && gg.getPayingMana().stream().filter(m->m.toString().equals("G")).count()==2,"native two-Prism GG payment");
        setup("prophetic-prism","Prophetic Prism","Wastes","Wastes","Wastes");SpellAbility genuine=spellInHand("Matter Reshaper");
        check(ComputerUtil.handlePlayingSpellAbility(ai,genuine,null),"native true colorless requirement paid by Wastes");check(!lens.isTapped() && genuine.getPayingMana().stream().noneMatch(m->m.getSourceCard().getId()==lens.getId()),"Prism cannot pretend to produce true colorless mana");
        setup("prophetic-prism","Prophetic Prism","Wastes","Wastes");while(!ai.getCardsIn(ZoneType.Library).isEmpty()){ai.getZone(ZoneType.Library).remove(ai.getCardsIn(ZoneType.Library).getFirst());}risk("Maralen of the Mornsong",ZoneType.Battlefield);
        pay("Grizzly Bears","G",2,true);check(!ai.canDraw() && ai.getCardsIn(ZoneType.Library).isEmpty(),"already-in-play Prism filtering remains usable when casting safety would hold");
        fresh(20,2);Thread.currentThread().interrupt();check(!CantripPermanentAi.preflight(ai,spell),"interrupted evaluation holds");Thread.interrupted();check(CantripPermanentAi.preflight(ai,spell),"next evaluation has no global stale veto");
        AiOverrideProfile.setActive("");System.out.println("DONE "+passed+" checks");
    }
}
