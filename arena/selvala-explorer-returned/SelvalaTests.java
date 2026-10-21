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
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Original-script fixtures for the opt-in Selvala admission and native parley. */
public class SelvalaTests {
    static Game game;
    static Player ai;
    static Card selvala;
    static int passed;
    static void check(boolean ok,String label) {
        if(!ok) { throw new AssertionError(label); }
        passed++; System.out.println("PASS "+label);
    }
    static Player p(int i) { return game.getPlayers().get(i); }
    static Card add(String name,Player owner,ZoneType zone) {
        Card c=Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name),owner);
        c.setGameTimestamp(game.getNextTimestamp()); owner.getZone(zone).add(c); c.setSickness(false); return c;
    }
    static void refresh() { game.getAction().checkStaticAbilities(); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState(); }
    static void base() {
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            LobbyPlayerAi lobby=new LobbyPlayerAi("Selvala-"+i,null); lobby.setAiProfile("Default");
            players.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Constructed); game=new Game(players,rules,new Match(rules,players,"Selvala fixtures"));
        game.setAge(GameStage.Play); ai=p(0); game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);
    }
    static void setupBattlefield(int lands) {
        base();
        for(int i=0;i<lands;i++) { add(i%2==0?"Plains":"Forest",ai,ZoneType.Battlefield); }
        selvala=add("Selvala, Explorer Returned",ai,ZoneType.Battlefield);
        AiOverrideProfile.setActive("selvala-explorer-returned"); refresh();
    }
    static void setupHand() {
        base();
        for(int i=0;i<3;i++) { add(i%2==0?"Plains":"Forest",ai,ZoneType.Battlefield); }
        selvala=add("Selvala, Explorer Returned",ai,ZoneType.Hand);
        AiOverrideProfile.setActive("selvala-explorer-returned"); refresh();
    }
    static SpellAbility castSelection() {
        List<SpellAbility> list=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();
        return list==null?null:list.stream().filter(x->x.getHostCard()==selvala && x.isSpell()).findFirst().orElse(null);
    }
    static SpellAbility parley() {
        return selvala.getSpellAbilities().stream().filter(x->x.getApi()==ApiType.PeekAndReveal).findFirst().orElseThrow();
    }
    static SpellAbility parleySelection() {
        List<SpellAbility> list=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();
        return list==null?null:list.stream().filter(x->x.getHostCard()==selvala && x.getApi()==ApiType.PeekAndReveal).findFirst().orElse(null);
    }
    static void admission() {
        setupBattlefield(0);
        check(AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(selvala),"profile admits the comma-quoted card during play");
        check(ComputerUtilCard.isCardRemAIDeck(selvala),"original deck-building warning retained");
        AiOverrideProfile.setActive("");
        check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(selvala),"inactive profile keeps the play prohibition");
        setupHand();
        check(castSelection()!=null,"profile casts Selvala through the normal play-list");
        AiOverrideProfile.setActive("");
        check(castSelection()==null,"default play-list keeps Selvala blocked");
        setupBattlefield(0);
        AiOverrideProfile.setActive("abzan-armor");
        Card tower=add("Tower Defense",ai,ZoneType.Hand); Card crawler=add("Psychosis Crawler",ai,ZoneType.Hand);
        check(AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(tower)
                && AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(crawler),"unquoted comma lists still parse");
    }
    static void parleyResolution() {
        setupBattlefield(0);
        for(int seat=0;seat<4;seat++) {
            p(seat).getZone(ZoneType.Library).removeAllCards(true);
            for(int i=0;i<12;i++) { add("Grizzly Bears",p(seat),ZoneType.Library); }
        }
        add("Grizzly Bears",ai,ZoneType.Hand);
        ai.setLife(20,null); refresh();
        int hand=ai.getCardsIn(ZoneType.Hand).size();
        SpellAbility parley=parley(); parley.setActivatingPlayer(ai);
        boolean played=ComputerUtil.playStack(parley,ai,game);
        game.getStack().resolveStack(); game.getAction().checkStateEffects(true);
        check(played,"native parley activation is queued");
        check(selvala.isTapped(),"native tap cost paid");
        check(ai.getLife()==24,"four revealed nonlands gain four life");
        check(ai.getCardsIn(ZoneType.Hand).size()==hand+1,"controller draws one revealed card");
        check(p(1).getCardsIn(ZoneType.Hand).size()==1 && p(2).getCardsIn(ZoneType.Hand).size()==1
                && p(3).getCardsIn(ZoneType.Hand).size()==1,"each opponent draws one revealed card");
        check(ai.getCardsIn(ZoneType.Library).size()==11,"controller's library loses the drawn card");
    }
    static void aiActivation() {
        setupBattlefield(0);
        add("Grizzly Bears",ai,ZoneType.Hand);
        for(int seat=0;seat<4;seat++) {
            p(seat).getZone(ZoneType.Library).removeAllCards(true);
            for(int i=0;i<12;i++) { add("Grizzly Bears",p(seat),ZoneType.Library); }
        }
        refresh();
        SpellAbility chosen=parleySelection();
        check(chosen!=null,"AI selects the parley ability when a green spell needs mana");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null); MyRandom.setRandom(new Random(96901));
        admission(); parleyResolution(); aiActivation(); System.out.println("DONE "+passed+" Selvala checks");
    }
}
