import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.ability.AbilityUtils;
import forge.game.card.*;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Original-script decisions and native payment, death, targeting, draw and loss of life. */
public class CorpseAugurTests {
    static Game game;
    static Player ai;
    static Card augur;
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
    static void setup(int life,int library,int... graves) {
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            LobbyPlayerAi lobby=new LobbyPlayerAi("Augur-"+i,null); lobby.setAiProfile("Default");
            players.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Constructed); game=new Game(players,rules,new Match(rules,players,"Corpse Augur fixtures"));
        game.setAge(GameStage.Play); ai=p(0); ai.setLife(life,null); game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);
        for(int i=0;i<4;i++) { add("Swamp",ai,ZoneType.Battlefield); }
        for(int i=0;i<library;i++) { add("Swamp",ai,ZoneType.Library); }
        for(int seat=0;seat<4;seat++) {
            for(int i=0;i<graves[seat];i++) { add("Grizzly Bears",p(seat),ZoneType.Graveyard); }
        }
        augur=add("Corpse Augur",ai,ZoneType.Hand); AiOverrideProfile.setActive("corpse-augur"); refresh();
    }
    static SpellAbility trigger() {
        SpellAbility sa=augur.getTriggers().get(0).ensureAbility().copy(ai);
        sa.setTrigger(augur.getTriggers().get(0)); sa.setActivatingPlayer(ai); return sa;
    }
    static Player chosen(SpellAbility sa) {
        check(SpellApiToAi.Converter.get(sa).doTrigger(ai,sa,true),"mandatory native trigger is accepted");
        check(sa.isTargetNumberValid(),"native target count valid"); return sa.getTargets().getFirstTargetedPlayer();
    }
    static void expect(int seat,String label) { check(chosen(trigger())==p(seat),label); }
    static SpellAbility selection() {
        List<SpellAbility> list=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();
        return list==null?null:list.stream().filter(sa->sa.getHostCard()==augur).findFirst().orElse(null);
    }
    static void choices() {
        setup(40,20,2,7,4,9); expect(3,"largest safe graveyard across all opponents");
        setup(12,20,8,7,4,9); expect(1,"five-life reserve includes equality");
        setup(11,20,8,7,4,9); expect(2,"reject life reserve exceeded by one");
        setup(40,8,8,7,4,9); expect(1,"one-card library reserve includes equality");
        setup(40,7,8,7,4,9); expect(2,"do not draw whole library");
        setup(5,20,0,7,4,9); expect(0,"empty graveyard is a valid safe choice");
        setup(4,20,3,7,1,9); expect(2,"no reserve-safe target: choose smallest unavoidable X");
        setup(1,20,3,7,1,9); expect(2,"mandatory trigger does not illegally decline unavoidable lethal");
        setup(40,1,3,7,1,9); expect(2,"every target consumes library: smallest mandatory draw");
        setup(40,0,0,7,1,9); expect(0,"empty library with zero X draws nothing");
        setup(40,20,4,4,4,4); expect(0,"ties use stable player order");
        setup(40,20,2,7,4,9); ai.setTeam(0); p(3).setTeam(0); expect(3,"allied graveyard only sizes our draw; ally does not draw");
        setup(40,20,2,7,4,9); add("Leyline of Sanctity",p(3),ZoneType.Battlefield); refresh();
        expect(1,"native player hexproof excludes largest opponent graveyard");
        setup(40,20,2,7,4,9); add("Ivory Mask",p(3),ZoneType.Battlefield); refresh(); expect(1,"native shroud excludes target");
        setup(40,20,2,7,4,9); for(int i=0;i<4;i++) { add("Ivory Mask",p(i),ZoneType.Battlefield); } refresh();
        SpellAbility none=trigger(); check(!SpellApiToAi.Converter.get(none).doTrigger(ai,none,true),"no legal player cannot manufacture a target");
        check(none.getTargets().isEmpty(),"failed targeting leaves no stale target");
        setup(40,20,2,7,4,9); for(int i=0;i<12;i++) { add("Swamp",p(0),ZoneType.Graveyard); } expect(3,"noncreature graveyard cards do not count");
        SpellAbility changing=trigger(); check(chosen(changing)==p(3),"initial target");
        ai.setLife(10,null); check(chosen(changing)==p(2),"re-evaluation replaces stale target after life changes");
        check(changing.getTargets().size()==1,"repeated evaluation never accumulates targets");
        for(int i=0;i<5;i++) { check(chosen(changing)==p(2),"repeat decision stable "+i); }
        check(game.getStack().isEmpty(),"target preview creates no stack action");
        check(ai.getLife()==10 && ai.getCardsIn(ZoneType.Library).size()==20,"preview leaves life and library intact");
    }
    static void nativeDeath(int life,int library,int[] graves,int expectedSeat,int expectedX,String label) {
        setup(life,library,graves);
        SpellAbility cast=selection(); check(cast!=null,label+": native creature selection");
        check(ComputerUtil.playStack(cast,ai,game),label+": native payment enters stack");
        check(ai.getLandsInPlay().stream().filter(Card::isTapped).count()==4,label+": four Swamps pay 3B");
        game.getStack().resolveStack(); refresh(); augur=game.getCardState(augur,null);
        check(augur.isInZone(ZoneType.Battlefield),label+": creature resolves");
        // Resolve the original removal script so native LTB snapshots and zone batching are used.
        SpellAbility removal=add("Murder",p(1),ZoneType.Hand).getFirstSpellAbility();
        removal.setActivatingPlayer(p(1)); check(removal.canTarget(augur),label+": native removal target legal");
        removal.getTargets().add(augur); AbilityUtils.resolve(removal);
        game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
        SpellAbility death=game.getStack().peekAbility();
        check(death!=null && death.getTargets().getFirstTargetedPlayer()==p(expectedSeat),label+": actual native death-trigger target");
        check(AbilityUtils.calculateAmount(death.getHostCard(),"X",death)==expectedX,label+": X includes current graveyard contents");
        int hand=ai.getCardsIn(ZoneType.Hand).size(), otherLife=p(expectedSeat).getLife();
        int otherHand=p(expectedSeat).getCardsIn(ZoneType.Hand).size();
        game.getStack().resolveStack();
        check(ai.getLife()==life-expectedX,label+": controller loses exact native X life");
        check(ai.getCardsIn(ZoneType.Hand).size()==hand+expectedX && ai.getCardsIn(ZoneType.Library).size()==library-expectedX,label+": controller draws exact native X");
        if(expectedSeat!=0) { check(p(expectedSeat).getLife()==otherLife && p(expectedSeat).getCardsIn(ZoneType.Hand).size()==otherHand,label+": targeted opponent neither draws nor loses life"); }
        check(game.getStack().isEmpty(),label+": native trigger completes");
    }
    static void boundaries() {
        setup(40,20,2,7,4,9); check(selection()!=null,"opt-in body admitted");
        check(augur.getRules().getAiHints().getRemAIDecks(),"original deck-building prohibition remains");
        for(String profile:List.of("","darksteel-mutation","marshals-anthem","ahoy-mateys")) {
            AiOverrideProfile.setActive(profile); check(selection()==null,"unrelated/default admission stays blocked: "+profile);
            check(!CorpseAugurAi.uses(trigger()),"unrelated/default trigger hook inactive: "+profile);
        }
        AiOverrideProfile.setActive("corpse-augur");
        for(Card land:ai.getLandsInPlay()) { land.setTapped(true); } check(selection()==null,"native unavailable mana prevents casting");
        setup(40,20,2,7,4,9); for(Card land:new ArrayList<>(ai.getLandsInPlay())) { ai.getZone(ZoneType.Battlefield).remove(land); add("Plains",ai,ZoneType.Battlefield); }
        refresh(); check(selection()==null,"native color payment requires black mana");
        setup(40,20,2,7,4,9); check(!CorpseAugurAi.uses(augur.getFirstSpellAbility()),"hook does not replace creature casting");
        check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(add("Darksteel Mutation",ai,ZoneType.Hand)),"profile does not unlock other cards");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null); MyRandom.setRandom(new Random(96451));
        choices();
        nativeDeath(40,20,new int[]{2,7,4,9},3,9,"opponent graveyard");
        nativeDeath(10,20,new int[]{4,7,4,9},0,5,"dead Augur itself counts");
        nativeDeath(5,20,new int[]{2,0,4,9},1,0,"zero draw avoids self-lethal");
        nativeDeath(6,20,new int[]{2,2,4,9},1,2,"mandatory minimum fallback");
        nativeDeath(40,5,new int[]{2,7,4,9},2,4,"one library card remains");
        boundaries(); AiOverrideProfile.setActive(""); System.out.println("DONE "+passed+" Corpse Augur checks");
    }
}
