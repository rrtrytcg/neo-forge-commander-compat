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

/** Native-choice, resolution, timing and profile-isolation fixtures for Daretti. */
public class DarettiTests {
    static Game game; static Player ai; static Card daretti; static SpellAbility plusTwo, minusTwo, ultimate; static int passed;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);passed++;System.out.println("PASS "+label);}
    static Player p(int i){return game.getPlayers().get(i);}
    static Card add(String name,Player owner,ZoneType zone){Card c=Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name),owner);c.setGameTimestamp(game.getNextTimestamp());owner.getZone(zone).add(c);c.setSickness(false);return c;}
    static void setup(String profile,int loyalty,int handLands,int fieldArtifacts,int graveArtifacts,PhaseType phase,boolean aiTurn){
        AiOverrideProfile.setActive(profile);List<RegisteredPlayer> regs=new ArrayList<>();for(int i=0;i<2;i++){LobbyPlayerAi lobby=new LobbyPlayerAi("Daretti fixture "+i,null);lobby.setAiProfile("Default");regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));}
        GameRules rules=new GameRules(GameType.Constructed);game=new Game(regs,rules,new Match(rules,regs,"Daretti native fixtures"));game.setAge(GameStage.Play);ai=p(0);game.getPhaseHandler().devModeSet(phase,aiTurn?ai:p(1));
        daretti=add("Daretti, Scrap Savant",ai,ZoneType.Battlefield);daretti.setCounters(CounterEnumType.LOYALTY,loyalty);
        for(int i=0;i<handLands;i++)add("Forest",ai,ZoneType.Hand);
        for(int i=0;i<fieldArtifacts;i++)add(i==0?"Sol Ring":"Mind Stone",ai,ZoneType.Battlefield);
        for(int i=0;i<graveArtifacts;i++)add(i==0?"Wurmcoil Engine":"Darksteel Relic",ai,ZoneType.Graveyard);
        for(int i=0;i<30;i++)add("Mountain",ai,ZoneType.Library);
        plusTwo=daretti.getSpellAbilities().stream().filter(s->s.getApi()==ApiType.Discard).findFirst().orElseThrow();
        minusTwo=daretti.getSpellAbilities().stream().filter(s->s.getApi()==ApiType.Sacrifice).findFirst().orElseThrow();
        ultimate=daretti.getSpellAbilities().stream().filter(s->s.getApi()==ApiType.Effect).findFirst().orElseThrow();
        plusTwo.setActivatingPlayer(ai);minusTwo.setActivatingPlayer(ai);ultimate.setActivatingPlayer(ai);
        game.getAction().checkStaticAbilities();game.getTriggerHandler().resetActiveTriggers();game.copyLastState();
    }
    static boolean willing(SpellAbility sa){return SpellApiToAi.Converter.get(sa).canPlayWithSubs(ai,sa).willingToPlay();}
    static SpellAbility selected(){List<SpellAbility> abilities=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();return abilities==null||abilities.isEmpty()?null:abilities.get(0);}
    static int count(ZoneType zone,java.util.function.Predicate<Card> predicate){return CardLists.count(ai.getCardsIn(zone),predicate);}
    static void plusTwoValueAndResolution(){
        setup("daretti",3,4,0,0,PhaseType.MAIN2,true);
        ai.setLandsPlayedThisTurn(1);
        check("DarettiScrapSavant".equals(AiOverrideProfile.get().getAiLogic(daretti)),"profile attaches Daretti logic only to Daretti, Scrap Savant");
        check(willing(plusTwo),"second-main +2 is admitted when excess lands can be discarded");
        SpellAbility choice=selected();check(choice!=null&&choice.getHostCard()==daretti&&choice.getApi()==ApiType.Discard,"normal AI selection chooses the approved +2 after the land drop");
        check(ComputerUtil.playStack(plusTwo,ai,game),"native +2 activation pays the printed loyalty cost");
        check(daretti.getCounters(CounterEnumType.LOYALTY)==5,"native +2 cost adds two loyalty before resolution");
        game.getStack().resolveStack();game.getAction().checkStaticAbilities();game.copyLastState();
        check(count(ZoneType.Graveyard,CardPredicates.LANDS)==2,"shared discard chooser discards only the two excess lands");
        check(ai.getCardsIn(ZoneType.Hand).size()==4,"native draw resolves for exactly the number discarded");
    }
    static void plusTwoHolds(){
        setup("daretti",3,2,0,0,PhaseType.MAIN2,true);check(!willing(plusTwo),"+2 holds when no excess land can be discarded");
        setup("daretti",3,4,0,0,PhaseType.MAIN1,true);check(!willing(plusTwo),"+2 holds before our second main phase");
        setup("daretti",3,4,0,0,PhaseType.MAIN2,false);check(!willing(plusTwo),"+2 holds during an opponent's second main phase");
    }
    static void minusTwoValueAndResolution(){
        setup("daretti",3,0,1,1,PhaseType.MAIN2,true);
        check(willing(minusTwo),"-2 is admitted with legal graveyard target and sacrifice material");
        SpellAbility choice=selected();check(choice!=null&&choice.getHostCard()==daretti&&choice.getApi()==ApiType.Sacrifice,"normal AI selection chooses the approved -2");
        check(ComputerUtil.playStack(minusTwo,ai,game),"native -2 activation pays the printed loyalty cost");
        check(daretti.getCounters(CounterEnumType.LOYALTY)==1,"native -2 cost removes two loyalty before resolution");
        game.getStack().resolveStack();game.getAction().checkStaticAbilities();game.copyLastState();
        check(count(ZoneType.Battlefield,CardPredicates.nameEquals("Wurmcoil Engine"))==1,"native ChangeZone choice returns the graveyard artifact");
        check(count(ZoneType.Graveyard,CardPredicates.nameEquals("Sol Ring"))==1,"native sacrifice choice sacrifices the battlefield artifact");
    }
    static void minusTwoHolds(){
        setup("daretti",3,0,1,0,PhaseType.MAIN2,true);check(!willing(minusTwo),"-2 holds without a legal artifact return target");
        setup("daretti",3,0,0,1,PhaseType.MAIN2,true);check(!willing(minusTwo),"-2 holds without legal artifact sacrifice material");
    }
    static void ultimateAndIsolation(){
        setup("daretti",10,0,0,0,PhaseType.MAIN2,true);add("Craw Wurm",p(1),ZoneType.Battlefield);game.getAction().checkStaticAbilities();game.copyLastState();
        check(willing(ultimate),"native Always logic accepts the ultimate at ten loyalty");
        MyRandom.setRandom(new Random(1));
        check(ComputerUtil.playStack(ultimate,ai,game),"native ultimate activation pays the printed loyalty cost");
        check(daretti.getCounters(CounterEnumType.LOYALTY)==0,"native ultimate cost removes ten loyalty before resolution");
        game.getStack().resolveStack();game.getAction().checkStaticAbilities();game.copyLastState();
        check(count(ZoneType.Command,Card::isEmblem)==1,"native ultimate resolution creates Daretti's emblem");
        for(String profile:List.of("","magus-of-the-wheel")){
            setup(profile,3,4,1,1,PhaseType.MAIN2,true);check(!"DarettiScrapSavant".equals(AiOverrideProfile.get().getAiLogic(daretti)),"unrelated profile does not attach Daretti logic");
            SpellAbility other=selected();check(other==null||other.getHostCard()!=daretti,"unrelated profile keeps blocked Daretti out of normal selection");
        }
    }
    public static void main(String[] args){GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(20261021));plusTwoValueAndResolution();plusTwoHolds();minusTwoValueAndResolution();minusTwoHolds();ultimateAndIsolation();AiOverrideProfile.setActive("");System.out.println("DONE "+passed+" Daretti checks");}
}
