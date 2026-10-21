import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.*;
import forge.game.combat.Combat;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Original Timely Ward script, normal controller selection, native flash/payment/attachment and rescue consequences. */
public class TimelyWardTests extends KenrithsTransformationTests {
    static void setup() {
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            LobbyPlayerAi lobby=new LobbyPlayerAi("Ward-"+i,null);lobby.setAiProfile("Default");
            players.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Constructed);game=new Game(players,rules,new Match(rules,players,"Timely Ward fixtures"));
        game.setAge(GameStage.Play);ai=p(0);game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);
        for(int i=0;i<5;i++){add("Plains",ai,ZoneType.Battlefield);}
        for(int i=0;i<20;i++){add("Plains",ai,ZoneType.Library);}
        aura=add("Timely Ward",ai,ZoneType.Hand);spell=aura.getFirstSpellAbility();spell.setActivatingPlayer(ai);
        AiOverrideProfile.setActive("timely-ward");refresh();
    }
    static Card commander(String name,int seat){Card c=permanent(name,seat);c.setCommander(true);refresh();return c;}
    static SpellAbility threaten(Card card,String name){
        Card source=add(name,p(1),ZoneType.Hand);SpellAbility threat=source.getFirstSpellAbility();threat.setActivatingPlayer(p(1));
        if(threat.usesTargeting()){threat.getTargets().add(card);}
        threat.setHostCard(game.getAction().moveToStack(source,threat));game.getStack().add(threat);refresh();return threat;
    }
    static void ward(Card expected,int mana,String label){
        SpellAbility chosen=selection();check(chosen!=null,label+": normal controller selects original Ward");spell=chosen;
        check(spell.getTargetCard()==expected,label+": selected expected target");check(spell.canCastTiming(ai),label+": native target-dependent timing permits cast");
        check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null),label+": native payment enters stack");
        check(ai.getLandsInPlay().stream().filter(Card::isTapped).count()==mana,label+": exact native mana payment");
        game.getStack().resolveStack();refresh();Card resolved=game.getCardState(aura,null);Card c=game.getCardState(expected,null);
        check(resolved.isInZone(ZoneType.Battlefield) && resolved.getEnchantingCard()==c,label+": original Aura actually attached");
        check(c.hasKeyword(Keyword.INDESTRUCTIBLE),label+": original native indestructible layer");
    }
    static void choices(){
        setup();check(target()==null,"empty board holds");permanent("Craw Wurm",1);check(target()==null,"opposing creatures excluded");
        setup();ai.setTeam(0);p(1).setTeam(0);permanent("Craw Wurm",1);check(target()==null,"allied creatures excluded");
        setup();Card c=commander("Isamaru, Hound of Konda",0);permanent("Ancient Brontodon",0);check(target()==c,"proactive commander priority");ward(c,3,"proactive commander");
        setup();permanent("Grizzly Bears",0);c=permanent("Craw Wurm",0);check(target()==c,"without commander native creature value ranks targets");ward(c,3,"proactive noncommander");
        setup();Card first=permanent("Grizzly Bears",0);permanent("Grizzly Bears",0);check(target()==first,"equal native value breaks ties by stable ID");
        setup();permanent("Darksteel Myr",0);check(target()==null,"already indestructible holds");
        setup();c=commander("Isamaru, Hound of Konda",0);Card old=permanent("Timely Ward",0);old.attachToEntity(c,null);refresh();check(target()==null,"already Ward-enchanted holds");
        setup();c=permanent("Craw Wurm",0);c.setPhasedOut(ai);refresh();check(target()==null,"phased creature excluded");
        setup();c=permanent("Craw Wurm",0);c.addIntrinsicKeyword("Shroud");refresh();check(target()==null,"native targeting legality retained");
        setup();c=commander("Isamaru, Hound of Konda",0);permanent("Humility",1);check(target()==c,"native timestamp layers allow a newer Ward after Humility");ward(c,3,"native Humility ordering");
    }
    static void timing(){
        for(PhaseType phase:List.of(PhaseType.MAIN1,PhaseType.END_OF_TURN,PhaseType.COMBAT_BEGIN)){
            setup();commander("Isamaru, Hound of Konda",0);game.getPhaseHandler().devModeSet(phase,ai);check(target()==null,"no speculative protection in "+phase);
        }
        setup();commander("Isamaru, Hound of Konda",0);game.getPhaseHandler().devModeSet(PhaseType.MAIN2,p(1));check(target()==null,"opponent main phase without threat holds");
        setup();Card c=commander("Isamaru, Hound of Konda",0);game.getPhaseHandler().devModeSet(PhaseType.MAIN1,p(1));threaten(c,"Murder");
        check(target()==c,"commander targeted by destroy qualifies outside sorcery timing");ward(c,3,"reactive commander destroy");
        game.getStack().resolveStack();game.getAction().checkStateEffects(true);refresh();check(game.getCardState(c,null).isInPlay(),"native commander survives resolved Murder");
        setup();c=commander("Isamaru, Hound of Konda",0);game.getPhaseHandler().devModeSet(PhaseType.MAIN1,p(1));threaten(c,"Lightning Bolt");
        check(target()==c,"lethal damage rescue qualifies");ward(c,3,"reactive commander damage");game.getStack().resolveStack();game.getAction().checkStateEffects(true);refresh();
        check(game.getCardState(c,null).isInPlay() && game.getCardState(c,null).getDamage()==3,"native damage marked but commander survives");
        setup();c=commander("Isamaru, Hound of Konda",0);threaten(c,"Day of Judgment");check(target()==c,"plain destroy-all qualifies");ward(c,3,"native sweeper rescue");
        game.getStack().resolveStack();game.getAction().checkStateEffects(true);refresh();check(game.getCardState(c,null).isInPlay(),"native commander survives destroy-all");
        setup();c=permanent("Grizzly Bears",0);game.getPhaseHandler().devModeSet(PhaseType.MAIN1,p(1));threaten(c,"Murder");check(target()==null && selection()==null,"noncommander cannot gain Ward-specific flash");
        setup();c=commander("Isamaru, Hound of Konda",0);c.setBaseToughness(5);refresh();threaten(c,"Lightning Bolt");check(target()==null,"nonlethal damage is not a rescue");
    }
    static void ineffective(){
        for(String name:List.of("Swords to Plowshares","Unsummon","Diabolic Edict","Tragic Slip","Sudden Death")){
            setup();Card c=commander("Isamaru, Hound of Konda",0);threaten(c,name);check(target()==null,"ineffective indestructibility holds against "+name);
        }
        setup();Card c=commander("Isamaru, Hound of Konda",0);threaten(c,"Murder");threaten(c,"Unsummon");check(target()==null,"mixed removal is deferred");
        for(String risk:List.of("Blood Artist","Rest in Peace","Kor Spiritdancer","Eidolon of Blossoms","Mayhem Devil","Everlasting Torment")){
            setup();Card v=commander("Isamaru, Hound of Konda",0);threaten(v,"Murder");permanent(risk,1);check(target()==null,"uncertain public consequence holds: "+risk);
        }
    }
    static void combat(boolean declared,boolean wither,boolean attack){
        setup();Card c=commander("Isamaru, Hound of Konda",0),foe=permanent(wither?"Boggart Ram-Gang":"Ancient Brontodon",1);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS,attack?ai:p(1));Combat combat=new Combat(attack?ai:p(1));game.getPhaseHandler().setCombat(combat);
        if(attack){combat.addAttacker(c,p(1));combat.addBlocker(c,foe);}else{combat.addAttacker(foe,ai);combat.addBlocker(foe,c);}
        if(declared){combat.fireTriggersForUnblockedAttackers(game);}refresh();
        check((target()==c)==(declared&&!wither),"declared combat rescue declared="+declared+" wither="+wither+" attacking="+attack);
        if(declared&&!wither){ward(c,3,"combat rescue attack="+attack);check(!ComputerUtilCombat.combatantWouldBeDestroyed(ai,game.getCardState(c,null),combat),"native combat forecast changes to survival");
            combat.assignCombatDamage(false);combat.dealAssignedDamage();game.getAction().checkStateEffects(true);refresh();check(game.getCardState(c,null).isInPlay(),"native combat damage leaves protected commander alive");}
    }
    static void lifecycle(){
        setup();Card c=commander("Isamaru, Hound of Konda",0);permanent("Thalia, Guardian of Thraben",1);ward(c,4,"native noncreature tax");
        setup();commander("Isamaru, Hound of Konda",0);for(Card land:ai.getLandsInPlay()){land.setTapped(true);}check(selection()==null,"normal controller rejects unavailable mana");
        setup();commander("Isamaru, Hound of Konda",0);for(Card land:new ArrayList<>(ai.getLandsInPlay())){ai.getZone(ZoneType.Battlefield).remove(land);}for(int i=0;i<4;i++){permanent("Mountain",0);}check(selection()==null,"normal controller rejects missing white mana");
        setup();c=commander("Isamaru, Hound of Konda",0);spell=selection();check(spell!=null && ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"fizzle scenario casts natively");
        game.getAction().moveToGraveyard(c,null);game.getStack().resolveStack();refresh();check(game.getCardState(aura,null).isInZone(ZoneType.Graveyard),"native fizzle moves Aura to graveyard");
        setup();c=commander("Isamaru, Hound of Konda",0);game.getPhaseHandler().devModeSet(PhaseType.MAIN1,p(1));threaten(c,"Murder");spell=selection();check(spell!=null,"reactive selection before threat disappears");game.getStack().clear();refresh();
        check(!ComputerUtil.handlePlayingSpellAbility(ai,spell,null) && aura.isInZone(ZoneType.Hand) && ai.getLandsInPlay().stream().noneMatch(Card::isTapped),"disappeared threat cancels reactive use before costs");
        setup();c=commander("Isamaru, Hound of Konda",0);game.getPhaseHandler().devModeSet(PhaseType.MAIN1,p(1));threaten(c,"Murder");spell=selection();check(spell!=null,"reactive selection before shrinking target");c.setBaseToughness(0);refresh();check(!ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"zero toughness cancels ineffective protection");
        setup();c=commander("Isamaru, Hound of Konda",0);game.getPhaseHandler().devModeSet(PhaseType.MAIN1,p(1));threaten(c,"Murder");spell=selection();long time=game.getTimestamp(),rng=MyRandom.callCount();check(spell!=null && TimelyWardAi.preflight(ai,spell),"reactive preview remains stable");check(game.getTimestamp()==time && MyRandom.callCount()==rng && c.getAttachedCards().isEmpty() && !c.hasKeyword(Keyword.INDESTRUCTIBLE),"reactive preview leaves original combat/stats/attachments and RNG intact");
        Thread.currentThread().interrupt();check(!TimelyWardAi.preflight(ai,spell),"interrupted evaluation holds");Thread.interrupted();
    }
    static void fresh(){
        for(String change:List.of("controller","gone","phased","protected","rank","risk","phase")){
            setup();Card c=commander("Isamaru, Hound of Konda",0);spell=selection();check(spell!=null,"selected before change "+change);
            switch(change){
                case "controller":c.setController(p(1),game.getNextTimestamp());break;
                case "gone":game.getAction().moveToGraveyard(c,null);break;
                case "phased":c.setPhasedOut(ai);break;
                case "protected":Card old=permanent("Timely Ward",0);old.attachToEntity(c,null);break;
                case "rank":commander("Thrun, the Last Troll",0);break;
                case "risk":permanent("Kor Spiritdancer",1);break;
                case "phase":game.getPhaseHandler().devModeSet(PhaseType.MAIN1,ai);break;
            }
            refresh();boolean result=ComputerUtil.handlePlayingSpellAbility(ai,spell,null);
            check(!result,"changed position cancels "+change);
            check(ai.getLandsInPlay().stream().noneMatch(Card::isTapped) && aura.isInZone(ZoneType.Hand) && game.getStack().isEmpty(),"cancel precedes cost and zone mutation "+change);
        }
        setup();Card c=commander("Isamaru, Hound of Konda",0);long time=game.getTimestamp(),rng=MyRandom.callCount();int power=c.getNetPower();
        for(int i=0;i<4;i++){check(target()==c,"repeat policy selection "+i);}
        check(MyRandom.callCount()==rng,"policy consumes no evaluation RNG");check(game.getTimestamp()==time && c.getNetPower()==power && c.getAttachedCards().isEmpty() && aura.isInZone(ZoneType.Hand),"evaluation preserves live source target and timestamps");
        setup();commander("Isamaru, Hound of Konda",0);AiOverrideProfile.setActive("");check(selection()==null,"default remains blocked");check(aura.getRules().getAiHints().getRemAIDecks(),"original script warning retained");
        AiOverrideProfile.setActive("kenriths-transformation");check(selection()==null,"unrelated profile remains blocked");
    }
    public static void main(String[] args){GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(103011));choices();timing();ineffective();combat(false,false,false);combat(true,false,false);combat(true,false,true);combat(true,true,false);lifecycle();fresh();System.out.println("DONE "+passed+" Timely Ward checks");}
}
