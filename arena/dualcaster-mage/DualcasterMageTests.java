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
import forge.game.trigger.Trigger;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Opt-in opponent-only spell copy fixtures for Dualcaster Mage. */
public class DualcasterMageTests {
    static Game game; static Player ai; static Card mage; static SpellAbility copy; static int passed;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);passed++;System.out.println("PASS "+label);}
    static Player p(int i){return game.getPlayers().get(i);}
    static Card add(String name,Player owner,ZoneType zone){Card c=Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name),owner);c.setGameTimestamp(game.getNextTimestamp());owner.getZone(zone).add(c);c.setSickness(false);return c;}
    static void setup(String profile){
        AiOverrideProfile.setActive(profile);List<RegisteredPlayer> regs=new ArrayList<>();for(int i=0;i<2;i++){LobbyPlayerAi lobby=new LobbyPlayerAi("Dualcaster fixture "+i,null);lobby.setAiProfile("Default");regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));}
        GameRules rules=new GameRules(GameType.Constructed);game=new Game(regs,rules,new Match(rules,regs,"Dualcaster Mage fixtures"));game.setAge(GameStage.Play);ai=p(0);game.getPhaseHandler().devModeSet(PhaseType.MAIN1,p(1));
        mage=add("Dualcaster Mage",ai,ZoneType.Battlefield);Trigger trigger=mage.getTriggers().stream().findFirst().orElseThrow();copy=trigger.ensureAbility();copy.setActivatingPlayer(ai);
        for(int i=0;i<3;i++)add("Mountain",ai,ZoneType.Battlefield);
        for(Player player:game.getPlayers())for(int i=0;i<12;i++)add("Island",player,ZoneType.Library);
        game.getAction().checkStaticAbilities();game.getTriggerHandler().resetActiveTriggers();game.copyLastState();
    }
    static SpellAbility push(String name,int seat){Card c=add(name,p(seat),ZoneType.Hand);SpellAbility sa=c.getFirstSpellAbility();sa.setActivatingPlayer(p(seat));sa.setHostCard(game.getAction().moveToStack(c,sa));sa.getHostCard().setCastSA(sa);game.getStack().add(sa);return sa;}
    static boolean willing(){return SpellApiToAi.Converter.get(copy).canPlayWithSubs(ai,copy).willingToPlay();}
    static SpellAbility pick(List<SpellAbility> candidates){return SpellApiToAi.Converter.get(copy).chooseSingleSpellAbility(ai,copy,candidates,Map.of());}
    static void profileAndTargetRule(){
        setup("dualcaster-mage");check(AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(mage),"opt-in bypasses only Dualcaster Mage RemoveDeck");
        check("DualcasterMage".equals(AiOverrideProfile.get().getAiLogic(mage)),"opt-in selects the dedicated Dualcaster policy");
        SpellAbility own=push("Opt",0), opposing=push("Opt",1);
        check(willing(),"copy trigger is willing with an opponent instant on the stack");
        check(pick(List.of(own,opposing))==opposing,"target chooser skips own spells and selects an opponent spell");
        check(pick(List.of(own))==null,"target chooser holds when only own spells are available");
    }
    static void holdsAndIsolation(){
        setup("dualcaster-mage");SpellAbility own=push("Opt",0);
        check(!willing(),"copy trigger holds when the stack has no opponent spell");
        check(pick(List.of(own))==null,"own-only candidates do not get copied");
        for(String profile:List.of("","drawn-from-dreams")){
            setup(profile);check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(mage),"profile '"+profile+"' does not bypass Dualcaster Mage");
            List<SpellAbility> choices=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();
            check(choices==null||choices.stream().noneMatch(sa->sa.getHostCard()==mage),"profile '"+profile+"' keeps Dualcaster Mage out of normal selection");
        }
    }
    static void normalSelection(){
        setup("dualcaster-mage");Card inHand=add("Dualcaster Mage",ai,ZoneType.Hand);SpellAbility cast=inHand.getFirstSpellAbility();cast.setActivatingPlayer(ai);push("Opt",1);
        List<SpellAbility> choices=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();
        check(choices!=null&&choices.stream().anyMatch(sa->sa.getHostCard()==inHand),"normal AI selection can choose Dualcaster Mage under its opt-in when an opponent spell is pending");
    }
    static void actualEtbCopy(){
        setup("dualcaster-mage");Card inHand=add("Dualcaster Mage",ai,ZoneType.Hand);SpellAbility cast=inHand.getFirstSpellAbility();cast.setActivatingPlayer(ai);SpellAbility original=push("Opt",1);
        check(ComputerUtil.playStack(cast,ai,game),"native payment casts Dualcaster Mage with an opponent Opt pending");
        game.getStack().resolveStack();game.getTriggerHandler().runWaitingTriggers();game.getStack().addAllTriggeredAbilitiesToStack();
        SpellAbility trigger=game.getStack().peekAbility();
        check(trigger!=null&&trigger.getApi()==ApiType.CopySpellAbility,"Dualcaster Mage's native ETB trigger is placed on the stack");
        check(trigger!=null&&trigger.getTargets().getTargetSpells().iterator().hasNext()&&trigger.getTargets().getTargetSpells().iterator().next()==original,"ETB trigger targets the opponent's legal Opt");
        game.getStack().resolveStack();
        check(game.getStack().peekAbility()!=original,"resolving the trigger puts a copied Opt above the original");
        game.getStack().resolveStack();game.getStack().resolveStack();game.getAction().checkStaticAbilities();game.copyLastState();
        check(ai.getCardsIn(ZoneType.Hand).size()==1&&p(1).getCardsIn(ZoneType.Hand).size()==1,"copied and original Opt resolve for their respective controllers");
    }
    static void nativeCopyResolution(){
        setup("dualcaster-mage");SpellAbility original=push("Opt",1);copy.getTargets().add(original);copy.setActivatingPlayer(ai);game.getStack().add(copy);
        for(int i=0;i<3&&!game.getStack().isEmpty();i++)game.getStack().resolveStack();game.getAction().checkStaticAbilities();game.copyLastState();
        check(ai.getCardsIn(ZoneType.Hand).size()==1,"native copied Opt resolves under Dualcaster controller (hand="+ai.getCardsIn(ZoneType.Hand).size()+", stack="+game.getStack().size()+", grave="+ai.getCardsIn(ZoneType.Graveyard).size()+")");
        check(p(1).getCardsIn(ZoneType.Hand).size()==1,"original Opt resolves under its caster after the copy");
        check(ai.getCardsIn(ZoneType.Library).size()==11&&p(1).getCardsIn(ZoneType.Library).size()==11,"both native spell resolutions perform their original scry and draw");
    }
    static void baseline(){setup("");push("Opt",1);check(!willing(),"native baseline declines optional copy trigger without profile logic");}
    public static void main(String[] args){GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(20261021));if(args.length>0&&"baseline".equals(args[0]))baseline();else{profileAndTargetRule();holdsAndIsolation();normalSelection();actualEtbCopy();nativeCopyResolution();}AiOverrideProfile.setActive("");System.out.println("DONE "+passed+" Dualcaster Mage checks");}
}
