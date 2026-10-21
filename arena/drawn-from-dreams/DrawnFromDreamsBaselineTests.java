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

/** Baseline: RemoveDeck blocks the card even though native DigAi accepts it. */
public class DrawnFromDreamsBaselineTests {
    static Game game; static Player ai; static Card dreams; static SpellAbility spell; static int passed;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);passed++;System.out.println("PASS "+label);}
    static Player p(int i){return game.getPlayers().get(i);}
    static Card add(String name,Player owner,ZoneType zone){Card c=Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name),owner);c.setGameTimestamp(game.getNextTimestamp());owner.getZone(zone).add(c);c.setSickness(false);return c;}
    static void setup(){
        AiOverrideProfile.setActive("");List<RegisteredPlayer> regs=new ArrayList<>();for(int i=0;i<2;i++){LobbyPlayerAi lobby=new LobbyPlayerAi("Drawn from Dreams baseline "+i,null);lobby.setAiProfile("Default");regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));}
        GameRules rules=new GameRules(GameType.Constructed);game=new Game(regs,rules,new Match(rules,regs,"Drawn from Dreams baseline"));game.setAge(GameStage.Play);ai=p(0);game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);
        dreams=add("Drawn from Dreams",ai,ZoneType.Hand);spell=dreams.getFirstSpellAbility();spell.setActivatingPlayer(ai);
        for(int i=0;i<4;i++)add("Island",ai,ZoneType.Battlefield);
        for(String name:List.of("Sol Ring","Craw Wurm","Island","Forest","Opt","Cultivate","Grizzly Bears"))add(name,ai,ZoneType.Library);
        game.getAction().checkStaticAbilities();game.getTriggerHandler().resetActiveTriggers();game.copyLastState();
    }
    static boolean willing(){return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai,spell).willingToPlay();}
    static SpellAbility selected(){List<SpellAbility> abilities=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();return abilities==null||abilities.isEmpty()?null:abilities.get(0);}
    public static void main(String[] args){GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(20261021));setup();
        check(spell.getApi()==ApiType.Dig,"card uses the native Dig API");
        check(ComputerUtilCard.isCardRemAIDeck(dreams),"card script marks Drawn from Dreams RemoveDeck:All");
        check(willing(),"native DigAi accepts the sorcery in our second main phase");
        check(selected()==null,"normal AI selection excludes the otherwise-willing card without its profile");
        AiOverrideProfile.setActive("");System.out.println("DONE "+passed+" Drawn from Dreams baseline checks");
    }
}
