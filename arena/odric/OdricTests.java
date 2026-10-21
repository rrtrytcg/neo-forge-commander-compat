import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.*;
import forge.game.combat.*;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Opt-in admission plus native Odric attack-trigger and blocker-control fixtures. */
public class OdricTests {
    static Game game; static Player ai; static Card odric; static int passed;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);passed++;System.out.println("PASS "+label);}
    static Player p(int i){return game.getPlayers().get(i);}
    static Card add(String name,Player owner,ZoneType zone){Card c=Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name),owner);c.setGameTimestamp(game.getNextTimestamp());owner.getZone(zone).add(c);c.setSickness(false);return c;}
    static void refresh(){game.getAction().checkStaticAbilities();game.getTriggerHandler().resetActiveTriggers();game.copyLastState();}
    static void setup(String profile){
        AiOverrideProfile.setActive(profile);List<RegisteredPlayer> regs=new ArrayList<>();for(int i=0;i<2;i++){LobbyPlayerAi lobby=new LobbyPlayerAi("Odric fixture "+i,null);lobby.setAiProfile("Default");regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));}
        GameRules rules=new GameRules(GameType.Constructed);game=new Game(regs,rules,new Match(rules,regs,"Odric native fixtures"));game.setAge(GameStage.Play);ai=p(0);game.getPhaseHandler().devModeSet(PhaseType.MAIN1,ai);
        odric=add("Odric, Master Tactician",ai,ZoneType.Battlefield);for(Player player:game.getPlayers())for(int i=0;i<12;i++)add("Plains",player,ZoneType.Library);refresh();
    }
    static List<SpellAbility> choices(){return ((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();}
    static boolean contains(List<SpellAbility> list,Card host){return list!=null&&list.stream().anyMatch(sa->sa.getHostCard()==host);}
    static void candidateIsolation(){
        setup("");Card hand=add("Odric, Master Tactician",ai,ZoneType.Hand);check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(hand),"default profile leaves Odric RemoveDeck intact");List<SpellAbility> c=choices();check(!contains(c,hand),"default profile excludes Odric from stock spell selection");
        for(String profile:List.of("drawn-from-dreams","easy-cards-3")){setup(profile);hand=add("Odric, Master Tactician",ai,ZoneType.Hand);check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(hand),"profile '"+profile+"' does not admit Odric");}
    }
    static Combat attack(int otherAttackers){
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS,ai);Combat combat=new Combat(p(1));game.getPhaseHandler().setCombat(combat);combat.addAttacker(odric,p(1));
        for(int i=0;i<otherAttackers;i++){Card c=add("Grizzly Bears",ai,ZoneType.Battlefield);combat.addAttacker(c,p(1));}
        refresh();for(Card attacker:new ArrayList<>(combat.getAttackers()))CombatUtil.checkDeclaredAttacker(game,attacker,combat,true);
        game.getTriggerHandler().runWaitingTriggers();game.getStack().addAllTriggeredAbilitiesToStack();return combat;
    }
    static void profileAdmission(){
        setup("odric");Card hand=add("Odric, Master Tactician",ai,ZoneType.Hand);for(int i=0;i<4;i++)add("Plains",ai,ZoneType.Battlefield);check(AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(hand),"opt-in profile admits only the named Odric card");
        check(AiOverrideProfile.get().getAiLogic(hand).isEmpty(),"Odric opt-in adds no card-specific AI logic");
    }
    static void triggerThresholdAndControl(){
        setup("odric");Combat combat=attack(3);check(!game.getStack().isEmpty(),"Odric plus three other declared attackers creates the native trigger");game.getStack().resolveStack();refresh();
        check(p(1).getDeclaresBlockers()==ai,"native effect assigns blocker decisions to Odric's controller");
        setup("odric");attack(2);check(game.getStack().isEmpty(),"Odric with only two other attackers does not trigger");check(p(1).getDeclaresBlockers()==null,"below-threshold combat leaves blocker control unchanged");
    }
    static void baseline(){setup("");Card hand=add("Odric, Master Tactician",ai,ZoneType.Hand);List<SpellAbility> c=choices();check(!contains(c,hand),"native baseline excludes Odric from normal play");}
    public static void main(String[] args){GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(20261021));if(args.length>0&&"baseline".equals(args[0]))baseline();else{candidateIsolation();profileAdmission();triggerThresholdAndControl();}AiOverrideProfile.setActive("");System.out.println("DONE "+passed+" Odric checks");}
}
