import com.google.common.eventbus.Subscribe;
import com.google.gson.Gson;
import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.AiOverrideProfile;
import forge.ai.LobbyPlayerAi;
import forge.deck.Deck;
import forge.deck.io.DeckSerializer;
import forge.game.*;
import forge.game.card.Card;
import forge.game.event.*;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import forge.view.TimeLimitedCodeBlock;
import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Read-only native events, own hand and public counts. No policy calls or injected gameplay. */
public class TreacherousTerrainProbe {
 public static class Observer {
  final Game game;int sequence;final Map<Integer,Map<String,Object>> pending=new HashMap<>();
  final Map<Integer,List<Map<String,Object>>> found=new HashMap<>();final Map<Integer,Integer> shuffles=new HashMap<>();
  Observer(Game g){game=g;}
  Player actor(){return game.getPlayers().stream().filter(p->p.getName().equals("TerrainProbe-0")).findFirst().orElse(null);}
  Map<String,Object> card(Card c){return Map.of("id",c.getId(),"stamp",c.getGameTimestamp(),"name",c.getName(),"land",c.isLand(),"basic",c.getType().isBasic(),"controller",c.getController().getName(),"zone",String.valueOf(c.getZone().getZoneType()));}
  Map<String,Object> state(){
   Map<String,Object> r=new LinkedHashMap<>();Player p=actor();if(p==null)return r;
   r.put("turn",game.getPhaseHandler().getTurn());r.put("phase",String.valueOf(game.getPhaseHandler().getPhase()));r.put("turnPlayer",game.getPhaseHandler().getPlayerTurn().getName());r.put("player",p.getName());
   r.put("hand",p.getCardsIn(ZoneType.Hand).stream().map(this::card).toList());r.put("librarySize",p.getCardsIn(ZoneType.Library).size());r.put("drawn",p.getNumDrawnThisTurn());
   Map<String,Integer> lands=new LinkedHashMap<>(),life=new LinkedHashMap<>();for(Player q:game.getPlayers()){lands.put(q.getName(),q.getLandsInPlay().size());life.put(q.getName(),q.getLife());}r.put("lands",lands);r.put("life",life);
   return r;
  }
  void emit(Map<String,Object> r){r.put("event",++sequence);System.out.println("TERRAIN_WITNESS "+new Gson().toJson(r));}
  SpellAbility find(int id){for(var entry:game.getStack())if(entry.getSpellAbility().getId()==id)return entry.getSpellAbility();return null;}
  Map<String,Object> action(SpellAbility s,String kind){
   var r=state();r.put("kind",kind);r.put("abilityId",s.getId());Card current=game.getCardState(s.getHostCard(),null);r.put("source",card(current==null?s.getHostCard():current));r.put("spell",s.isSpell());r.put("api",String.valueOf(s.getApi()));r.put("stackSize",game.getStack().size());r.put("targets",s.getTargets().size());
   List<Map<String,Object>> mana=new ArrayList<>();for(var m:s.getPayingMana())mana.add(Map.of("color",m.toString(),"source",m.getSourceCard().getName()));r.put("mana",mana);return r;
  }
  @Subscribe public void cast(GameEventSpellAbilityCast e){
   SpellAbility s=find(e.sa().getId());if(s==null||!s.getActivatingPlayer().getName().equals("TerrainProbe-0")||!s.isSpell()&&!s.getHostCard().getName().equals("Treacherous Terrain"))return;
   var r=action(s,"cast");emit(r);pending.put(s.getId(),r);found.put(s.getId(),new ArrayList<>());shuffles.put(s.getId(),0);
  }
  @Subscribe public void moved(GameEventCardChangeZone e){
   if(e.from()==null||e.to()==null)return;
   if(e.from().zoneType()==ZoneType.Stack&&e.to().zoneType()==ZoneType.Graveyard&&e.to().player()!=null&&e.to().player().getName().equals("TerrainProbe-0")&&e.card().getCurrentState().getName().equals("Treacherous Terrain")){
    var r=state();r.put("kind","spell-cleanup");r.put("sourceId",e.card().getId());emit(r);
   }
   if(e.from()==null||e.to()==null||e.from().player()==null||!e.from().player().getName().equals("TerrainProbe-0"))return;
   if(e.from().zoneType()==ZoneType.Hand&&e.to().zoneType()==ZoneType.Graveyard&&e.card().getCurrentState().getName().equals("Treacherous Terrain")){
    var r=state();r.put("kind","discard");r.put("sourceId",e.card().getId());emit(r);
   }
   if(e.from().zoneType()==ZoneType.Hand&&e.to().zoneType()==ZoneType.Battlefield&&e.card().getCurrentState().getType().isLand()){
    var r=state();r.put("kind","land-play");r.put("sourceId",e.card().getId());emit(r);
   }
   if(e.from().zoneType()==ZoneType.Library&&e.to().zoneType()==ZoneType.Hand){
    for(var entry:pending.entrySet())if(entry.getValue().get("spell").equals(false)){
     found.get(entry.getKey()).add(Map.of("id",e.card().getId(),"name",e.card().getCurrentState().getName(),"basic",e.card().getCurrentState().getType().isBasic(),"land",e.card().getCurrentState().getType().isLand()));
    }
   }
  }
  @Subscribe public void shuffled(GameEventShuffle e){if(!e.player().getName().equals("TerrainProbe-0"))return;for(var entry:pending.entrySet())if(entry.getValue().get("spell").equals(false))shuffles.merge(entry.getKey(),1,Integer::sum);}
  @Subscribe public void resolved(GameEventSpellResolved e){SpellAbility s=find(e.spell().getId());if(s==null||!pending.containsKey(s.getId()))return;var r=action(s,"resolved");r.put("before",pending.remove(s.getId()));r.put("found",found.remove(s.getId()));r.put("shuffles",shuffles.remove(s.getId()));r.put("fizzled",e.hasFizzled());emit(r);}
  void end(){System.out.println("TERRAIN_OBSERVER_END "+new Gson().toJson(Map.of("pendingActions",pending.size())));}
 }
 public static void main(String[] args)throws Exception{
  GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(Long.parseLong(args[0])));AiOverrideProfile.setActive(args[1].equals("on")?"treacherous-terrain":"");
  List<RegisteredPlayer> players=new ArrayList<>();for(int i=0;i<4;i++){Deck d=DeckSerializer.fromFile(new File(args[i==0?3:4]));LobbyPlayerAi lobby=new LobbyPlayerAi("TerrainProbe-"+i,null);lobby.setAiProfile("Default");players.add(RegisteredPlayer.forCommander(d).setPlayer(lobby));}
  GameRules rules=new GameRules(GameType.Commander);rules.addAppliedVariant(GameType.Commander);rules.setGamesPerMatch(1);Match match=new Match(rules,players,"Terrain custom Commander probe");Game game=match.createGame();game.setNoGUIUser();Observer observer=new Observer(game);game.subscribeToEvents(observer);
  System.out.println("TERRAIN_PROBE seed="+args[0]+" profile="+args[1]+" clock="+args[2]);
  try{TimeLimitedCodeBlock.runWithTimeout(()->match.startGame(game),Integer.parseInt(args[2]),TimeUnit.SECONDS);}catch(TimeoutException expected){System.out.println("TERRAIN_PROBE_CLOCK_STOP");}finally{if(!game.isGameOver())game.setGameOver(GameEndReason.Draw);}
  observer.end();List<GameLogEntry> entries=game.getGameLog().getLogEntries(null);Collections.reverse(entries);for(GameLogEntry entry:entries)System.out.println(entry);
  System.out.println("TERRAIN_PROBE_DONE card-use only; no winner or completion claim");
 }
}
