import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.ai.ability.AttachAi;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.*;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Original-script fixtures exercise the normal AI decision entry points. */
public class AssaultSuitTests {
    static Game game;
    static Player ai;
    static Card suit;
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
    static Card permanent(String name,int seat) { Card c=add(name,p(seat),ZoneType.Battlefield); refresh(); return c; }
    static void setup(int lands) {
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            LobbyPlayerAi lobby=new LobbyPlayerAi("Suit-"+i,null); lobby.setAiProfile("Default");
            players.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Constructed); game=new Game(players,rules,new Match(rules,players,"Assault Suit fixtures"));
        game.setAge(GameStage.Play); ai=p(0); game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);
        for(int i=0;i<lands;i++) { add("Plains",ai,ZoneType.Battlefield); }
        suit=add("Assault Suit",ai,ZoneType.Battlefield);
        AiOverrideProfile.setActive("assault-suit"); refresh();
    }
    static SpellAbility equip() {
        return suit.getSpellAbilities().stream().filter(SpellAbility::isEquip).findFirst().orElseThrow();
    }
    static Card choose() {
        SpellAbility sa=equip(); sa.setActivatingPlayer(ai);
        List<Card> legal=new ArrayList<>(ai.getCreaturesInPlay());
        return AttachAi.attachGeneralAI(ai,sa,legal,false,suit,null);
    }
    static SpellAbility selection() {
        List<SpellAbility> list=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();
        return list==null?null:list.stream().filter(x->x.getHostCard()==suit && x.isEquip()).findFirst().orElse(null);
    }
    static void attachSuit(Card c) { suit.attachToEntity(c,null); refresh(); }
    static SpellAbility handoff() {
        Trigger t=suit.getTriggers().stream().filter(x->x.getMode()==TriggerType.Phase
                && "Opponent".equals(x.getParam("ValidPlayer"))).findFirst().orElseThrow();
        SpellAbility sa=t.ensureAbility(); sa.setActivatingPlayer(ai); return sa;
    }
    static boolean acceptsHandoff() { SpellAbility sa=handoff(); return SpellApiToAi.Converter.get(sa).doTrigger(ai,sa,false); }
    static void priority() {
        setup(3); Card mammoth=permanent("War Mammoth",0); permanent("Serra Angel",0);
        permanent("White Knight",0); permanent("Craw Wurm",0);
        check(choose()==mammoth,"largest trample outranks larger flyer, first striker and regular");
        setup(3); Card wilde=permanent("Stampeding Wildebeests",0); permanent("War Mammoth",0);
        check(choose()==wilde,"largest trample wins within the trample tier");
        setup(3); Card angel=permanent("Serra Angel",0); permanent("White Knight",0); permanent("Craw Wurm",0);
        check(choose()==angel,"largest flyer outranks first strike and regular");
        setup(3); Card knight=permanent("White Knight",0); permanent("Craw Wurm",0);
        check(choose()==knight,"largest first striker outranks larger regular");
        setup(3); Card wurm=permanent("Craw Wurm",0); permanent("Grizzly Bears",0);
        check(choose()==wurm,"largest regular creature");
        setup(3); permanent("Wall of Wood",0);
        check(choose()==null,"do not equip a creature that cannot attack");
        setup(3); permanent("Wall of Wood",0); Card bears=permanent("Grizzly Bears",0);
        check(choose()==bears,"skip unusable creature when a legal attacker exists");
        setup(3); permanent("Ornithopter",0);
        check(choose()==null,"do not equip a zero-power flyer");
        setup(3); Card own=permanent("Grizzly Bears",0); permanent("Craw Wurm",1);
        check(choose()==own,"only own legal creatures are considered");
        setup(3); Card priority=permanent("War Mammoth",0); permanent("Serra Angel",0);
        SpellAbility selected=selection();
        check(selected!=null && selected.getTargetCard()==priority,"normal controller selects the priority equip target");
    }
    static void handoffGate() {
        setup(3); Card c=permanent("Grizzly Bears",0); attachSuit(c);
        check(acceptsHandoff(),"three available mana accepts the handoff");
        setup(2); c=permanent("Grizzly Bears",0); attachSuit(c);
        check(!acceptsHandoff(),"two available mana declines the handoff");
        setup(3); c=permanent("Grizzly Bears",0); attachSuit(c);
        for(Card land:ai.getLandsInPlay()) { land.setTapped(true); } refresh();
        check(!acceptsHandoff(),"tapped sources are not available mana");
        setup(2); c=permanent("Llanowar Elves",0); attachSuit(c);
        check(!acceptsHandoff(),"equipped creature's own mana does not fund the re-equip");
        setup(3); c=permanent("Llanowar Elves",0); attachSuit(c);
        check(acceptsHandoff(),"three lands plus the equipped creature leave only three after the handoff");
    }
    static void defaults() {
        setup(3); check(AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(suit),"profile admits the card during play");
        check("AssaultSuit".equals(AiOverrideProfile.get().getAiLogic(suit)),"profile selects the AssaultSuit logic");
        check(ComputerUtilCard.isCardRemAIDeck(suit),"original deck-building warning retained");
        AiOverrideProfile.setActive("");
        check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(suit),"inactive profile keeps the play prohibition");
        setup(2); Card c=permanent("Grizzly Bears",0); attachSuit(c);
        AiOverrideProfile.setActive("");
        check(acceptsHandoff(),"default AI accepts the handoff regardless of mana");
        setup(2); c=permanent("Grizzly Bears",0); attachSuit(c);
        AiOverrideProfile.setActive("biomass-mutation");
        check(acceptsHandoff(),"unrelated profile leaves the handoff decision unchanged");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null); MyRandom.setRandom(new Random(96801));
        priority(); handoffGate(); defaults(); System.out.println("DONE "+passed+" Assault Suit checks");
    }
}
