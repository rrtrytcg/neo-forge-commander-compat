import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.*;
import forge.game.event.GameEventShuffle;
import com.google.common.eventbus.Subscribe;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Native controller, payment and original ETB search; no synthetic search implementation. */
public class OreskosExplorerTests {
    static Game game;
    static Player ai;
    static Card explorer;
    static int passed;
    public static class ShuffleObserver {
        int count;
        @Subscribe public void shuffled(GameEventShuffle event) { if(event.player().getId()==ai.getId()) { count++; } }
    }
    static void check(boolean result,String label) {
        if(!result) { throw new AssertionError(label); }
        passed++; System.out.println("PASS "+label);
    }
    static Card add(String name,Player owner,ZoneType zone) {
        Card c=Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name),owner);
        c.setGameTimestamp(game.getNextTimestamp()); owner.getZone(zone).add(c); c.setSickness(false); return c;
    }
    static void refresh() { game.getAction().checkStaticAbilities(); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState(); }
    static void setup(int[] opposingLands,int plains,String landName) {
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            LobbyPlayerAi lobby=new LobbyPlayerAi("Explorer-"+i,null); lobby.setAiProfile("Default");
            players.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Constructed);
        game=new Game(players,rules,new Match(rules,players,"Oreskos Explorer native fixtures"));
        game.setAge(GameStage.Play); ai=game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);
        add("Plains",ai,ZoneType.Battlefield); add("Plains",ai,ZoneType.Battlefield);
        for(int i=0;i<3;i++) {
            for(int n=0;n<opposingLands[i];n++) { add("Forest",game.getPlayers().get(i+1),ZoneType.Battlefield); }
        }
        explorer=add("Oreskos Explorer",ai,ZoneType.Hand);
        for(int i=0;i<plains;i++) { add(landName,ai,ZoneType.Library); }
        add("Forest",ai,ZoneType.Library); add("Eager Cadet",ai,ZoneType.Library);
        AiOverrideProfile.setActive("oreskos-explorer"); refresh();
    }
    static List<SpellAbility> selected() { return ((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay(); }
    static boolean selectsExplorer() {
        List<SpellAbility> result=selected(); return result!=null && result.stream().anyMatch(sa->sa.getHostCard()==explorer);
    }
    static void castAndSearch(int expected,String label) {
        castAndSearch(expected,label,false);
    }
    static void castAndSearch(int expected,String label,boolean addLandBeforeSearch) {
        ShuffleObserver observer=new ShuffleObserver(); game.subscribeToEvents(observer);
        check(selectsExplorer(),label+": normal native selection casts body");
        SpellAbility spell=explorer.getFirstSpellAbility(); spell.setActivatingPlayer(ai);
        check(ComputerUtil.playStack(spell,ai,game),label+": native payment puts creature on stack");
        check(ai.getLandsInPlay().stream().filter(Card::isTapped).count()==2,label+": two Plains pay 1W");
        game.getStack().resolveStack(); refresh();
        check(ai.getCreaturesInPlay().stream().anyMatch(c->c.getName().equals("Oreskos Explorer")),label+": body resolves");
        game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
        if(addLandBeforeSearch) { add("Forest",game.getPlayers().get(1),ZoneType.Battlefield); refresh(); }
        int guard=0;
        while(!game.getStack().isEmpty() && guard++<8) { game.getStack().resolveStack(); }
        check(game.getStack().isEmpty(),label+": original ETB finishes");
        check(ai.getCardsIn(ZoneType.Hand).size()==expected,label+": fetches exactly "+expected+" cards");
        check(ai.getCardsIn(ZoneType.Hand).stream().allMatch(c->c.getType().hasSubtype("Plains")),label+": every fetched card is a Plains");
        check(ai.getLandsInPlay().size()==2,label+": searched lands go to hand, not battlefield");
        if(expected>0) { check(observer.count==1,label+": native library shuffle occurs once"); }
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null); MyRandom.setRandom(new Random(7391));
        for(int x=0;x<=3;x++) {
            int[] lands={2,2,2}; for(int i=0;i<x;i++) { lands[i]=3; }
            setup(lands,5,"Plains"); castAndSearch(x,"X="+x);
        }
        setup(new int[]{1,2,3},5,"Plains"); castAndSearch(1,"only strictly greater land counts qualify");
        setup(new int[]{3,4,5},1,"Plains"); castAndSearch(1,"fewer Plains than X");
        setup(new int[]{3,4,5},0,"Plains"); castAndSearch(0,"no Plains still casts body");
        setup(new int[]{3,4,5},3,"Hallowed Fountain"); castAndSearch(3,"nonbasic Plains qualify");
        setup(new int[]{2,2,2},5,"Plains"); castAndSearch(1,"count updates when lands change before resolution",true);
        setup(new int[]{3,4,5},5,"Plains");
        add("Aven Mindcensor",game.getPlayers().get(1),ZoneType.Battlefield);
        // Only Forests occur in the first four positions of this restricted search.
        for(Card c:new ArrayList<Card>(ai.getCardsIn(ZoneType.Library))) { ai.getZone(ZoneType.Library).remove(c); }
        for(int i=0;i<4;i++) { add("Forest",ai,ZoneType.Library); }
        for(int i=0;i<5;i++) { add("Plains",ai,ZoneType.Library); }
        refresh(); castAndSearch(0,"native restricted search sees no eligible Plains");
        setup(new int[]{3,3,3},5,"Plains");
        for(Card land:ai.getLandsInPlay()) { land.setTapped(true); }
        check(!selectsExplorer(),"native payment: tapped mana cannot cast");
        setup(new int[]{3,3,3},5,"Plains");
        for(String profile:List.of("","mirror-entity","adaptive-enchantment","ahoy-mateys","decree-of-pain","curse-of-the-swine")) {
            AiOverrideProfile.setActive(profile);
            check(ComputerUtilCard.isCardRemAIDeck(explorer) && !selectsExplorer(),"isolation: still blocked in '"+profile+"'");
        }
        setup(new int[]{3,3,3},5,"Plains");
        check(AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(explorer) && selectsExplorer(),"profile admission enables native selection");
        check(ComputerUtilCard.isCardRemAIDeck(explorer),"original deck-building warning is preserved");
        check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(add("Mirror Entity",ai,ZoneType.Hand)),"profile does not admit Mirror Entity");
        check(AiOverrideProfile.get().getAiLogic(explorer).equals("OptionalPlainsSearch"),"profile only overrides harmless optional-search veto");
        setup(new int[]{3,3,3},0,"Plains");
        SpellAbility search=explorer.getTriggers().get(0).ensureAbility().copy(ai);
        search.setTrigger(explorer.getTriggers().get(0));
        check(SpellApiToAi.Converter.get(search).doTrigger(ai,search,false),"opt-in empty search is harmless for ETB assessment");
        AiOverrideProfile.setActive("");
        check(!SpellApiToAi.Converter.get(search).doTrigger(ai,search,false),"default empty-search ETB assessment is unchanged");
        AiOverrideProfile.setActive(""); System.out.println("DONE "+passed+" Explorer checks");
    }
}
