import com.google.common.eventbus.Subscribe;
import com.google.gson.Gson;
import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.deck.io.DeckSerializer;
import forge.game.*;
import forge.game.card.Card;
import forge.game.event.*;
import forge.game.keyword.Keyword;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import forge.view.TimeLimitedCodeBlock;
import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Native Commander match and read-only observer. No forced hands, actions, decisions, targets or payment. */
public class TimelyWardProbe {
    record Rescue(int witness,Card victim,int threatId,long incarnation) { }
    public static class Observer {
        final Game game;
        int nextWitness;
        final Map<Integer,Map<String,Object>> before=new HashMap<>();
        final Map<Integer,List<Rescue>> awaitingThreat=new HashMap<>();
        final List<Rescue> awaitingPriority=new ArrayList<>();
        Observer(Game game){this.game=game;}
        SpellAbility find(int id){for(var entry:game.getStack()){if(entry.getSpellAbility().getId()==id){return entry.getSpellAbility();}}return null;}
        Map<String,Object> card(Card c){
            Map<String,Object> r=new LinkedHashMap<>();r.put("id",c.getId());r.put("name",c.getName());r.put("incarnation",c.getGameTimestamp());
            r.put("controller",c.getController().getName());r.put("zone",String.valueOf(c.getZone().getZoneType()));r.put("power",c.getNetPower());r.put("toughness",c.getNetToughness());r.put("damage",c.getDamage());
            r.put("commander",c.isCommander());r.put("indestructible",c.hasKeyword(Keyword.INDESTRUCTIBLE));r.put("ward",c.isEnchantedBy("Timely Ward"));return r;
        }
        void emit(Map<String,Object> r){System.out.println("WARD_WITNESS "+new Gson().toJson(r));}
        Map<String,Object> snapshot(SpellAbility sa,String kind){
            Map<String,Object> r=new LinkedHashMap<>();r.put("kind",kind);r.put("abilityId",sa.getId());r.put("sourceId",sa.getHostCard().getId());r.put("spell",sa.isSpell());
            r.put("turn",game.getPhaseHandler().getTurn());r.put("phase",String.valueOf(game.getPhaseHandler().getPhase()));r.put("turnPlayer",game.getPhaseHandler().getPlayerTurn().getName());r.put("player",sa.getActivatingPlayer().getName());
            r.put("manaPaid",sa.getPayingMana().size());r.put("targets",sa.getTargets().getTargetCards().stream().map(this::card).toList());
            List<Map<String,Object>> threats=new ArrayList<>();
            for(var entry:game.getStack()){
                SpellAbility t=entry.getSpellAbility();if(t.getId()==sa.getId()){continue;}
                Map<String,Object> v=new LinkedHashMap<>();v.put("id",t.getId());v.put("api",String.valueOf(t.getApi()));v.put("name",t.getHostCard().getName());v.put("targets",t.getTargets().getTargetCards().stream().map(Card::getId).toList());threats.add(v);
            }
            r.put("threats",threats);r.put("auraOnBattlefield",sa.getHostCard().isInPlay());r.put("attachedTo",sa.getHostCard().getEnchantingCard()==null?-1:sa.getHostCard().getEnchantingCard().getId());return r;
        }
        @Subscribe public void cast(GameEventSpellAbilityCast event){
            SpellAbility sa=find(event.sa().getId());if(sa==null || !sa.getHostCard().getName().equals("Timely Ward")){return;}
            Map<String,Object> b=snapshot(sa,"cast");b.put("witnessId",++nextWitness);before.put(sa.getId(),b);emit(b);
        }
        @Subscribe public void resolved(GameEventSpellResolved event){
            SpellAbility sa=find(event.spell().getId());if(sa==null){return;}
            if(sa.getHostCard().getName().equals("Timely Ward")){
                Map<String,Object> b=before.remove(sa.getId());Map<String,Object> r=snapshot(sa,"resolved");r.put("before",b);r.put("fizzled",event.hasFizzled());emit(r);
                if(b!=null && !event.hasFizzled() && sa.getTargetCard()!=null){
                    Card victim=sa.getTargetCard();
                    for(var entry:game.getStack()){
                        SpellAbility t=entry.getSpellAbility();if(t.getId()==sa.getId()){continue;}
                        if(t.getTargets().getTargetCards().contains(victim) || String.valueOf(t.getApi()).endsWith("All")){
                            awaitingThreat.computeIfAbsent(t.getId(),k->new ArrayList<>()).add(new Rescue(((Number)b.get("witnessId")).intValue(),victim,t.getId(),victim.getGameTimestamp()));
                        }
                    }
                }
            }
            List<Rescue> pending=awaitingThreat.remove(sa.getId());
            if(pending!=null){for(Rescue rescue:pending){Map<String,Object> r=new LinkedHashMap<>();r.put("kind","threat-resolved");r.put("witnessId",rescue.witness());r.put("threatId",sa.getId());r.put("api",String.valueOf(sa.getApi()));r.put("fizzled",event.hasFizzled());r.put("victim",card(game.getCardState(rescue.victim(),null)));emit(r);awaitingPriority.add(rescue);}}
        }
        void settled(){
            Iterator<Rescue> it=awaitingPriority.iterator();
            while(it.hasNext()){
                Rescue rescue=it.next();if(find(rescue.threatId())!=null){continue;}
                Card c=game.getCardState(rescue.victim(),null);Map<String,Object> r=new LinkedHashMap<>();r.put("kind","settled");r.put("witnessId",rescue.witness());r.put("threatId",rescue.threatId());r.put("originalIncarnation",rescue.incarnation());r.put("victim",card(c));emit(r);it.remove();
            }
        }
        @Subscribe public void priority(GameEventPlayerPriority event){settled();}
        @Subscribe public void phase(GameEventTurnPhase event){settled();}
    }
    public static void main(String[] args)throws Exception{
        GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(Long.parseLong(args[0])));AiOverrideProfile.setActive(args[1].equals("on")?"timely-ward":"");
        List<RegisteredPlayer> players=new ArrayList<>();for(int i=0;i<4;i++){Deck deck=DeckSerializer.fromFile(new File(args[i==0?3:4]));LobbyPlayerAi lobby=new LobbyPlayerAi("WardProbe-"+i,null);lobby.setAiProfile("Default");players.add(RegisteredPlayer.forCommander(deck).setPlayer(lobby));}
        GameRules rules=new GameRules(GameType.Commander);rules.addAppliedVariant(GameType.Commander);rules.setGamesPerMatch(1);Match match=new Match(rules,players,"Timely Ward custom-deck Commander probe");Game game=match.createGame();game.setNoGUIUser();game.subscribeToEvents(new Observer(game));
        System.out.println("WARD_PROBE seed="+args[0]+" profile="+args[1]+" clock="+args[2]);
        try{TimeLimitedCodeBlock.runWithTimeout(()->match.startGame(game),Integer.parseInt(args[2]),TimeUnit.SECONDS);}catch(TimeoutException expected){System.out.println("WARD_PROBE_CLOCK_STOP");}
        finally{if(!game.isGameOver()){game.setGameOver(GameEndReason.Draw);}}
        List<GameLogEntry> entries=game.getGameLog().getLogEntries(null);Collections.reverse(entries);for(GameLogEntry entry:entries){System.out.println(entry);}System.out.println("WARD_PROBE_DONE card-use only; no winner or completion claim");
    }
}
