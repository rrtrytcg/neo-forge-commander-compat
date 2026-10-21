import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.ability.ApiType;
import forge.game.card.*;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Native Phyrexia's Core threshold, combat-danger, profile and artifact-payment fixtures. */
public class PhyrexiasCoreTests {
    static Game game; static Player ai; static Card core; static SpellAbility gainLife; static Card firstArtifact, secondArtifact; static int passed;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);passed++;System.out.println("PASS "+label);}
    static Player p(int i){return game.getPlayers().get(i);}
    static Card add(String name,Player owner,ZoneType zone){Card c=Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name),owner);c.setGameTimestamp(game.getNextTimestamp());owner.getZone(zone).add(c);c.setSickness(false);return c;}
    static void refresh(){game.getAction().checkStaticAbilities();game.getTriggerHandler().resetActiveTriggers();game.copyLastState();}
    static void setup(String profile,int life,int manaSources,int artifacts){
        AiOverrideProfile.setActive(profile);
        List<RegisteredPlayer> regs=new ArrayList<>();for(int i=0;i<2;i++){LobbyPlayerAi lobby=new LobbyPlayerAi("Core fixture "+i,null);lobby.setAiProfile("Default");regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));}
        GameRules rules=new GameRules(GameType.Constructed);game=new Game(regs,rules,new Match(rules,regs,"Phyrexia's Core native fixtures"));game.setAge(GameStage.Play);ai=p(0);ai.setLife(life,null);game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);
        for(int i=0;i<manaSources;i++)add("Forest",ai,ZoneType.Battlefield);
        core=add("Phyrexia's Core",ai,ZoneType.Battlefield);
        if(artifacts>0)firstArtifact=add("Sol Ring",ai,ZoneType.Battlefield);
        if(artifacts>1)secondArtifact=add("Mind Stone",ai,ZoneType.Battlefield);
        gainLife=core.getSpellAbilities().stream().filter(s->s.getApi()==ApiType.GainLife).findFirst().orElseThrow();gainLife.setActivatingPlayer(ai);refresh();
    }
    static boolean willing(){return SpellApiToAi.Converter.get(gainLife).canPlayWithSubs(ai,gainLife).willingToPlay();}
    static SpellAbility selected(){List<SpellAbility> abilities=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();return abilities==null||abilities.isEmpty()?null:abilities.get(0);}
    static int battlefieldArtifacts(){return CardLists.count(ai.getCardsIn(ZoneType.Battlefield),Card::isArtifact);}
    static int graveyardArtifacts(){return CardLists.count(ai.getCardsIn(ZoneType.Graveyard),Card::isArtifact);}

    static void baseline(){setup("",5,1,2);check(!willing(),"baseline native AI declines Phyrexia's Core even with low life and legal sacrifice material");SpellAbility choice=selected();check(choice==null||choice.getHostCard()!=core,"baseline deck filter keeps Phyrexia's Core out of normal selection");}
    static void lowLifeActivation(){
        setup("phyrexias-core",5,1,2);
        check("PhyrexiasCore".equals(AiOverrideProfile.get().getAiLogic(core)),"profile gates only Phyrexia's Core");
        check(willing(),"five life permits activation under the opt-in profile");
        check(battlefieldArtifacts()==2&&gainLife.getTargets().isEmpty(),"decision leaves both artifacts and the native ability choice untouched");
        SpellAbility choice=selected();check(choice!=null&&choice.getHostCard()==core,"normal native AI selection chooses Phyrexia's Core when life is critical");
        check(ComputerUtil.playStack(gainLife,ai,game),"native activation pays one mana, taps Core, and selects its artifact sacrifice");
        check(core.isTapped()&&battlefieldArtifacts()==1&&graveyardArtifacts()==1,"native payment taps Core and sacrifices exactly one of the legal artifacts");
        game.getStack().resolveStack();refresh();check(ai.getLife()==6,"original effect gains exactly one life");
    }
    static void combatDanger(){
        setup("phyrexias-core",10,1,1);game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS,p(1));Combat combat=new Combat(p(1));game.getPhaseHandler().setCombat(combat);Card attacker=add("Electryte",p(1),ZoneType.Battlefield);combat.addAttacker(attacker,ai);refresh();
        check(ComputerUtilCombat.lifeInDanger(ai,combat),"native combat evaluator marks an unblocked MustBeBlocked attacker dangerous");
        check(willing(),"native combat-danger evaluation permits Core above five life");
    }
    static void holdsAndIsolation(){
        setup("phyrexias-core",6,1,2);check(!willing(),"holds above five life with no combat danger");
        setup("phyrexias-core",5,1,0);check(!willing(),"holds at low life when no legal artifact can be sacrificed");
        setup("phyrexias-core",5,0,0);add("Darksteel Relic",ai,ZoneType.Battlefield);refresh();check(!willing(),"holds when the one-mana activation cost cannot be paid despite legal artifact material");
        setup("phyrexias-core",5,1,1);core.setTapped(true);refresh();check(!willing(),"holds when Core is already tapped");
        for(String profile:List.of("","molten-slagheap")){setup(profile,5,1,2);check(!"PhyrexiasCore".equals(AiOverrideProfile.get().getAiLogic(core)),"unrelated profile does not attach Core logic");SpellAbility choice=selected();check(choice==null||choice.getHostCard()!=core,"unrelated profile keeps blocked Core out of normal selection");}
    }
    public static void main(String[] args){GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(20261021));if(args.length>0&&"baseline".equals(args[0]))baseline();else{lowLifeActivation();combatDanger();holdsAndIsolation();}AiOverrideProfile.setActive("");System.out.println("DONE "+passed+" Phyrexia's Core checks");}
}
