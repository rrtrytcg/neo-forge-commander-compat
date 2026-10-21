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

/** Original-script fixtures for the opt-in Trading Post mode and sacrifice policy. */
public class TradingPostTests {
    static Game game;
    static Player ai;
    static Card post;
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
    static void setup(int life) {
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            LobbyPlayerAi lobby=new LobbyPlayerAi("Post-"+i,null); lobby.setAiProfile("Default");
            players.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Constructed); game=new Game(players,rules,new Match(rules,players,"Trading Post fixtures"));
        game.setAge(GameStage.Play); ai=p(0); ai.setLife(life,null); game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);
        for(int i=0;i<5;i++) { add("Wastes",ai,ZoneType.Battlefield); }
        for(int i=0;i<20;i++) { add("Grizzly Bears",ai,ZoneType.Library); }
        post=add("Trading Post",ai,ZoneType.Battlefield);
        AiOverrideProfile.setActive("trading-post"); refresh();
    }
    static Card permanent(String name,int seat) { Card c=add(name,p(seat),ZoneType.Battlefield); refresh(); return c; }
    static Card hand(String name) { Card c=add(name,ai,ZoneType.Hand); refresh(); return c; }
    static SpellAbility selection() {
        List<SpellAbility> list=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();
        return list==null?null:list.stream().filter(sa->sa.getHostCard()==post).findFirst().orElse(null);
    }
    static void expect(ApiType api,String label) {
        SpellAbility sa=selection(); check(sa!=null,label+": normal controller selects a mode");
        check(sa.getApi()==api,label+": expected "+api);
    }
    static void choices() {
        setup(20); hand("Grizzly Bears"); permanent("Darksteel Relic",0); permanent("Prophetic Prism",0);
        expect(ApiType.Draw,"expendable artifacts choose card draw");
        setup(20); hand("Grizzly Bears"); add("Darksteel Relic",ai,ZoneType.Graveyard);
        permanent("Grizzly Bears",0); permanent("Grizzly Bears",0);
        expect(ApiType.ChangeZone,"graveyard artifact plus creature fodder returns the artifact");
        setup(20); hand("Grizzly Bears"); expect(ApiType.Token,"no fodder falls back to a Goat");
        setup(3); hand("Grizzly Bears"); hand("Craw Wurm"); expect(ApiType.GainLife,"low life with a spare card gains life");
        setup(2); hand("Grizzly Bears"); check(selection()==null,"no safe mode at low life");
        setup(20); hand("Grizzly Bears"); permanent("Darksteel Relic",0);
        expect(ApiType.Token,"single non-token artifact is not sacrificed for draw");
    }
    static void nativeDraw() {
        setup(20); hand("Grizzly Bears"); Card relic=permanent("Darksteel Relic",0); Card key=permanent("Darksteel Plate",0);
        SpellAbility sa=selection(); check(sa!=null && sa.getApi()==ApiType.Draw,"draw mode selected");
        int hand=ai.getCardsIn(ZoneType.Hand).size();
        check(ComputerUtil.handlePlayingSpellAbility(ai,sa,null),"draw mode pays costs through normal play");
        check(!game.getStack().isEmpty(),"draw mode reaches native stack");
        game.getStack().resolveStack(); game.getAction().checkStateEffects(true); refresh();
        check(ai.getCardsIn(ZoneType.Hand).size()==hand+1,"draw mode draws one card");
        check(game.getCardState(relic,null).isInZone(ZoneType.Graveyard),"cheap artifact is sacrificed");
        check(game.getCardState(key,null).isInZone(ZoneType.Battlefield),"key artifact survives the draw mode");
        check(post.isTapped(),"native tap cost paid");
    }
    static void nativeGoat() {
        setup(20); hand("Grizzly Bears"); SpellAbility sa=selection(); check(sa!=null && sa.getApi()==ApiType.Token,"Goat mode selected");
        int life=ai.getLife();
        check(ComputerUtil.handlePlayingSpellAbility(ai,sa,null),"Goat mode pays costs through normal play");
        check(!game.getStack().isEmpty(),"Goat mode reaches native stack");
        game.getStack().resolveStack(); game.getAction().checkStateEffects(true); refresh();
        check(ai.getLife()==life-1,"one life paid");
        check(ai.getCreaturesInPlay().stream().anyMatch(c->c.isToken() && c.getName().startsWith("Goat")
                && c.getNetPower()==0 && c.getNetToughness()==1),"native 0/1 Goat token created");
    }
    static void nativeReturn() {
        setup(20); hand("Grizzly Bears"); Card relic=add("Darksteel Relic",ai,ZoneType.Graveyard);
        Card fodder=permanent("Grizzly Bears",0); Card keep=permanent("Craw Wurm",0);
        SpellAbility sa=selection(); check(sa!=null && sa.getApi()==ApiType.ChangeZone,"return mode selected");
        check(sa.getTargetCard()==relic && sa.isTargetNumberValid(),"normal AI selects its own legal graveyard target");
        check(ComputerUtil.handlePlayingSpellAbility(ai,sa,null),"return mode pays costs through normal play");
        check(!game.getStack().isEmpty(),"return mode reaches native stack before resolution");
        game.getStack().resolveStack(); game.getAction().checkStateEffects(true); refresh();
        check(game.getCardState(relic,null).isInZone(ZoneType.Hand),"graveyard artifact returned to hand");
        check(game.getCardState(fodder,null).isInZone(ZoneType.Graveyard),"creature fodder sacrificed");
        check(game.getCardState(keep,null).isInZone(ZoneType.Battlefield),"larger creature survives");
    }
    static void nativeLife() {
        setup(3); hand("Grizzly Bears"); hand("Craw Wurm"); SpellAbility sa=selection();
        check(sa!=null && sa.getApi()==ApiType.GainLife,"life mode selected");
        int life=ai.getLife();
        check(ComputerUtil.handlePlayingSpellAbility(ai,sa,null),"life mode pays costs through normal play");
        check(!game.getStack().isEmpty(),"life mode reaches native stack");
        game.getStack().resolveStack(); game.getAction().checkStateEffects(true); refresh();
        check(ai.getLife()==life+4,"four life gained");
        check(ai.getCardsIn(ZoneType.Hand).size()==1,"one spare card discarded");
    }
    static void boundaries() {
        setup(20); hand("Grizzly Bears");
        check(post.getRules().getAiHints().getRemAIDecks(),"original deck-building prohibition retained");
        AiOverrideProfile.setActive(""); check(selection()==null,"default profile keeps Trading Post blocked");
        AiOverrideProfile.setActive("gaze-of-granite"); check(selection()==null,"unrelated profile keeps Trading Post blocked");
        AiOverrideProfile.setActive("trading-post"); check(selection()!=null,"opt-in admission enables native play");
    }
    static void drawSafety() {
        for(int size:new int[]{0,1,4,5}) {
            setup(20); hand("Grizzly Bears"); Card relic=permanent("Darksteel Relic",0); permanent("Darksteel Plate",0);
            ai.getZone(ZoneType.Library).removeAllCards(true);
            for(int i=0;i<size;i++) { add("Grizzly Bears",ai,ZoneType.Library); } refresh();
            SpellAbility sa=selection();
            check(sa!=null && sa.getApi()==(size>4?ApiType.Draw:ApiType.Token),"library size "+size+" selects safe mode");
            if(size<=4) {
                check(ComputerUtil.handlePlayingSpellAbility(ai,sa,null) && !game.getStack().isEmpty(),"safe fallback reaches stack: "+size);
                game.getStack().resolveStack(); game.getAction().checkStateEffects(true); refresh();
                check(!ai.hasLost() && ai.getCardsIn(ZoneType.Library).size()==size,"fallback neither draws nor decks: "+size);
                check(game.getCardState(relic,null).isInZone(ZoneType.Battlefield),"unsafe draw spends no artifact: "+size);
            }
        }
        setup(20); hand("Grizzly Bears"); permanent("Darksteel Relic",0); permanent("Darksteel Plate",0);
        permanent("Maralen of the Mornsong",1); expect(ApiType.Token,"cannot draw uses safe fallback");
    }
    static void legalityAndLifecycle() {
        setup(20); hand("Grizzly Bears"); add("Darksteel Relic",ai,ZoneType.Graveyard);
        permanent("Grizzly Bears",0); permanent("Craw Wurm",0); permanent("Ground Seal",1);
        SpellAbility sa=selection(); check(sa==null || sa.getApi()!=ApiType.ChangeZone,"graveyard targeting prohibition respected");
        setup(20); hand("Grizzly Bears"); post.setTapped(true); check(selection()==null,"tapped source cannot activate");
        setup(20); hand("Grizzly Bears"); for(Card land:ai.getLandsInPlay()) {land.setTapped(true);}
        check(selection()==null,"native mana feasibility retained");
        setup(20); hand("Grizzly Bears"); check(selection()!=null,"prior game creates a policy decision");
        AiOverrideProfile.setActive(""); check(selection()==null,"switching profile leaves no cached authorization");
        setup(20); hand("Grizzly Bears"); AiOverrideProfile.setActive("");
        check(selection()==null,"new game default remains blocked after opt-in game");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null); MyRandom.setRandom(new Random(97011));
        choices(); nativeDraw(); nativeGoat(); nativeReturn(); nativeLife(); boundaries(); drawSafety(); legalityAndLifecycle();
        AiOverrideProfile.setActive(""); System.out.println("DONE "+passed+" Trading Post checks");
    }
}
