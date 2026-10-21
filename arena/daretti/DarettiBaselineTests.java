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

/** Baseline proves native abilities can act, but RemoveDeck blocks ordinary selection. */
public class DarettiBaselineTests {
    static Game game; static Player ai; static Card daretti; static SpellAbility plusTwo, minusTwo; static int passed;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);passed++;System.out.println("PASS "+label);}
    static Player p(int i){return game.getPlayers().get(i);}
    static Card add(String name,Player owner,ZoneType zone){Card c=Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name),owner);c.setGameTimestamp(game.getNextTimestamp());owner.getZone(zone).add(c);c.setSickness(false);return c;}
    static void setup(){
        AiOverrideProfile.setActive("");List<RegisteredPlayer> regs=new ArrayList<>();for(int i=0;i<2;i++){LobbyPlayerAi lobby=new LobbyPlayerAi("Daretti baseline "+i,null);lobby.setAiProfile("Default");regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));}
        GameRules rules=new GameRules(GameType.Constructed);game=new Game(regs,rules,new Match(rules,regs,"Daretti baseline"));game.setAge(GameStage.Play);ai=p(0);game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);
        daretti=add("Daretti, Scrap Savant",ai,ZoneType.Battlefield);daretti.setCounters(CounterEnumType.LOYALTY,3);
        for(int i=0;i<4;i++)add("Forest",ai,ZoneType.Hand);add("Sol Ring",ai,ZoneType.Battlefield);add("Wurmcoil Engine",ai,ZoneType.Graveyard);
        plusTwo=daretti.getSpellAbilities().stream().filter(s->s.getApi()==ApiType.Discard).findFirst().orElseThrow();
        minusTwo=daretti.getSpellAbilities().stream().filter(s->s.getApi()==ApiType.Sacrifice).findFirst().orElseThrow();
        plusTwo.setActivatingPlayer(ai);minusTwo.setActivatingPlayer(ai);game.getAction().checkStaticAbilities();game.getTriggerHandler().resetActiveTriggers();game.copyLastState();
    }
    static boolean willing(SpellAbility sa){return SpellApiToAi.Converter.get(sa).canPlayWithSubs(ai,sa).willingToPlay();}
    static SpellAbility selected(){List<SpellAbility> abilities=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();return abilities==null||abilities.isEmpty()?null:abilities.get(0);}
    static void baseline(){setup();System.out.println("NATIVE baseline decisions +2="+willing(plusTwo)+" -2="+willing(minusTwo));SpellAbility choice=selected();check(choice==null||choice.getHostCard()!=daretti,"AI:RemoveDeck blocks Daretti from normal selection");}
    public static void main(String[] args){GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(20261021));baseline();AiOverrideProfile.setActive("");System.out.println("DONE "+passed+" Daretti baseline checks");}
}
