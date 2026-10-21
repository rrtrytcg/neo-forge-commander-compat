import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Baseline: native CharmAi declines this useful repeated-mode situation. */
public class MysticConfluenceBaselineTests {
    static Game game; static Player ai; static Card confluence; static SpellAbility spell; static int passed;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);passed++;System.out.println("PASS "+label);}
    static Player p(int i){return game.getPlayers().get(i);}
    static Card add(String name,Player owner,ZoneType zone){Card c=Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name),owner);c.setGameTimestamp(game.getNextTimestamp());owner.getZone(zone).add(c);c.setSickness(false);return c;}
    static void setup(){
        AiOverrideProfile.setActive("");List<RegisteredPlayer> regs=new ArrayList<>();for(int i=0;i<2;i++){LobbyPlayerAi lobby=new LobbyPlayerAi("Mystic baseline "+i,null);lobby.setAiProfile("Default");regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));}
        GameRules rules=new GameRules(GameType.Constructed);game=new Game(regs,rules,new Match(rules,regs,"Mystic Confluence baseline"));game.setAge(GameStage.Play);ai=p(0);game.getPhaseHandler().devModeSet(PhaseType.MAIN1,ai);
        confluence=add("Mystic Confluence",ai,ZoneType.Hand);spell=confluence.getFirstSpellAbility();spell.setActivatingPlayer(ai);
        for(int i=0;i<7;i++)add("Island",ai,ZoneType.Battlefield);
        add("Craw Wurm",p(1),ZoneType.Battlefield);
        for(int i=0;i<8;i++)add("Island",ai,ZoneType.Library);
        game.getAction().checkStaticAbilities();game.getTriggerHandler().resetActiveTriggers();game.copyLastState();
    }
    static boolean willing(){return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai,spell).willingToPlay();}
    public static void main(String[] args){GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(20261021));setup();check(!willing(),"native CharmAi declines with one useful targeted mode because it cannot repeat draw");AiOverrideProfile.setActive("");System.out.println("DONE "+passed+" Mystic Confluence baseline checks");}
}
