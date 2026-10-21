import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.*;

/** Original-script selection, bound native sacrifice and scry fixtures. */
public class VisceraSeerTests extends GreaterGoodTests {
    static Card seer;
    public static class ScryObserver {
        int calls,top,bottom;
        @com.google.common.eventbus.Subscribe public void scry(forge.game.event.GameEventScry event) {
            calls++;top+=event.toTop();bottom+=event.toBottom();
        }
    }
    static void begin() {
        setup(20);AiOverrideProfile.setActive("viscera-seer");seer=permanent("Viscera Seer",0);
        ability=seer.getSpellAbilities().stream().filter(sa->sa.getApi()==ApiType.Scry).findFirst().orElseThrow();
        ability.setActivatingPlayer(ai);refresh();
    }
    static SpellAbility selected() {
        List<SpellAbility> list=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();
        return list==null?null:list.stream().filter(sa->sa.getHostCard()==seer && sa.getApi()==ApiType.Scry).findFirst().orElse(null);
    }
    static void decisions() {
        begin();Card bear=permanent("Grizzly Bears",0);check(!willing() && selected()==null,"healthy creatures are retained");
        begin();bear=permanent("Grizzly Bears",0);threaten(bear);
        check(VisceraSeerAi.uses(ability),"original single-sacrifice scry shape admitted");
        check(willing() && VisceraSeerAi.payment(ai,ability)==bear,"doomed two-power creature qualifies without power threshold");
        long rng=forge.util.MyRandom.callCount();check(willing() && willing(),"repeat evaluation remains willing");
        check(forge.util.MyRandom.callCount()==rng,"policy evaluation consumes no RNG");
        for(int size:new int[]{0,1,2,4}) {begin();bear=permanent("Grizzly Bears",0);threaten(bear);library(size);check(willing()==(size>0),"nonempty library only at size "+size);}
        begin();bear=permanent("Grizzly Bears",0);threaten(bear);permanent("Maralen of the Mornsong",1);check(willing(),"draw prohibition does not prohibit scry");
        begin();bear=permanent("Grizzly Bears",0);threaten(bear);permanent("Underworld Dreams",1);check(willing(),"scry does not trigger draw damage");
        for(String threat:List.of("Unsummon","Control Magic")) {begin();bear=permanent("Grizzly Bears",0);threaten(bear,threat);check(!willing(),"nonlethal threat holds: "+threat);}
        begin();Card ind=permanent("Darksteel Myr",0);threaten(ind);check(!willing(),"indestructible creature survives destroy spell");
        begin();bear=permanent("Grizzly Bears",0);threaten(bear,"Lightning Bolt");check(willing(),"visible lethal damage qualifies");
        begin();bear=permanent("Craw Wurm",0);threaten(bear,"Lightning Bolt");check(!willing(),"nonlethal damage holds");
        for(String risk:List.of("Blood Artist","Mayhem Devil","Rest in Peace","Midnight Reaper","Harsh Mentor","Psychic Corrosion","Eligeth, Crossroads Augur")) {
            begin();bear=permanent("Grizzly Bears",0);threaten(bear);permanent(risk,1);
            check(willing()==risk.equals("Psychic Corrosion"),"public consequence boundary: "+risk);
        }
        begin();bear=permanent("Glorious Anthem",0);Card lord=permanent("Elesh Norn, Grand Cenobite",0);threaten(lord);check(!willing(),"doomed continuous source deferred");
        begin();bear=permanent("Grizzly Bears",0);threaten(bear);bear.setPhasedOut(ai);refresh();check(!willing(),"phased creature held");
        begin();bear=permanent("Grizzly Bears",0);threaten(bear);permanent("Pithing Needle",1).setNamedCards(List.of("Viscera Seer"));refresh();check(!willing(),"native activation prohibition held");
        begin();bear=permanent("Grizzly Bears",0);threaten(bear);Thread.currentThread().interrupt();
        try {check(!willing(),"interrupted evaluation holds");}finally{Thread.interrupted();}
    }
    static void choices() {
        begin();Card small=permanent("Grizzly Bears",0),big=permanent("Craw Wurm",0);threaten(big);threaten(small);
        check(willing() && VisceraSeerAi.payment(ai,ability)==small,"lowest-value doomed creature preferred");
        begin();small=permanent("Grizzly Bears",0);big=permanent("Grizzly Bears",0);threaten(big);threaten(small);
        check(willing() && VisceraSeerAi.payment(ai,ability)==small,"equal values use lower card id");
        begin();big=permanent("Craw Wurm",0);threaten(seer);threaten(big);
        check(willing() && VisceraSeerAi.payment(ai,ability)==big,"another doomed creature preferred over Seer itself");
        begin();permanent("Craw Wurm",0);threaten(seer);
        check(willing() && VisceraSeerAi.payment(ai,ability)==seer,"doomed Seer itself qualifies without healthy replacement");
    }
    static void nativeUse(boolean self) {
        begin();Card doomed=self?seer:permanent("Grizzly Bears",0);Card healthy=permanent("Ancient Brontodon",0);threaten(doomed);
        SpellAbility sa=selected();check(sa!=null,"normal controller chooses removal response self="+self);
        ScryObserver seen=new ScryObserver();game.subscribeToEvents(seen);
        int cards=ai.getCardsIn(ZoneType.Library).size(),hand=ai.getCardsIn(ZoneType.Hand).size(),life=ai.getLife();
        check(ComputerUtil.handlePlayingSpellAbility(ai,sa,null),"normal payment succeeds self="+self);
        check(game.getCardState(doomed,null).isInZone(ZoneType.Graveyard),"bound creature actually sacrificed self="+self);
        check(game.getCardState(healthy,null).isInPlay(),"healthy creature not substituted self="+self);
        check(com.google.common.collect.Iterables.size(sa.getPaidList("Sacrificed"))==1,"exactly one native sacrifice recorded self="+self);
        game.getStack().resolveStack();game.getAction().checkStateEffects(true);refresh();
        check(seen.calls==1 && seen.top+seen.bottom==1,"original effect emits exactly one native scry self="+self);
        check(ai.getCardsIn(ZoneType.Library).size()==cards && ai.getCardsIn(ZoneType.Hand).size()==hand && ai.getLife()==life,"native scry neither draws nor spends life self="+self);
        check(!game.getStack().isEmpty(),"pending removal remains below resolved scry self="+self);
        game.getStack().resolveStack();game.getAction().checkStateEffects(true);refresh();
        check(game.getCardState(healthy,null).isInPlay(),"original removal cannot substitute healthy target self="+self);
    }
    static void nativeOrdering(boolean bottom,boolean single) {
        begin();Card bear=permanent("Grizzly Bears",0);threaten(bear);
        ai.getZone(ZoneType.Library).removeAllCards(true);
        Card top=add(bottom?"Wastes":"Grizzly Bears",ai,ZoneType.Library);
        if(!single){add("Forest",ai,ZoneType.Library);add("Swamp",ai,ZoneType.Library);}
        if(bottom){for(int i=0;i<8;i++){permanent("Wastes",0);}}refresh();
        ScryObserver seen=new ScryObserver();game.subscribeToEvents(seen);SpellAbility sa=selected();
        check(sa!=null && ComputerUtil.handlePlayingSpellAbility(ai,sa,null),"native ordering pays bound sacrifice bottom="+bottom+" single="+single);
        game.getStack().resolveStack();game.getAction().checkStateEffects(true);refresh();
        check(seen.calls==1 && seen.top+seen.bottom==1,"native scry reveals only one card bottom="+bottom+" single="+single);
        check((bottom?ai.getCardsIn(ZoneType.Library).getLast():ai.getCardsIn(ZoneType.Library).getFirst()).getId()==top.getId(),"native top/bottom placement bottom="+bottom+" single="+single);
        check(ai.getCardsIn(ZoneType.Library).size()==(single?1:3) && !ai.hasLost(),"scry never draws or decks at boundary single="+single);
    }
    static void combat(boolean trade,boolean trample,boolean declared) {
        begin();Card blocker=permanent("Grizzly Bears",0),attacker=permanent(trade?"Grizzly Bears":trample?"Terra Stomper":"Ancient Brontodon",1);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS,p(1));Combat combat=new Combat(p(1));game.getPhaseHandler().setCombat(combat);
        combat.addAttacker(attacker,ai);combat.addBlocker(attacker,blocker);if(declared){combat.fireTriggersForUnblockedAttackers(game);}refresh();
        check(willing()==(!trade && !trample && declared),"combat trade="+trade+" trample="+trample+" declared="+declared);
        if(!declared) {
            threaten(permanent("Ancient Brontodon",0),"Lightning Bolt");
            check(!willing(),"unrelated nonlethal stack does not authorize undeclared combat sacrifice");
        }
        if(!trade && !trample && declared) {
            SpellAbility sa=selected();check(sa!=null && ComputerUtil.handlePlayingSpellAbility(ai,sa,null),"normal controller pays declared-blocker rescue");
            game.getStack().resolveStack();game.getAction().checkStateEffects(true);refresh();
            check(combat.isBlocked(attacker) && game.getCardState(blocker,null).isInZone(ZoneType.Graveyard),"native blocked status survives sacrifice");
        }
    }
    static void stale() {
        for(String change:List.of("threat","library","source","profile","removed","incarnation","source-incarnation","risk","turn","better-choice")) {
            begin();Card bear=permanent("Grizzly Bears",0);threaten(bear);check(willing(),"plan created before "+change);
            if(change.equals("threat")){game.getStack().clear();}
            if(change.equals("library")){library(0);}
            if(change.equals("source")){ai.getZone(ZoneType.Battlefield).remove(seer);}
            if(change.equals("profile")){AiOverrideProfile.setActive("");}
            if(change.equals("removed")){ai.getZone(ZoneType.Battlefield).remove(bear);}
            if(change.equals("incarnation")){bear.setGameTimestamp(game.getNextTimestamp());}
            if(change.equals("source-incarnation")){seer.setGameTimestamp(game.getNextTimestamp());}
            if(change.equals("risk")){permanent("Blood Artist",1);}
            if(change.equals("turn")){game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai,game.getPhaseHandler().getTurn()+1);}
            if(change.equals("better-choice")){threaten(permanent("Ornithopter",0));}
            refresh();check(!VisceraSeerAi.preflight(ai,ability),"stale preflight holds: "+change);
            check(!ComputerUtil.handlePlayingSpellAbility(ai,ability,null),"native payment cancels: "+change);
            check(ai.getCardsIn(ZoneType.Graveyard).isEmpty(),"cancelled plan sacrifices nothing: "+change);
        }
        begin();threaten(seer);check(willing(),"self plan created while no other doomed creature exists");
        Card alternate=permanent("Craw Wurm",0);threaten(alternate);
        check(!VisceraSeerAi.preflight(ai,ability) && !ComputerUtil.handlePlayingSpellAbility(ai,ability,null),"new other doomed creature cancels old self-sacrifice plan");
        check(seer.isInPlay() && alternate.isInPlay(),"stale self plan cannot substitute or sacrifice either creature");
        begin();Card bear=permanent("Grizzly Bears",0);threaten(bear);SpellAbility copy=ability.copy();
        check(!VisceraSeerAi.preflight(ai,copy),"unplanned copy has no payment authority");
        check(seer.getRules().getAiHints().getRemAIDecks(),"original script prohibition remains");
        begin();bear=permanent("Grizzly Bears",0);threaten(bear);check(willing(),"old game plan created");SpellAbility old=ability;
        begin();check(!VisceraSeerAi.preflight(ai,old),"game rollover cannot reuse old plan");
        bear=permanent("Grizzly Bears",0);threaten(bear);check(willing(),"fresh game independently qualifies");
        for(String profile:List.of("","greater-good","command-beacon")){AiOverrideProfile.setActive(profile);check(!VisceraSeerAi.uses(ability) && selected()==null,"default or unrelated profile blocked: "+profile);}
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);forge.util.MyRandom.setRandom(new Random(101001));passed=0;
        decisions();choices();nativeUse(false);nativeUse(true);nativeOrdering(false,false);nativeOrdering(true,false);nativeOrdering(false,true);combat(false,false,true);combat(true,false,true);combat(false,true,true);combat(false,false,false);stale();
        AiOverrideProfile.setActive("");System.out.println("DONE "+passed+" Viscera Seer checks");
    }
}
