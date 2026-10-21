import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.*;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Original-script fixtures for the opt-in Gaze of Granite X choice. */
public class GazeOfGraniteTests {
    static Game game;
    static Player ai;
    static Card gaze;
    static SpellAbility spell;
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
    static void setup() {
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            LobbyPlayerAi lobby=new LobbyPlayerAi("Gaze-"+i,null); lobby.setAiProfile("Default");
            players.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Constructed); game=new Game(players,rules,new Match(rules,players,"Gaze of Granite fixtures"));
        game.setAge(GameStage.Play); ai=p(0); game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);
        for(int i=0;i<6;i++) { add("Swamp",ai,ZoneType.Battlefield); }
        for(int i=0;i<2;i++) { add("Forest",ai,ZoneType.Battlefield); }
        for(int i=0;i<20;i++) { add("Swamp",ai,ZoneType.Library); }
        gaze=add("Gaze of Granite",ai,ZoneType.Hand); spell=gaze.getFirstSpellAbility(); spell.setActivatingPlayer(ai);
        AiOverrideProfile.setActive("gaze-of-granite"); refresh();
    }
    static Card permanent(String name,int seat) { Card c=add(name,p(seat),ZoneType.Battlefield); refresh(); return c; }
    static boolean willing() { return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai,spell).willingToPlay(); }
    static void expect(int x,String label) {
        check(willing(),label+": API accepts"); check(spell.getXManaCostPaid()==x,label+": X="+x);
    }
    static SpellAbility selection() {
        List<SpellAbility> list=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();
        return list==null?null:list.stream().filter(sa->sa.getHostCard()==gaze).findFirst().orElse(null);
    }
    static void choices() {
        setup(); check(!willing() && spell.getXManaCostPaid()==0,"empty board holds and clears X");
        setup(); permanent("Grizzly Bears",1); expect(2,"single opposing permanent");
        setup(); permanent("Eager Cadet",1); permanent("Centaur Courser",2); expect(3,"best X takes both opposing values");
        setup(); permanent("Grizzly Bears",0); permanent("Grizzly Bears",1);
        check(!willing(),"equal friendly and enemy value holds");
        setup(); permanent("Grizzly Bears",0); permanent("Grizzly Bears",1); permanent("Grizzly Bears",2);
        expect(2,"two enemies outweigh one friendly permanent");
        setup(); permanent("Blood Artist",1); check(!willing(),"uncertain public death trigger holds");
        setup(); permanent("Prophetic Prism",1); expect(2,"artifact permanent is a valid target");
        setup(); permanent("Darksteel Myr",1); check(!willing(),"indestructible permanent is not counted");
        setup(); permanent("Forest",1); check(!willing(),"land-only board is not a target");
        setup(); permanent("Craw Wurm",1); check(!willing(),"unaffordable X holds");
    }
    static void nativeCast() {
        setup(); Card bear1=permanent("Grizzly Bears",1); Card bear2=permanent("Grizzly Bears",2);
        Card courier=permanent("Centaur Courser",0); Card land=ai.getLandsInPlay().iterator().next();
        spell=selection(); check(spell!=null,"native controller selects Gaze");
        check(spell.getXManaCostPaid()==2,"native controller retains X=2");
        check(ComputerUtil.playStack(spell,ai,game),"native casting pays costs");
        check(gaze.isInZone(ZoneType.Stack),"original spell on stack");
        game.getStack().resolveStack(); game.getAction().checkStateEffects(true); refresh();
        check(game.getCardState(bear1,null).isInZone(ZoneType.Graveyard),"opponent permanent at X=2 dies");
        check(game.getCardState(bear2,null).isInZone(ZoneType.Graveyard),"second opponent permanent at X=2 dies");
        check(game.getCardState(courier,null).isInZone(ZoneType.Battlefield),"friendly permanent above X survives");
        check(game.getCardState(land,null).isInZone(ZoneType.Battlefield),"lands survive");
    }
    static void boundaries() {
        setup(); permanent("Grizzly Bears",1);
        check(gaze.getRules().getAiHints().getRemAIDecks(),"original deck-building prohibition retained");
        for(String profile:List.of("","assault-suit","selvala-explorer-returned","trading-post")) {
            AiOverrideProfile.setActive(profile); check(!GazeOfGraniteAi.uses(spell) && selection()==null,"default/unrelated profile remains blocked: "+profile);
        }
        setup(); permanent("Grizzly Bears",1); AiOverrideProfile.setActive("gaze-of-granite");
        check(selection()!=null,"opt-in admission enables native play");
        for(Card card:ai.getLandsInPlay()) { card.setTapped(true); } refresh();
        check(selection()==null,"tapped lands cannot pay native mana");
    }
    static void continuousSources() {
        setup(); Card monument=permanent("Eldrazi Monument",0);
        Card force=permanent("Force of Savagery",0); Card force2=permanent("Force of Savagery",0);
        permanent("Air Elemental",1); permanent("Air Elemental",2);
        check(force.getNetToughness()==1 && force.isInZone(ZoneType.Battlefield),"Monument keeps zero-base-toughness creature alive");
        check(!willing() && spell.getXManaCostPaid()==0,"holds X that destroys a continuous protection/toughness source");
        check(selection()==null,"normal controller also holds collateral-death trap");
        check(!monument.isTapped() && force2.isInZone(ZoneType.Battlefield),"holding spends no resources or creatures");
        // Independent native consequence witness: deliberately cast the rejected line in this fixture only.
        spell.setXManaCostPaid(5);
        check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null) && !game.getStack().isEmpty(),"rejected line is natively payable for consequence test");
        game.getStack().resolveStack(); game.getAction().checkStateEffects(true); refresh();
        check(game.getCardState(monument,null).isInZone(ZoneType.Graveyard)
                && game.getCardState(force,null).isInZone(ZoneType.Graveyard)
                && game.getCardState(force2,null).isInZone(ZoneType.Graveyard),"native state actions confirm omitted collateral deaths");
        setup(); permanent("Glorious Anthem",0); permanent("Air Elemental",1);
        check(!willing(),"other dying continuous source also holds conservatively");
        setup(); permanent("Glorious Anthem",0); permanent("Grizzly Bears",1); expect(2,"continuous source above X does not suppress safe lower X");
        setup(); permanent("Darksteel Forge",0); permanent("Darksteel Relic",0); permanent("Grizzly Bears",1);
        expect(2,"surviving continuous source does not suppress ordinary wipe");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null); MyRandom.setRandom(new Random(97001));
        choices(); nativeCast(); boundaries(); continuousSources(); AiOverrideProfile.setActive(""); System.out.println("DONE "+passed+" Gaze of Granite checks");
    }
}
