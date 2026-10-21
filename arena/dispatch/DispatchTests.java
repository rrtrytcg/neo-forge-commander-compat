import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.*;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Four-player native decision, payment, targeting and original Tap/Metalcraft-exile fixtures. */
public class DispatchTests {
    static Game game;
    static Player ai;
    static Card dispatch;
    static SpellAbility spell;
    static int passed;
    static Player opp(int index) { return game.getPlayers().get(index); }
    static void check(boolean result,String label) {
        if(!result) { throw new AssertionError(label); }
        passed++; System.out.println("PASS "+label);
    }
    static Card add(String name,Player owner,ZoneType zone) {
        Card card=Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name),owner);
        card.setGameTimestamp(game.getNextTimestamp()); owner.getZone(zone).add(card); card.setSickness(false); return card;
    }
    static void refresh() { game.getAction().checkStaticAbilities(); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState(); }
    static Card creature(String name,Player owner) { Card card=add(name,owner,ZoneType.Battlefield); refresh(); return card; }
    static void setup(int artifacts,int plains) {
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            LobbyPlayerAi lobby=new LobbyPlayerAi("Dispatch-"+i,null); lobby.setAiProfile("Default");
            players.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Constructed);
        game=new Game(players,rules,new Match(rules,players,"Dispatch native fixtures"));
        game.setAge(GameStage.Play); ai=opp(0); game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);
        for(int i=0;i<plains;i++) { add("Plains",ai,ZoneType.Battlefield); }
        for(int i=0;i<artifacts;i++) { add("Darksteel Relic",ai,ZoneType.Battlefield); }
        for(int i=0;i<12;i++) { add("Plains",ai,ZoneType.Library); }
        dispatch=add("Dispatch",ai,ZoneType.Hand); spell=dispatch.getFirstSpellAbility(); spell.setActivatingPlayer(ai);
        AiOverrideProfile.setActive("dispatch"); refresh();
    }
    static boolean willing() { return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai,spell).willingToPlay(); }
    static boolean selectsDispatch() {
        List<SpellAbility> selected=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();
        return selected!=null && selected.stream().anyMatch(sa->sa.getHostCard()==dispatch);
    }
    static boolean targets(Card card) { return spell.getTargets().getTargetCards().contains(card); }
    static void resolve() {
        game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
        int guard=0;
        while(!game.getStack().isEmpty() && guard++<12) {
            game.getStack().resolveStack(); game.getAction().checkStateEffects(true);
            game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
        }
        check(game.getStack().isEmpty(),"native stack finishes"); refresh();
    }
    static void thresholdsAndSelection() {
        for(int artifacts=0;artifacts<5;artifacts++) {
            setup(artifacts,1); Card target=creature("Serra Angel",opp(1));
            check(willing()==(artifacts>=3),"metalcraft: "+artifacts+" own artifacts");
            if(artifacts>=3) { check(targets(target),"metalcraft: selected opposing creature"); }
        }
        setup(2,1); creature("Serra Angel",opp(1)); add("Darksteel Relic",opp(1),ZoneType.Battlefield); refresh();
        check(!willing(),"metalcraft: opponent artifacts do not count");
        creature("Ornithopter",ai); check(willing(),"metalcraft: own artifact creature counts");
        ai.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isArtifact).findFirst().orElseThrow().setPhasedOut(ai);
        check(!willing() && spell.getTargets().isEmpty(),"metalcraft: phased-out artifact does not count and stale target is cleared");
        setup(2,0); add("Ancient Den",ai,ZoneType.Battlefield); refresh(); Card target=creature("Serra Angel",opp(1));
        check(willing() && targets(target),"metalcraft: native artifact land and white mana count");
        setup(3,1); creature("Serra Angel",ai);
        check(!willing() && spell.getTargets().isEmpty(),"targeting: never remove own creature");
        setup(3,1); creature("Eager Cadet",opp(1)); Card large=creature("Darksteel Colossus",opp(3));
        check(willing() && targets(large),"targeting: native evaluation considers every opponent");
        setup(3,1); target=creature("Serra Angel",opp(2)); target.setTapped(true);
        check(willing() && targets(target),"targeting: tapped creature remains a removal target");
        setup(3,1); target=creature("Slippery Bogle",opp(1));
        check(!spell.canTarget(target) && !willing(),"legality: native hexproof");
        setup(3,1); target=creature("Blastoderm",opp(1));
        check(!spell.canTarget(target) && !willing(),"legality: native shroud");
        setup(3,1); target=creature("Black Knight",opp(1));
        check(!spell.canTarget(target) && !willing(),"legality: native protection from white");
        setup(3,0); creature("Serra Angel",opp(1)); check(!willing(),"payment: no white mana holds");
        setup(3,1); creature("Serra Angel",opp(1)); ai.getLandsInPlay().get(0).setTapped(true);
        check(!willing(),"payment: tapped mana holds");
        setup(3,1); creature("Serra Angel",opp(1)); creature("Thalia, Guardian of Thraben",opp(2));
        check(!willing(),"payment: native noncreature spell tax prevents unaffordable cast");
        add("Plains",ai,ZoneType.Battlefield); refresh();
        check(willing(),"payment: native tax is affordable with extra mana");
    }
    static void combat() {
        setup(3,1); Card attacker=creature("Grizzly Bears",opp(1)); attacker.setTapped(true);
        creature("Darksteel Colossus",opp(2)); game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS,opp(1));
        Combat combat=new Combat(opp(1)); game.getPhaseHandler().setCombat(combat); combat.addAttacker(attacker,ai); refresh();
        check(willing() && targets(attacker),"combat: attacking us outranks larger idle creature");
        check(selectsDispatch(),"combat: normal controller selects instant after attackers declared");
        setup(3,1); attacker=creature("Grizzly Bears",ai); Card blocker=creature("Eager Cadet",opp(1));
        creature("Darksteel Colossus",opp(2)); game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS,ai);
        combat=new Combat(ai); game.getPhaseHandler().setCombat(combat); combat.addAttacker(attacker,opp(1)); combat.addBlocker(attacker,blocker); refresh();
        check(willing() && targets(blocker),"combat: actual blocker of our attacker outranks idle creature");
        check(selectsDispatch(),"combat: normal controller selects removal after blockers declared");
        setup(3,1); attacker=creature("Eager Cadet",opp(1)); Card idle=creature("Darksteel Colossus",opp(2));
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS,opp(1));
        combat=new Combat(opp(1)); game.getPhaseHandler().setCombat(combat); combat.addAttacker(attacker,opp(3)); refresh();
        check(willing() && targets(idle),"combat: attacking another opponent gets no special priority");
    }
    static void nativeResolution() {
        for(String name:List.of("Serra Angel","Darksteel Colossus")) {
            setup(3,1); Card target=creature(name,opp(1)); target.setTapped(true);
            check(selectsDispatch(),"native: controller selects removal for tapped "+name);
            check(willing() && targets(target) && ComputerUtil.playStack(spell,ai,game),"native: pays and stacks against "+name);
            check(ai.getLandsInPlay().get(0).isTapped(),"native: Plains pays W"); resolve();
            check(opp(1).getCardsIn(ZoneType.Exile).stream().anyMatch(c->c.getId()==target.getId()),"native: original subability exiles "+name);
            check(ai.getCardsIn(ZoneType.Graveyard).stream().anyMatch(c->c.getName().equals("Dispatch")),"native: Dispatch ends in graveyard");
        }
        setup(3,1); Card target=creature("Serra Angel",opp(1));
        check(willing() && ComputerUtil.playStack(spell,ai,game),"resolution change: cast with metalcraft");
        Card relic=ai.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isArtifact).findFirst().orElseThrow();
        game.getAction().moveToGraveyard(relic,null); refresh(); resolve();
        int targetId=target.getId();
        check(opp(1).getCreaturesInPlay().stream().anyMatch(c->c.getId()==targetId && c.isTapped()),"resolution change: lost metalcraft only taps, no exile");
        setup(3,1); target=creature("Serra Angel",opp(1));
        check(willing() && ComputerUtil.playStack(spell,ai,game),"target change: cast with legal target");
        game.getAction().moveToHand(target,null); refresh(); resolve();
        check(opp(1).getCardsIn(ZoneType.Hand).stream().anyMatch(c->c.getName().equals("Serra Angel")),"target change: departed target is not exiled from hand");
        for(int mana:new int[]{1,3}) {
            setup(3,mana); target=creature("Tolarian Terror",opp(1));
            check(spell.canTarget(target),"ward: target remains legally targetable");
            if(mana==1) {
                check(!willing() && !selectsDispatch(),"ward: native cost prediction holds when tax is unaffordable");
                continue;
            }
            check(willing() && ComputerUtil.playStack(spell,ai,game),"ward: legal target can enter stack with "+mana+" mana"); resolve();
            boolean exiled=opp(1).getCardsIn(ZoneType.Exile).stream().anyMatch(c->c.getName().equals("Tolarian Terror"));
            check(exiled==(mana==3),"ward: native payment/counter result with "+mana+" mana");
        }
    }
    static void isolation() {
        for(String profile:List.of("","oreskos-explorer","mirror-entity","adaptive-enchantment","ahoy-mateys","decree-of-pain","curse-of-the-swine")) {
            setup(3,1); creature("Serra Angel",opp(1)); AiOverrideProfile.setActive(profile);
            check(!DispatchAi.uses(spell) && !AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(dispatch) && !selectsDispatch(),"isolation: no Dispatch admission/policy in '"+profile+"'");
        }
        setup(3,1); Card target=creature("Serra Angel",opp(1)); target.setTapped(true); AiOverrideProfile.setActive("");
        check(!willing(),"default tap AI still rejects tapped creature in own second main");
        AiOverrideProfile.setActive("dispatch"); check(willing(),"opt-in removal accepts same tapped creature");
        check(ComputerUtilCard.isCardRemAIDeck(dispatch),"original deck-building prohibition remains");
        check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(add("Oreskos Explorer",ai,ZoneType.Hand)),"profile does not admit unrelated cards");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null); MyRandom.setRandom(new Random(7401));
        thresholdsAndSelection(); combat(); nativeResolution(); isolation(); AiOverrideProfile.setActive("");
        System.out.println("DONE "+passed+" Dispatch checks");
    }
}
