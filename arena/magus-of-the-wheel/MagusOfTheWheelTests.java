import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.ability.ApiType;
import forge.game.card.*;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Native Magus of the Wheel hand, timing, profile and wheel-resolution fixtures. */
public class MagusOfTheWheelTests {
    static Game game; static Player ai; static Card magus; static SpellAbility wheel; static int passed;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);passed++;System.out.println("PASS "+label);}
    static Player p(int i){return game.getPlayers().get(i);}
    static Card add(String name,Player owner,ZoneType zone){Card c=Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name),owner);c.setGameTimestamp(game.getNextTimestamp());owner.getZone(zone).add(c);c.setSickness(false);return c;}
    static void refresh(){game.getAction().checkStaticAbilities();game.getTriggerHandler().resetActiveTriggers();game.copyLastState();}
    static void setup(String profile,int ownHand,int firstOppHand,int secondOppHand,int ownLibrary,int mountains){
        AiOverrideProfile.setActive(profile);
        List<RegisteredPlayer> regs=new ArrayList<>();for(int i=0;i<3;i++){LobbyPlayerAi lobby=new LobbyPlayerAi("Wheel fixture "+i,null);lobby.setAiProfile("Default");regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));}
        GameRules rules=new GameRules(GameType.Constructed);game=new Game(regs,rules,new Match(rules,regs,"Magus of the Wheel native fixtures"));game.setAge(GameStage.Play);ai=p(0);game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);
        for(int i=0;i<mountains;i++)add("Mountain",ai,ZoneType.Battlefield);
        magus=add("Magus of the Wheel",ai,ZoneType.Battlefield);
        for(int i=0;i<ownHand;i++)add("Craw Wurm",ai,ZoneType.Hand);
        for(int i=0;i<firstOppHand;i++)add("Forest",p(1),ZoneType.Hand);
        for(int i=0;i<secondOppHand;i++)add("Forest",p(2),ZoneType.Hand);
        for(int i=0;i<ownLibrary;i++)add("Forest",ai,ZoneType.Library);
        for(Player player:game.getPlayers())if(player!=ai)for(int i=0;i<30;i++)add("Forest",player,ZoneType.Library);
        wheel=magus.getSpellAbilities().stream().filter(s->s.getApi()==ApiType.Discard).findFirst().orElseThrow();wheel.setActivatingPlayer(ai);refresh();
    }
    static boolean willing(){return SpellApiToAi.Converter.get(wheel).canPlayWithSubs(ai,wheel).willingToPlay();}
    static SpellAbility selected(){List<SpellAbility> abilities=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();return abilities==null||abilities.isEmpty()?null:abilities.get(0);}
    static void baseline(){setup("",2,4,4,30,2);check(!willing(),"baseline native Discard AI rejects the favorable wheel because it judges Magus's creature-sacrifice cost too harshly");SpellAbility choice=selected();check(choice==null||choice.getHostCard()!=magus,"baseline deck filter excludes Magus of the Wheel from normal selection");}
    static void favoredWheel(){setup("magus-of-the-wheel",2,4,4,30,2);check("MagusOfTheWheel".equals(AiOverrideProfile.get().getAiLogic(magus)),"profile attaches wheel logic only to Magus of the Wheel");check(willing(),"low hand and full opponent hands allow the second-main wheel");check(wheel.getTargets().isEmpty(),"wheel policy leaves native player-wide effect untargeted");SpellAbility choice=selected();check(choice!=null&&choice.getHostCard()==magus,"normal native AI selection chooses Magus only under the approved conditions");check(ComputerUtil.playStack(wheel,ai,game),"native payment spends one red and one generic mana, taps and sacrifices Magus");check(magus.isInZone(ZoneType.Graveyard),"native cost puts Magus into its owner's graveyard");game.getStack().resolveStack();refresh();for(Player player:game.getPlayers()){check(player.getCardsIn(ZoneType.Hand).size()==7,"native wheel leaves each player with seven cards");check(player.getCardsIn(ZoneType.Library).size()==23,"native wheel draws exactly seven cards");}}
    static void holds(){setup("magus-of-the-wheel",3,4,4,30,2);check(!willing(),"holds when our hand exceeds two cards");setup("magus-of-the-wheel",2,3,4,30,2);check(!willing(),"holds when an opponent has fewer than four cards");setup("magus-of-the-wheel",2,4,4,6,2);check(!willing(),"holds when our library cannot provide seven cards");setup("magus-of-the-wheel",2,4,4,30,1);check(!willing(),"holds when printed mana cost cannot be paid");setup("magus-of-the-wheel",2,4,4,30,2);game.getPhaseHandler().devModeSet(PhaseType.MAIN1,ai);check(!willing(),"holds before our second main phase");setup("magus-of-the-wheel",2,4,4,30,2);SpellAbility pending=add("Opt",p(1),ZoneType.Hand).getFirstSpellAbility();pending.setActivatingPlayer(p(1));game.getStack().add(pending);refresh();check(!willing(),"holds while another ability is on the stack");}
    static void profileIsolation(){for(String profile:List.of("","phyrexias-core")){setup(profile,2,4,4,30,2);check(!"MagusOfTheWheel".equals(AiOverrideProfile.get().getAiLogic(magus)),"unrelated profile does not attach wheel logic");SpellAbility choice=selected();check(choice==null||choice.getHostCard()!=magus,"unrelated profile keeps blocked Magus out of normal selection");}}
    public static void main(String[] args){GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(20261021));if(args.length>0&&"baseline".equals(args[0]))baseline();else{favoredWheel();holds();profileIsolation();}AiOverrideProfile.setActive("");System.out.println("DONE "+passed+" Magus of the Wheel checks");}
}
