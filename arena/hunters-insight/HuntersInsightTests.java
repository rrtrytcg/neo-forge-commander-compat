import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.*;

/** Original-script combat/draw fixtures with normal AI selection and native payment/damage. */
public class HuntersInsightTests extends TradingPostTests {
    static Card insight, attacker;
    static SpellAbility spell;
    static Combat combat;
    static void start(boolean declared) {
        setup(20); AiOverrideProfile.setActive("hunters-insight");
        for(int i=0;i<3;i++) {permanent("Forest",0);} p(1).setLife(40,null);
        insight=hand("Hunter's Insight"); spell=insight.getFirstSpellAbility(); spell.setActivatingPlayer(ai);
        attacker=permanent("Craw Wurm",0);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS,ai);
        combat=new Combat(ai); game.getPhaseHandler().setCombat(combat); combat.addAttacker(attacker,p(1));
        if(declared) {combat.fireTriggersForUnblockedAttackers(game);} refresh();
    }
    static boolean willing() {return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai,spell).willingToPlay();}
    static SpellAbility selected() {
        List<SpellAbility> list=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();
        return list==null?null:list.stream().filter(sa->sa.getHostCard()==insight).findFirst().orElse(null);
    }
    static void library(int size) {
        ai.getZone(ZoneType.Library).removeAllCards(true);
        for(int i=0;i<size;i++) {add("Forest",ai,ZoneType.Library);} refresh();
    }
    static void basic() {
        start(true); check(willing() && spell.getTargetCard()==attacker,"unblocked six-power attacker is targeted");
        check(willing() && willing(),"repeated evaluation does not consume combat allowance");
        start(false); check(!willing(),"wait until blockers actually declared");
        start(false); Card blocker=permanent("Grizzly Bears",1); combat.addBlocker(attacker,blocker);
        combat.fireTriggersForUnblockedAttackers(game); refresh(); check(!willing(),"blocked attacker is not offered");
        for(PhaseType phase:List.of(PhaseType.MAIN1,PhaseType.COMBAT_DECLARE_ATTACKERS,PhaseType.COMBAT_DAMAGE,PhaseType.COMBAT_END)) {
            start(true); game.getPhaseHandler().devModeSet(phase,ai,false); check(!willing(),"holds outside blocker window: "+phase);
        }
        start(true); game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS,p(1),false);
        check(!willing(),"opponent's combat does not qualify");
        start(true); Card small=permanent("Grizzly Bears",0); combat.removeFromCombat(attacker); combat.addAttacker(small,p(1));
        combat.fireTriggersForUnblockedAttackers(game); refresh(); check(!willing(),"two damage is below minimum draw");
        for(int size:new int[]{0,6,9,10}) {start(true); library(size); check(willing()==(size>=10),"six draws leave four cards with library size "+size);}
        start(true); Card three=permanent("Centaur Courser",0); combat.addAttacker(three,p(1)); combat.fireTriggersForUnblockedAttackers(game); library(7);
        check(willing() && spell.getTargetCard()==three,"choose smaller safe draw over unsafe larger attack");
        start(true); Card large=permanent("Ancient Brontodon",0); combat.addAttacker(large,p(2)); combat.fireTriggersForUnblockedAttackers(game); refresh();
        check(willing() && spell.getTargetCard()==large,"choose largest safe predicted draw across defenders");
        start(true); attacker.setPhasedOut(ai); refresh(); check(!willing(),"phased out attacker cannot be targeted");
        start(true); Card murder=add("Murder",p(1),ZoneType.Hand); SpellAbility removal=murder.getFirstSpellAbility();
        removal.setActivatingPlayer(p(1)); removal.getTargets().add(attacker); removal.setHostCard(game.getAction().moveToStack(murder,removal)); game.getStack().add(removal); refresh();
        check(!willing(),"pending removal or any nonempty stack holds");
    }
    static void complex() {
        for(String name:List.of("Ridgetop Raptor","Porcelain Legionnaire","Blightsteel Colossus")) {
            start(true); combat.removeFromCombat(attacker); Card c=permanent(name,0); combat.addAttacker(c,p(1)); combat.fireTriggersForUnblockedAttackers(game); refresh();
            check(!willing(),"defer split-step or infect damage: "+name);
        }
        for(String name:List.of("Underworld Dreams","Notion Thief","Dictate of the Twin Gods","Glacial Chasm","Nivix Cyclops","Glorious Anthem")) {
            start(true); permanent(name,1); check(!willing(),"hold public draw/damage/cast/PT uncertainty: "+name);
        }
        start(true); permanent("Maralen of the Mornsong",1); check(!willing(),"cannot draw holds");
        start(true); Card walker=permanent("Jace Beleren",1); walker.setCounters(forge.game.card.CounterEnumType.LOYALTY,3); combat.removeFromCombat(attacker); combat.addAttacker(attacker,walker);
        combat.fireTriggersForUnblockedAttackers(game); refresh(); check(willing(),"planeswalker defender is eligible");
        start(true); Card battle=permanent("Invasion of Zendikar",1); battle.setProtectingPlayer(p(1)); combat.removeFromCombat(attacker); combat.addAttacker(attacker,battle);
        combat.fireTriggersForUnblockedAttackers(game); refresh(); check(!willing(),"battle defender is outside original draw trigger");
    }
    static void nativeDraw(boolean planeswalker) {
        start(true); Card walker=null;
        if(planeswalker) {
            walker=permanent("Jace Beleren",1); walker.setCounters(forge.game.card.CounterEnumType.LOYALTY,12);
            combat.removeFromCombat(attacker); combat.addAttacker(attacker,walker); combat.fireTriggersForUnblockedAttackers(game); refresh();
        }
        SpellAbility sa=selected(); check(sa!=null && sa.getTargetCard()==attacker,"normal controller targets Insight, planeswalker="+planeswalker);
        int hand=ai.getCardsIn(ZoneType.Hand).size(), lib=ai.getCardsIn(ZoneType.Library).size(), life=p(1).getLife();
        check(ComputerUtil.handlePlayingSpellAbility(ai,sa,null),"native payment casts original spell");
        check(ai.getLandsInPlay().stream().filter(Card::isTapped).count()==3,"native payment spends exactly three mana including green");
        game.getStack().resolveStack(); game.getAction().checkStateEffects(true); refresh();
        check(game.getCardState(insight,null).isInZone(ZoneType.Graveyard),"original instant resolves into graveyard");
        check(ai.getCardsIn(ZoneType.Hand).size()==hand-1,"effect resolution itself draws nothing");
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DAMAGE,ai,false);
        combat.assignCombatDamage(false); combat.dealAssignedDamage(); game.getTriggerHandler().runWaitingTriggers(); game.getAction().checkStateEffects(true);
        game.getStack().addAllTriggeredAbilitiesToStack();
        check(planeswalker?walker.getCounters(forge.game.card.CounterEnumType.LOYALTY)==6:p(1).getLife()==life-6,"native combat deals six actual damage, planeswalker="+planeswalker);
        check(!game.getStack().isEmpty(),"original delayed damage trigger enters native stack");
        while(!game.getStack().isEmpty()) {game.getStack().resolveStack();}
        check(ai.getCardsIn(ZoneType.Library).size()==lib-6 && ai.getCardsIn(ZoneType.Hand).size()==hand-1+6,"original triggered draw equals damage dealt");
        check(!ai.hasLost(),"native combat/draw avoids decking");
    }
    static void boundariesAndRecheck() {
        for(String change:List.of("library","blocked","phase","target")) {
            start(true); check(willing(),"approved before stale change "+change);
            if(change.equals("library")) {library(6);}
            if(change.equals("blocked")) {combat.setBlocked(attacker,true);}
            if(change.equals("phase")) {game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DAMAGE,ai,false);}
            if(change.equals("target")) {spell.resetTargets();}
            check(!ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"prepayment recheck rejects "+change);
            check(insight.isInZone(ZoneType.Hand) && ai.getLandsInPlay().stream().noneMatch(Card::isTapped),"cancelled cast spends no spell or mana: "+change);
        }
        start(true); check(willing(),"evaluate first copy"); SpellAbility other=hand("Hunter's Insight").getFirstSpellAbility(); other.setActivatingPlayer(ai);
        check(SpellApiToAi.Converter.get(other).canPlayWithSubs(ai,other).willingToPlay(),"evaluating first copy does not block second");
        check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"first cast consumes combat allowance");
        game.getStack().clear(); check(!SpellApiToAi.Converter.get(other).canPlayWithSubs(ai,other).willingToPlay(),"second copy held in same combat even before first resolves");
        start(true); check(willing(),"fresh game does not inherit combat marker");
        check(insight.getRules().getAiHints().getRemAIDecks(),"original deck-building hint retained");
        for(String profile:List.of("","greater-good","trading-post")) {AiOverrideProfile.setActive(profile); check(!HuntersInsightAi.uses(spell) && selected()==null,"default/unrelated profile remains blocked: "+profile);}
        start(true); for(Card land:ai.getLandsInPlay()) {if(land.getName().equals("Forest")) {land.setTapped(true);}} refresh();
        check(selected()==null,"color-sensitive native payment cannot replace green with colorless");
        start(true); permanent("Thalia, Guardian of Thraben",1); SpellAbility sa=selected();
        check(sa!=null && ComputerUtil.handlePlayingSpellAbility(ai,sa,null),"native spell tax is retained");
        check(ai.getLandsInPlay().stream().filter(Card::isTapped).count()==4,"native tax costs exactly four mana");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null); forge.util.MyRandom.setRandom(new Random(99001)); passed=0;
        basic(); complex(); nativeDraw(false); nativeDraw(true); boundariesAndRecheck(); AiOverrideProfile.setActive("");
        System.out.println("DONE "+passed+" Hunter's Insight checks");
    }
}
