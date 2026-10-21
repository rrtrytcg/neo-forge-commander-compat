import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.ability.*;
import forge.game.card.*;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import com.google.common.collect.HashMultiset;
import java.util.*;

class DecreeOfPainTests {
    static Game game;
    static Player ai;
    static Card decree;
    static SpellAbility cast, cycle;
    static int passed;
    static void check(boolean result, String label) {
        if (!result) { throw new AssertionError(label); }
        passed++; System.out.println("PASS " + label);
    }
    static Card add(String name, Player owner, ZoneType zone) {
        Card card=Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name),owner);
        card.setGameTimestamp(game.getNextTimestamp()); owner.getZone(zone).add(card); return card;
    }
    static Player opp(int i) { return game.getPlayers().get(i); }
    static void library(int count) {
        ai.getZone(ZoneType.Library).removeAllCards(true);
        for(int i=0;i<count;i++) { add("Swamp",ai,ZoneType.Library); }
    }
    static void refresh() {
        game.getAction().checkStaticAbilities(); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
    }
    static void setup() {
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            LobbyPlayerAi lobby=new LobbyPlayerAi("Decree-"+i,null); lobby.setAiProfile("Default");
            players.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Constructed);
        game=new Game(players,rules,new Match(rules,players,"Decree native fixtures")); game.setAge(GameStage.Play);
        ai=opp(0); game.getPhaseHandler().devModeSet(PhaseType.MAIN1,ai);
        library(32);
        for(int i=0;i<8;i++) { add("Swamp",ai,ZoneType.Battlefield); }
        decree=add("Decree of Pain",ai,ZoneType.Hand);
        cast=decree.getFirstSpellAbility(); cast.setActivatingPlayer(ai);
        cycle=decree.getAllPossibleAbilities(ai,false).stream().filter(SpellAbility::isCycling).findFirst().orElseThrow();
        cycle.setActivatingPlayer(ai); AiOverrideProfile.setActive("decree-of-pain"); refresh();
    }
    static void creatures(Player p,String name,int count) {
        for(int i=0;i<count;i++) { add(name,p,ZoneType.Battlefield); } refresh();
    }
    static boolean willing(SpellAbility sa) {
        return SpellApiToAi.Converter.get(sa).canPlayWithSubs(ai,sa).willingToPlay();
    }
    static void shield(Card c) {
        var counters=HashMultiset.<CounterType>create(); counters.add(CounterEnumType.SHIELD); c.setCounters(counters); refresh();
    }
    static void casting() {
        setup(); creatures(opp(1),"Centaur Courser",1);
        check(!willing(cast),"cast: one opposing casualty holds");
        creatures(opp(2),"Centaur Courser",1);
        check(willing(cast),"cast: two opposing casualties across players pass");
        creatures(ai,"Centaur Courser",1);
        check(!willing(cast),"cast: one more opposing casualty is insufficient");
        creatures(opp(3),"Centaur Courser",1);
        check(willing(cast),"cast: exactly two more opposing casualties pass");
        library(4); check(!willing(cast),"cast: enough for all creatures without spare still holds");
        library(5); check(willing(cast),"cast: all creatures plus one passes");
        Card immortal=add("Darksteel Myr",opp(1),ZoneType.Battlefield); refresh();
        check(DecreeOfPainAi.casualty(immortal,cast,false)==0,"cast: indestructible is not a casualty");
        check(!willing(cast),"cast: survivors still increase conservative library bound");
        library(6); check(willing(cast),"cast: bound includes indestructible survivor");
        Card protectedCreature=add("Centaur Courser",opp(1),ZoneType.Battlefield); shield(protectedCreature);
        check(DecreeOfPainAi.casualty(protectedCreature,cast,false)==0,"cast: shield counter protects creature");
        library(7); check(willing(cast),"cast: shield survivor counted for draw bound, not advantage");
        Card token=add("Centaur Courser",opp(1),ZoneType.Battlefield);
        token.setGamePieceType(forge.card.GamePieceType.TOKEN); refresh();
        library(7); check(!willing(cast),"cast: token also contributes to upper bound");
        library(8); check(willing(cast),"cast: token-inclusive bound plus spare passes");
        setup(); creatures(opp(1),"Centaur Courser",2);
        add("Alhammarret's Archive",ai,ZoneType.Battlefield); refresh();
        check(!willing(cast),"cast: uncertain draw multiplier holds");
        setup(); creatures(opp(1),"Centaur Courser",2);
        add("Harvester of Souls",ai,ZoneType.Battlefield); creatures(opp(2),"Centaur Courser",2);
        check(!willing(cast),"cast: additional triggered draws do not bypass decking gate");
        setup(); creatures(opp(1),"Centaur Courser",2);
        Card extraDraw=add("Sol Ring",ai,ZoneType.Battlefield);
        extraDraw.setSVar("ExtraRepeat","DB$ RepeatEach | RepeatPlayers$ You | RepeatSubAbility$ ExtraDraw");
        extraDraw.setSVar("ExtraDraw","DB$ Draw | Defined$ Remembered | NumCards$ 2");
        extraDraw.addTrigger(forge.game.trigger.TriggerHandler.parseTrigger(
                "Mode$ ChangesZone | Origin$ Battlefield | Destination$ Graveyard | ValidCard$ Creature | TriggerZones$ Battlefield | Execute$ ExtraRepeat",
                extraDraw,true)); refresh();
        check(!willing(cast),"cast: native nested trigger ability tree is checked for extra draws");
        setup(); creatures(opp(1),"Centaur Courser",2);
        Card withArmor=opp(1).getCreaturesInPlay().getFirst();
        Card armor=add("Hyena Umbra",opp(1),ZoneType.Battlefield); armor.attachToEntity(withArmor,null); refresh();
        check(!willing(cast),"cast: unmodelled destruction replacement holds");
    }
    static void cycling() {
        setup(); creatures(opp(1),"Grizzly Bears",1);
        check(!willing(cycle),"cycle: one kill holds");
        creatures(opp(2),"Grizzly Bears",1);
        check(willing(cycle),"cycle: two kills across opponents pass in Main1");
        check(!willing(cast),"preference: payable cycling beats qualifying cast");
        creatures(ai,"Grizzly Bears",1);
        check(!willing(cycle),"cycle: one more opposing death is insufficient");
        creatures(opp(3),"Grizzly Bears",1);
        check(willing(cycle),"cycle: two more opposing deaths pass");
        library(1); check(!willing(cycle),"cycle: last library card is reserved");
        library(2); check(willing(cycle),"cycle: one draw plus spare passes");
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS,opp(1));
        check(willing(cycle),"cycle: native instant-speed decision on opponent turn");
        check(((PlayerControllerAi)ai.getController()).getAi().canPlaySa(cast)!=AiPlayDecision.WillPlay,
                "cast: native sorcery timing is preserved");
        setup();
        Card indestructible=add("Darksteel Myr",opp(1),ZoneType.Battlefield); refresh();
        check(DecreeOfPainAi.casualty(indestructible,cycle,true)==1,"cycle: zero toughness kills indestructible");
        Card shielded=add("Grizzly Bears",opp(1),ZoneType.Battlefield); shield(shielded);
        check(DecreeOfPainAi.casualty(shielded,cycle,true)==1,"cycle: zero toughness bypasses shield counter");
        Card marked=add("Centaur Courser",opp(2),ZoneType.Battlefield); marked.setDamage(1); refresh();
        check(DecreeOfPainAi.casualty(marked,cycle,true)==1,"cycle: marked damage kills positive toughness survivor");
        shield(marked);
        check(DecreeOfPainAi.casualty(marked,cycle,true)==1,"cycle: shield counter cannot prevent state-based lethal marked-damage death");
        Card buffed=add("Grizzly Bears",opp(2),ZoneType.Battlefield);
        add("Glorious Anthem",opp(2),ZoneType.Battlefield); refresh();
        check(DecreeOfPainAi.casualty(buffed,cycle,true)==0,"cycle: native current toughness includes anthem");
        setup(); creatures(opp(1),"Grizzly Bears",2);
        add("Alhammarret's Archive",ai,ZoneType.Battlefield); refresh();
        check(!willing(cycle),"cycle: uncertain draw multiplier holds");
        setup(); creatures(opp(1),"Grizzly Bears",2);
        add("Death Baron",opp(2),ZoneType.Battlefield); refresh();
        check(!willing(cycle),"cycle: dying static ability source makes cascading deaths uncertain");
        setup(); creatures(opp(1),"Grizzly Bears",2);
        for(Card land:ai.getLandsInPlay()) { land.setTapped(true); }
        check(!willing(cycle),"cycle: native cost feasibility rejects tapped mana");
        // A cycling prohibition should allow the full spell rather than losing both choices.
        setup(); creatures(opp(1),"Grizzly Bears",2);
        add("Stabilizer",opp(2),ZoneType.Battlefield); refresh();
        check(((PlayerControllerAi)ai.getController()).getAi().canPlaySa(cycle)!=AiPlayDecision.WillPlay && willing(cast),
                "preference: prohibited cycling does not suppress legal cast");
        setup(); creatures(opp(1),"Grizzly Bears",2);
        add("Suppression Field",opp(1),ZoneType.Battlefield); add("Suppression Field",opp(2),ZoneType.Battlefield); refresh();
        check(!ComputerUtilCost.canPayCost(cycle,ai,false) && willing(cast),
                "preference: native activation taxes can make cycling unpayable without suppressing cast");
        setup(); creatures(opp(1),"Centaur Courser",2);
        Card phased=opp(1).getCreaturesInPlay().getFirst(); phased.setPhasedOut(opp(1)); refresh();
        check(!willing(cast) && DecreeOfPainAi.casualty(phased,cast,false)==0,
                "cast: phased-out creature is not a casualty");
    }
    static void resolution() {
        setup(); creatures(opp(1),"Centaur Courser",2);
        Card immortal=add("Darksteel Myr",opp(2),ZoneType.Battlefield);
        Card shielded=add("Centaur Courser",opp(2),ZoneType.Battlefield); shield(shielded);
        library(5); int hand=ai.getCardsIn(ZoneType.Hand).size();
        check(willing(cast),"native cast: policy permits protected-survivor board");
        check(ComputerUtil.playStack(cast,ai,game),"native cast: engine pays 6BB and puts spell on stack");
        check(ai.getLandsInPlay().stream().filter(Card::isTapped).count()==8,"native cast: exactly eight Swamps tapped");
        game.getStack().resolveStack();
        check(opp(1).getCreaturesInPlay().isEmpty(),"native cast: opposing creatures destroyed");
        check(immortal.isInZone(ZoneType.Battlefield) && shielded.isInZone(ZoneType.Battlefield),
                "native cast: indestructible and shielded creatures survive");
        check(shielded.getCounters(CounterEnumType.SHIELD)==0,"native cast: native shield replacement consumes counter");
        check(ai.getCardsIn(ZoneType.Hand).size()==hand-1+2 && ai.getCardsIn(ZoneType.Library).size()==3,
                "native cast: draw counts actual destruction, not all battlefield creatures");
        check(!cast.getHostCard().getRemembered().iterator().hasNext(),"native cast: script cleanup preserved");
        setup(); creatures(opp(1),"Grizzly Bears",2); library(2);
        check(willing(cycle),"native cycle: policy permits exactly draw-plus-spare library");
        check(ComputerUtil.playStack(cycle,ai,game),"native cycle: engine pays mana and discards source");
        check(decree.isInZone(ZoneType.Graveyard),"native cycle: Decree discarded as native cost");
        check(ai.getLandsInPlay().stream().filter(Card::isTapped).count()==5,"native cycle: exactly five Swamps tapped");
        game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
        check(game.getStack().peekAbility().getApi()==ApiType.PumpAll,"native cycle: -2/-2 trigger is above draw");
        game.getStack().resolveStack(); game.getAction().checkStateEffects(true);
        check(opp(1).getCreaturesInPlay().isEmpty() && ai.getCardsIn(ZoneType.Library).size()==2,
                "native cycle: creatures die before cycling draw");
        game.getStack().resolveStack();
        check(ai.getCardsIn(ZoneType.Library).size()==1 && ai.getCardsIn(ZoneType.Hand).size()==1,
                "native cycle: one draw resolves and leaves the spare");
        setup(); library(2);
        Card fragileImmortal=add("Darksteel Myr",opp(1),ZoneType.Battlefield);
        Card fragileShielded=add("Grizzly Bears",opp(1),ZoneType.Battlefield); shield(fragileShielded);
        Card marked=add("Centaur Courser",opp(2),ZoneType.Battlefield); marked.setDamage(1);
        Card survivingImmortal=add("Darksteel Sentinel",opp(2),ZoneType.Battlefield); survivingImmortal.setDamage(1);
        Card survivingShielded=add("Centaur Courser",opp(3),ZoneType.Battlefield); survivingShielded.setDamage(1); shield(survivingShielded);
        check(willing(cycle),"native cycle: mixed protection board qualifies");
        check(ComputerUtil.playStack(cycle,ai,game),"native cycle: mixed protection activation pays normally");
        game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
        game.getStack().resolveStack(); game.getAction().checkStateEffects(true);
        check(fragileImmortal.isInZone(ZoneType.Graveyard) && fragileShielded.isInZone(ZoneType.Graveyard),
                "native cycle: zero-toughness deaths bypass indestructible and shield");
        check(marked.isInZone(ZoneType.Graveyard),"native cycle: native state actions kill marked-damage survivor");
        check(survivingImmortal.isInZone(ZoneType.Battlefield) && survivingShielded.isInZone(ZoneType.Graveyard),
                "native cycle: indestructible survives marked damage but shield counter cannot prevent state action");
        game.getStack().resolveStack();
        check(ai.getCardsIn(ZoneType.Library).size()==1,"native cycle: mixed board leaves spare library card");
    }
    static void isolation() {
        setup(); creatures(opp(1),"Centaur Courser",2);
        long rng=MyRandom.callCount();
        check(willing(cast),"profile: opt-in decision active");
        check(MyRandom.callCount()==rng,"profile: policy evaluation adds no RNG");
        for(String profile:List.of("","abzan-armor","adaptive-enchantment","ahoy-mateys")) {
            AiOverrideProfile.setActive(profile);
            check(!DecreeOfPainAi.uses(cast) && !AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(decree),
                    "profile: no Decree override in '"+profile+"'");
        }
        AiOverrideProfile.setActive("decree-of-pain");
        check(!AiOverrideProfile.get().supportsLandManaAuras(),"profile: no Adaptive family enabled");
        check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(add("Rakdos Charm",ai,ZoneType.Hand)),
                "profile: no Ahoy admission enabled");
        setup(); creatures(opp(1),"Grizzly Bears",2);
        List<SpellAbility> selected=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();
        check(selected!=null && !selected.isEmpty() && selected.get(0).isCycling(),
                "native selection: normal AI chooses cycling ahead of full spell");
        setup(); creatures(opp(1),"Centaur Courser",2);
        selected=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();
        check(selected!=null && !selected.isEmpty() && selected.get(0).getApi()==ApiType.DestroyAll,
                "native selection: normal AI chooses full spell when cycling cannot meet threshold");
        AiOverrideProfile.setActive("");
        selected=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();
        check(selected==null || selected.isEmpty(),"native selection: default retains original in-game admission blocker");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null); MyRandom.setRandom(new Random(7351));
        casting(); cycling(); resolution(); isolation(); AiOverrideProfile.setActive("");
        System.out.println("DONE "+passed+" Decree checks");
    }
}
