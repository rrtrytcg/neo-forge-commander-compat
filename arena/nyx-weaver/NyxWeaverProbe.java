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

/** Native-event observer only: actor hand and public cards/counts; never policy calls or library order. */
public class NyxWeaverProbe {
 public static class Observer {
  final Game game;int sequence;final Map<Integer,Map<String,Object>> pending=new HashMap<>();
  final Map<Integer,List<Map<String,Object>>> moves=new HashMap<>();
  Observer(Game g){game=g;}
  Player actor(){return game.getPlayers().stream().filter(p->p.getName().equals("NyxProbe-0")).findFirst().orElse(null);}
  Map<String,Object> card(Card c){Map<String,Object> r=new LinkedHashMap<>();r.put("id",c.getId());r.put("stamp",c.getGameTimestamp());r.put("name",c.getName());r.put("controller",c.getController().getName());r.put("zone",String.valueOf(c.getZone().getZoneType()));r.put("land",c.isLand());r.put("creature",c.isCreature());r.put("mv",c.getCMC());r.put("power",c.getNetPower());r.put("toughness",c.getNetToughness());return r;}
  Map<String,Object> state(){var r=new LinkedHashMap<String,Object>();Player p=actor();if(p==null)return r;r.put("player",p.getName());r.put("turn",game.getPhaseHandler().getTurn());r.put("phase",String.valueOf(game.getPhaseHandler().getPhase()));r.put("turnPlayer",game.getPhaseHandler().getPlayerTurn().getName());r.put("nextPlayer",game.getPhaseHandler().getNextTurn().getName());r.put("librarySize",p.getCardsIn(ZoneType.Library).size());r.put("drawn",p.getNumDrawnThisTurn());r.put("hand",p.getCardsIn(ZoneType.Hand).stream().map(this::card).toList());r.put("graveyard",p.getCardsIn(ZoneType.Graveyard).stream().map(this::card).toList());r.put("weavers",p.getCardsIn(ZoneType.Battlefield).stream().filter(c->c.getName().equals("Nyx Weaver")&&!c.isPhasedOut()).map(this::card).toList());Map<String,Integer> life=new LinkedHashMap<>();for(Player q:game.getPlayers())life.put(q.getName(),q.getLife());r.put("life",life);return r;}
  void emit(Map<String,Object> r){r.put("event",++sequence);System.out.println("NYX_WITNESS "+new Gson().toJson(r));}
  SpellAbility find(int id){for(var e:game.getStack())if(e.getSpellAbility().getId()==id)return e.getSpellAbility();return null;}
  Map<String,Object> action(SpellAbility s,String kind){var r=state();r.put("kind",kind);r.put("abilityId",s.getId());Card c=game.getCardState(s.getHostCard(),null);r.put("source",card(c==null?s.getHostCard():c));r.put("spell",s.isSpell());r.put("trigger",s.isTrigger());r.put("api",String.valueOf(s.getApi()));r.put("stackSize",game.getStack().size());r.put("targets",s.getTargets().getTargetCards().stream().map(this::card).toList());List<Map<String,Object>> mana=new ArrayList<>();for(var m:s.getPayingMana())mana.add(Map.of("color",m.toString(),"source",m.getSourceCard().getName()));r.put("mana",mana);List<Map<String,Object>> stack=new ArrayList<>();for(var e:game.getStack()){var a=e.getSpellAbility();stack.add(Map.of("abilityId",a.getId(),"source",a.getHostCard().getName(),"api",String.valueOf(a.getApi()),"targetIds",a.getTargets().getTargetCards().stream().map(Card::getId).toList()));}r.put("stack",stack);return r;}
  @Subscribe public void cast(GameEventSpellAbilityCast e){SpellAbility s=find(e.sa().getId());if(s==null||!s.getActivatingPlayer().getName().equals("NyxProbe-0")||!s.isSpell()&&!s.getHostCard().getName().equals("Nyx Weaver"))return;var r=action(s,"cast");emit(r);pending.put(s.getId(),r);moves.put(s.getId(),new ArrayList<>());}
  @Subscribe public void moved(GameEventCardChangeZone e){
   if(e.from()==null||e.to()==null||e.from().player()==null||!e.from().player().getName().equals("NyxProbe-0"))return;
   String name=e.card().getCurrentState().getName();
   if(e.from().zoneType()==ZoneType.Battlefield&&e.to().zoneType()==ZoneType.Exile&&name.equals("Nyx Weaver")){var r=state();r.put("kind","source-exile");r.put("sourceId",e.card().getId());emit(r);}
   if(e.from().zoneType()==ZoneType.Hand&&e.to().zoneType()==ZoneType.Battlefield&&e.card().getCurrentState().getType().isLand()){var r=state();r.put("kind","land-play");r.put("sourceId",e.card().getId());emit(r);}
   if((e.from().zoneType()==ZoneType.Library&&e.to().zoneType()==ZoneType.Graveyard)||(e.from().zoneType()==ZoneType.Graveyard&&e.to().zoneType()==ZoneType.Hand)){
    for(var entry:pending.entrySet())if(entry.getValue().get("source") instanceof Map<?,?> source&&source.get("name").equals("Nyx Weaver")){
     moves.get(entry.getKey()).add(Map.of("id",e.card().getId(),"name",name,"from",String.valueOf(e.from().zoneType()),"to",String.valueOf(e.to().zoneType())));
    }
   }
  }
  @Subscribe public void resolved(GameEventSpellResolved e){SpellAbility s=find(e.spell().getId());if(s==null)return;
   if(s.getHostCard().getName().equals("Cut Down")){var r=action(s,"threat-resolved");r.put("fizzled",e.hasFizzled());emit(r);}
   if(!pending.containsKey(s.getId()))return;var r=action(s,"resolved");r.put("before",pending.remove(s.getId()));r.put("movements",moves.remove(s.getId()));r.put("fizzled",e.hasFizzled());emit(r);
  }
  void end(){System.out.println("NYX_OBSERVER_END "+new Gson().toJson(Map.of("pendingActions",pending.size())));}
 }
 public static void main(String[]args)throws Exception{
  GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(Long.parseLong(args[0])));AiOverrideProfile.setActive(args[1].equals("on")?"nyx-weaver":"");
  List<RegisteredPlayer> players=new ArrayList<>();for(int i=0;i<4;i++){Deck d=DeckSerializer.fromFile(new File(args[i==0?3:4]));LobbyPlayerAi lobby=new LobbyPlayerAi("NyxProbe-"+i,null);lobby.setAiProfile("Default");players.add(RegisteredPlayer.forCommander(d).setPlayer(lobby));}
  GameRules rules=new GameRules(GameType.Commander);rules.addAppliedVariant(GameType.Commander);rules.setGamesPerMatch(1);Match match=new Match(rules,players,"Nyx Weaver custom Commander probe");Game game=match.createGame();game.setNoGUIUser();Observer observer=new Observer(game);game.subscribeToEvents(observer);
  System.out.println("NYX_PROBE seed="+args[0]+" profile="+args[1]+" clock="+args[2]);
  try{TimeLimitedCodeBlock.runWithTimeout(()->match.startGame(game),Integer.parseInt(args[2]),TimeUnit.SECONDS);}catch(TimeoutException expected){System.out.println("NYX_PROBE_CLOCK_STOP");}finally{if(!game.isGameOver())game.setGameOver(GameEndReason.Draw);}
  observer.end();List<GameLogEntry> entries=game.getGameLog().getLogEntries(null);Collections.reverse(entries);for(GameLogEntry e:entries)System.out.println(e);
  System.out.println("NYX_PROBE_DONE card-use only; no completion/winner/strength claim");
 }
}
