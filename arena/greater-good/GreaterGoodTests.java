import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.cost.CostSacrifice;
import forge.game.phase.PhaseType;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.*;

/** Original-script fixtures through normal selection, native cost payment and resolution. */
public class GreaterGoodTests extends TradingPostTests {
    static Card greater;
    static SpellAbility ability;
    static void start() {
        setup(20); AiOverrideProfile.setActive("greater-good");
        greater=permanent("Greater Good",0);
        ability=greater.getSpellAbilities().stream().filter(sa->sa.getApi()==ApiType.Draw).findFirst().orElseThrow();
        ability.setActivatingPlayer(ai);
    }
    static boolean willing() {return SpellApiToAi.Converter.get(ability).canPlayWithSubs(ai,ability).willingToPlay();}
    static SpellAbility selected() {
        List<SpellAbility> list=((forge.ai.PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();
        return list==null?null:list.stream().filter(sa->sa.getHostCard()==greater && sa.getApi()==ApiType.Draw).findFirst().orElse(null);
    }
    static SpellAbility threaten(Card target) {
        return threaten(target,"Murder");
    }
    static SpellAbility threaten(Card target,String name) {
        Card murder=add(name,p(1),ZoneType.Hand);
        SpellAbility removal=murder.getFirstSpellAbility(); removal.setActivatingPlayer(p(1)); removal.getTargets().add(target);
        removal.setHostCard(game.getAction().moveToStack(murder,removal));
        game.getStack().add(removal); refresh(); return removal;
    }
    static void library(int size) {
        ai.getZone(ZoneType.Library).removeAllCards(true);
        for(int i=0;i<size;i++) {add("Forest",ai,ZoneType.Library);} refresh();
    }
    static Card victim() {return permanent("Craw Wurm",0);}
    static void decisions() {
        start(); victim(); check(!willing() && selected()==null,"healthy valuable creature is retained");
        start(); Card bear=permanent("Grizzly Bears",0); threaten(bear); check(!willing(),"doomed power below four holds");
        start(); Card wurm=victim(); threaten(wurm);
        check(willing(),"visible lethal removal permits rescue");
        check(GreaterGoodAi.payment(ai,ability)==wurm,"prediction binds the exact six-power creature");
        for(int size:new int[]{0,6,9,10}) {
            start(); wurm=victim(); threaten(wurm); library(size);
            check(willing()==(size>=10),"six draws preserve four-card library reserve at size "+size);
        }
        start(); Card four=permanent("Rumbling Baloth",0); wurm=victim(); threaten(four); threaten(wurm); library(8);
        check(willing() && GreaterGoodAi.payment(ai,ability)==four,"highest safe power skips larger unsafe draw");
        start(); four=permanent("Rumbling Baloth",0); wurm=victim(); threaten(four); threaten(wurm);
        check(willing() && GreaterGoodAi.payment(ai,ability)==wurm,"highest qualifying power preferred");
        start(); wurm=victim(); threaten(wurm); permanent("Maralen of the Mornsong",1);
        check(!willing(),"drawing prohibition holds");
        for(String risk:List.of("Blood Artist","Underworld Dreams","Waste Not","Notion Thief","Rest in Peace","Mayhem Devil")) {
            start(); wurm=victim(); threaten(wurm); permanent(risk,1); check(!willing(),"uncertain public trigger/replacement holds: "+risk);
        }
        start(); wurm=permanent("Darksteel Colossus",0); threaten(wurm);
        check(!willing(),"indestructible creature is not doomed by destroy removal");
        start(); wurm=permanent("Elesh Norn, Grand Cenobite",0); threaten(wurm);
        check(!willing(),"sacrificing continuous source is deferred conservatively");
        start(); wurm=victim(); threaten(wurm,"Control Magic"); check(!willing(),"theft is outside this removal policy");
        start(); wurm=victim(); threaten(wurm,"Unsummon"); check(!willing(),"bounce does not justify sacrificing healthy creature");
    }
    static void nativeRescue() {
        start(); Card wurm=victim(); Card healthy=permanent("Ancient Brontodon",0); threaten(wurm);
        SpellAbility sa=selected(); check(sa!=null,"normal controller selects rescue response");
        int before=ai.getCardsIn(ZoneType.Hand).size(), deck=ai.getCardsIn(ZoneType.Library).size();
        check(ComputerUtil.handlePlayingSpellAbility(ai,sa,null),"normal play pays native sacrifice cost");
        check(game.getCardState(wurm,null).isInZone(ZoneType.Graveyard),"chosen creature actually sacrificed");
        check(game.getCardState(healthy,null).isInZone(ZoneType.Battlefield),"larger healthy creature not substituted");
        game.getStack().resolveStack(); game.getAction().checkStateEffects(true); refresh();
        check(ai.getCardsIn(ZoneType.Library).size()==deck-6,"native sacrificed LKI draws exactly six");
        check(ai.getCardsIn(ZoneType.Hand).size()==before+3,"native draw six then discard three");
        check(ai.getCardsIn(ZoneType.Graveyard).size()==4,"native discard puts three real cards in graveyard");
        check(!ai.hasLost(),"rescue does not deck the controller");
    }
    static void combat(boolean trade, boolean trample) {
        start(); Card blocker=victim(); Card attacker=permanent(trade?"Craw Wurm":trample?"Terra Stomper":"Ancient Brontodon",1);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS,p(1));
        Combat combat=new Combat(p(1)); game.getPhaseHandler().setCombat(combat);
        combat.addAttacker(attacker,ai); combat.addBlocker(attacker,blocker);
        combat.fireTriggersForUnblockedAttackers(game); refresh();
        check(willing()==(!trade && !trample),"combat rescue trade="+trade+" trample="+trample);
        if(!trade && !trample) {
            SpellAbility sa=selected(); check(sa!=null,"normal controller selects combat rescue");
            check(ComputerUtil.handlePlayingSpellAbility(ai,sa,null),"combat rescue pays native sacrifice");
            game.getStack().resolveStack(); game.getAction().checkStateEffects(true); refresh();
            check(ai.getCardsIn(ZoneType.Library).size()==14 && ai.getCardsIn(ZoneType.Hand).size()==3,"native combat rescue draws six and discards three");
            check(combat.isBlocked(attacker) && game.getCardState(blocker,null).isInZone(ZoneType.Graveyard),"native blocked status survives sacrifice of blocker");
        }
    }
    static void stalePlans() {
        for(String change:List.of("power","library","threat","source","profile","removed","incarnation")) {
            start(); Card wurm=victim(); threaten(wurm); check(willing(),"plan created before "+change);
            if(change.equals("power")) {wurm.addPTBoost(1,0,game.getNextTimestamp(),0);}
            if(change.equals("library")) {library(6);}
            if(change.equals("threat")) {game.getStack().clear();}
            if(change.equals("source")) {greater.getController().getZone(ZoneType.Battlefield).remove(greater);}
            if(change.equals("profile")) {AiOverrideProfile.setActive("");}
            if(change.equals("removed")) {wurm.getController().getZone(ZoneType.Battlefield).remove(wurm);}
            if(change.equals("incarnation")) {wurm.setGameTimestamp(game.getNextTimestamp());}
            refresh(); int hand=ai.getCardsIn(ZoneType.Hand).size();
            check(!GreaterGoodAi.preflight(ai,ability),"stale plan rejected before payment: "+change);
            check(!ComputerUtil.handlePlayingSpellAbility(ai,ability,null),"normal play fails closed: "+change);
            check(ai.getCardsIn(ZoneType.Hand).size()==hand,"cancelled rescue spends no cards: "+change);
        }
        start(); Card wurm=victim(); threaten(wurm); check(willing(),"old game plan created"); SpellAbility old=ability;
        check(!GreaterGoodAi.preflight(ai,ability.copy(ai)),"unplanned ability copy cannot substitute another sacrifice");
        start(); check(GreaterGoodAi.payment(ai,old)==null,"new game cannot inherit old authorization");
    }
    static void boundariesAndCosts() {
        start(); Card wurm=victim(); threaten(wurm);
        check(greater.getRules().getAiHints().getRemAIDecks(),"original deck hint retained");
        for(String profile:List.of("","trading-post","gaze-of-granite")) {
            AiOverrideProfile.setActive(profile); check(!GreaterGoodAi.uses(ability) && selected()==null,"default/unrelated profile remains blocked: "+profile);
        }
        start(); wurm=victim(); threaten(wurm); permanent("Suppression Field",1);
        for(Card land:ai.getLandsInPlay()) {land.setTapped(true);} refresh();
        check(selected()==null,"native activation tax cannot be paid with tapped lands");
        for(Card land:ai.getLandsInPlay()) {land.setTapped(false);} refresh();
        SpellAbility sa=selected(); check(sa!=null && ComputerUtil.handlePlayingSpellAbility(ai,sa,null),"native activation tax paid normally");
        check(ai.getLandsInPlay().stream().filter(Card::isTapped).count()==2,"exactly two mana pays public tax");
        start(); greater.getController().getZone(ZoneType.Battlefield).remove(greater);
        greater=add("Greater Good",ai,ZoneType.Hand); for(int i=0;i<4;i++) {permanent("Forest",0);} refresh();
        List<SpellAbility> choices=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();
        check(choices!=null && choices.stream().anyMatch(s->s.getHostCard()==greater && s.isSpell()),"enchantment casting reuses native AI");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null);
        forge.util.MyRandom.setRandom(new Random(98001)); passed=0;
        decisions(); nativeRescue(); combat(false,false); combat(true,false); combat(false,true); stalePlans(); boundariesAndCosts();
        AiOverrideProfile.setActive(""); System.out.println("DONE "+passed+" Greater Good checks");
    }
}
