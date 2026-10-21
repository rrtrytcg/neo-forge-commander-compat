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

/** Native profile, selection, payment and resolution fixtures for Drawn from Dreams. */
public class DrawnFromDreamsTests {
    static Game game; static Player ai; static Card dreams; static SpellAbility spell; static int passed;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);passed++;System.out.println("PASS "+label);}
    static Player p(int i){return game.getPlayers().get(i);}
    static Card add(String name,Player owner,ZoneType zone){Card c=Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name),owner);c.setGameTimestamp(game.getNextTimestamp());owner.getZone(zone).add(c);c.setSickness(false);return c;}
    static void setup(String profile,PhaseType phase){
        AiOverrideProfile.setActive(profile);List<RegisteredPlayer> regs=new ArrayList<>();for(int i=0;i<2;i++){LobbyPlayerAi lobby=new LobbyPlayerAi("Drawn from Dreams fixture "+i,null);lobby.setAiProfile("Default");regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));}
        GameRules rules=new GameRules(GameType.Constructed);game=new Game(regs,rules,new Match(rules,regs,"Drawn from Dreams fixtures"));game.setAge(GameStage.Play);ai=p(0);game.getPhaseHandler().devModeSet(phase,ai);
        dreams=add("Drawn from Dreams",ai,ZoneType.Hand);spell=dreams.getFirstSpellAbility();spell.setActivatingPlayer(ai);
        for(int i=0;i<4;i++)add("Island",ai,ZoneType.Battlefield);
        for(String name:List.of("Sol Ring","Craw Wurm","Island","Forest","Opt","Cultivate","Grizzly Bears"))add(name,ai,ZoneType.Library);
        game.getAction().checkStaticAbilities();game.getTriggerHandler().resetActiveTriggers();game.copyLastState();
    }
    static boolean willing(){return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai,spell).willingToPlay();}
    static SpellAbility selected(){List<SpellAbility> abilities=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();return abilities==null||abilities.isEmpty()?null:abilities.get(0);}
    static void nativeDigSelectionAndResolution(){
        setup("drawn-from-dreams",PhaseType.MAIN2);
        check(AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(dreams),"profile bypasses RemoveDeck only for Drawn from Dreams");
        check(AiOverrideProfile.get().getAiLogic(dreams).isEmpty(),"profile keeps the stock DigAi and its card-choice logic");
        check(willing(),"native DigAi accepts the spell in the second main phase");
        SpellAbility choice=selected();check(choice!=null&&choice.getHostCard()==dreams,"normal AI selection includes Drawn from Dreams under its profile");
        List<Card> expectedPool=new ArrayList<>(ai.getCardsIn(ZoneType.Library));
        Card expectedFirst=ComputerUtilCard.getBestAI(expectedPool);expectedPool.remove(expectedFirst);
        Card expectedSecond=ComputerUtilCard.getBestAI(expectedPool);expectedPool.remove(expectedSecond);
        check(ComputerUtil.playStack(spell,ai,game),"native payment pays two blue and two generic mana");
        game.getStack().resolveStack();game.getAction().checkStaticAbilities();game.copyLastState();
        CardCollectionView hand=ai.getCardsIn(ZoneType.Hand);CardCollectionView library=ai.getCardsIn(ZoneType.Library);
        check(hand.size()==2&&hand.contains(expectedFirst)&&hand.contains(expectedSecond),"native Dig choice moves its two best evaluated cards to hand");
        check(library.size()==5&&library.containsAll(expectedPool),"native effect returns the other five cards to the library");
        check(ai.getCardsIn(ZoneType.Graveyard).contains(dreams),"native sorcery finishes in its owner's graveyard");
    }
    static void defaultAndUnrelatedProfilesRemainBlocked(){
        for(String profile:List.of("","mystic-confluence")){
            setup(profile,PhaseType.MAIN2);
            check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(dreams),"profile '"+profile+"' does not bypass Drawn from Dreams");
            check(selected()==null,"profile '"+profile+"' retains the RemoveDeck block");
        }
    }
    public static void main(String[] args){GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(20261021));nativeDigSelectionAndResolution();defaultAndUnrelatedProfilesRemainBlocked();AiOverrideProfile.setActive("");System.out.println("DONE "+passed+" Drawn from Dreams checks");}
}
