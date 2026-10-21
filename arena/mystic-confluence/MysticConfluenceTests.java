import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.ability.ApiType;
import forge.game.ability.effects.CharmEffect;
import forge.game.card.*;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Native fixtures for the opt-in Mystic Confluence mode chooser. */
public class MysticConfluenceTests {
    static Game game; static Player ai; static Card confluence; static SpellAbility spell; static int passed;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);passed++;System.out.println("PASS "+label);}
    static Player p(int i){return game.getPlayers().get(i);}
    static Card add(String name,Player owner,ZoneType zone){Card c=Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name),owner);c.setGameTimestamp(game.getNextTimestamp());owner.getZone(zone).add(c);c.setSickness(false);return c;}
    static void setup(String profile,boolean creature,boolean pendingSpell,int islands){
        AiOverrideProfile.setActive(profile);List<RegisteredPlayer> regs=new ArrayList<>();for(int i=0;i<2;i++){LobbyPlayerAi lobby=new LobbyPlayerAi("Mystic fixture "+i,null);lobby.setAiProfile("Default");regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));}
        GameRules rules=new GameRules(GameType.Constructed);game=new Game(regs,rules,new Match(rules,regs,"Mystic Confluence fixtures"));game.setAge(GameStage.Play);ai=p(0);game.getPhaseHandler().devModeSet(PhaseType.MAIN1,ai);
        confluence=add("Mystic Confluence",ai,ZoneType.Hand);spell=confluence.getFirstSpellAbility();spell.setActivatingPlayer(ai);
        for(int i=0;i<islands;i++)add("Island",ai,ZoneType.Battlefield);
        if(creature){add("Craw Wurm",p(1),ZoneType.Battlefield);add("Grizzly Bears",p(1),ZoneType.Battlefield);}
        for(int i=0;i<10;i++)add("Island",ai,ZoneType.Library);
        if(pendingSpell){Card opt=add("Opt",p(1),ZoneType.Hand);SpellAbility other=opt.getFirstSpellAbility();other.setActivatingPlayer(p(1));other.setHostCard(game.getAction().moveToStack(opt,other));game.getStack().add(other);}
        game.getAction().checkStaticAbilities();game.getTriggerHandler().resetActiveTriggers();game.copyLastState();
    }
    static boolean willing(){return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai,spell).willingToPlay();}
    static SpellAbility selected(){List<SpellAbility> abilities=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();return abilities==null||abilities.isEmpty()?null:abilities.get(0);}
    static long modeCount(ApiType api){return spell.getChosenList().stream().filter(s->s.getApi()==api).count();}
    static void usefulCreatureModeRepeatsDrawAndResolves(){
        setup("mystic-confluence",true,false,7);
        check("MysticConfluence".equals(AiOverrideProfile.get().getAiLogic(confluence)),"profile attaches only Mystic Confluence logic");
        check(willing(),"one useful bounce target admits the instant");
        check(modeCount(ApiType.ChangeZone)==1&&modeCount(ApiType.Draw)==2,"native mode chain selects bounce then repeats draw twice");
        check(((Card)spell.getChosenList().stream().filter(s->s.getApi()==ApiType.ChangeZone).findFirst().orElseThrow().getTargets().get(0)).getName().equals("Craw Wurm"),"bounce selects the highest-valued legal opposing creature");
        check(selected()!=null&&selected().getHostCard()==confluence,"normal AI selection includes Mystic Confluence when a mode target is useful");
        check(ComputerUtil.playStack(spell,ai,game),"native casting pays five mana for the chosen modes");
        game.getStack().resolveStack();game.getAction().checkStaticAbilities();game.copyLastState();
        check(p(1).getCardsIn(ZoneType.Hand).stream().anyMatch(c->c.getName().equals("Craw Wurm")),"native ChangeZone mode returns the opposing creature");
        check(p(1).getCardsIn(ZoneType.Battlefield).stream().anyMatch(c->c.getName().equals("Grizzly Bears")),"lower-valued opposing creature is left untouched");
        check(ai.getCardsIn(ZoneType.Hand).size()==2,"two repeated native draw modes resolve");
    }
    static void counterAndBouncePrioritizeBeforeDraw(){
        setup("mystic-confluence",true,true,7);
        check(willing(),"counter and bounce modes are independently useful");
        check(modeCount(ApiType.Counter)==1&&modeCount(ApiType.ChangeZone)==1&&modeCount(ApiType.Draw)==1,"native chain prioritizes counter, then bounce, then draw");
        check(ComputerUtil.playStack(spell,ai,game),"native cast can respond to an opposing spell");
        game.getStack().resolveStack();game.getAction().checkStaticAbilities();game.copyLastState();
        check(game.getStack().isEmpty(),"native counter mode removes the opposing spell from the stack");
        check(p(1).getCardsIn(ZoneType.Hand).stream().anyMatch(c->c.getName().equals("Craw Wurm")),"native bounce mode resolves alongside the counter");
        check(ai.getCardsIn(ZoneType.Hand).size()==1,"single draw mode resolves once when both targeted modes are useful");
    }
    static void holdsAndProfileIsolation(){
        setup("mystic-confluence",false,false,7);check(!willing(),"holds when no counter or bounce target is useful");
        setup("mystic-confluence",true,false,4);check(willing(),"useful modes remain selected before native payment");check(!ComputerUtil.playStack(spell,ai,game),"native mana payment rejects the cast when five mana is unavailable");
        setup("",true,false,7);check(!willing(),"default CharmAi retains its repeated-mode rejection");check(selected()==null,"default profile retains the card's RemoveDeck block");
        setup("daretti",true,false,7);check(!"MysticConfluence".equals(AiOverrideProfile.get().getAiLogic(confluence)),"unrelated profile does not attach Mystic Confluence logic");check(selected()==null,"unrelated profile retains the card's RemoveDeck block");
    }
    public static void main(String[] args){GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(20261021));usefulCreatureModeRepeatsDrawAndResolves();counterAndBouncePrioritizeBeforeDraw();holdsAndProfileIsolation();AiOverrideProfile.setActive("");System.out.println("DONE "+passed+" Mystic Confluence checks");}
}
