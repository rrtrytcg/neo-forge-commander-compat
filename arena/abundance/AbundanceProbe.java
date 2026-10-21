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

/** Read-only native events and actor-visible state; no policy calls, injected gameplay or hidden library snapshots. */
public class AbundanceProbe {
    public static class Observer {
        final Game game;int sequence;final Map<Integer,Map<String,Object>> casts=new HashMap<>();
        Map<String,Object> choice;final List<Map<String,Object>> movements=new ArrayList<>();
        Observer(Game g){game=g;}
        Player actor(){return game.getPlayers().stream().filter(p->p.getName().equals("AbundanceProbe-0")).findFirst().orElse(null);}
        Map<String,Object> card(Card c){return Map.of("id",c.getId(),"stamp",c.getGameTimestamp(),"name",c.getName(),"land",c.isLand(),"controller",c.getController().getName(),"zone",String.valueOf(c.getZone().getZoneType()));}
        Map<String,Object> view(forge.game.card.CardView c){return Map.of("id",c.getId(),"name",c.getCurrentState().getName(),"land",c.getCurrentState().getType().isLand());}
        Map<String,Object> state(){
            Player p=actor();Map<String,Object> r=new LinkedHashMap<>();if(p==null)return r;
            r.put("turn",game.getPhaseHandler().getTurn());r.put("phase",String.valueOf(game.getPhaseHandler().getPhase()));r.put("player",p.getName());
            r.put("lands",p.getLandsInPlay().size());r.put("hand",p.getCardsIn(ZoneType.Hand).stream().map(this::card).toList());
            r.put("librarySize",p.getCardsIn(ZoneType.Library).size());r.put("drawn",p.getNumDrawnThisTurn());
            r.put("abundances",p.getCardsIn(ZoneType.Battlefield).stream().filter(c->c.getName().equals("Abundance")&&!c.isPhasedOut()).map(this::card).toList());
            return r;
        }
        void emit(Map<String,Object> r){r.put("event",++sequence);System.out.println("ABUNDANCE_WITNESS "+new Gson().toJson(r));}
        void settle(){if(choice==null)return;Map<String,Object> r=state();r.put("kind","settled");r.put("before",choice);r.put("movements",new ArrayList<>(movements));emit(r);choice=null;movements.clear();}
        @Subscribe public void mode(GameEventCardModeChosen e){
            if(!e.cardName().equals("Abundance")||!e.player().getName().equals("AbundanceProbe-0"))return;
            settle();choice=state();choice.put("kind","choice");choice.put("mode",e.mode());choice.put("random",e.random());
            List<Map<String,Object>> stack=new ArrayList<>();for(var entry:game.getStack()){var s=entry.getSpellAbility();stack.add(Map.of("abilityId",s.getId(),"source",s.getHostCard().getName(),"api",String.valueOf(s.getApi())));}choice.put("stack",stack);emit(choice);
        }
        @Subscribe public void moved(GameEventCardChangeZone e){
            if(e.from()!=null&&e.to()!=null&&e.from().zoneType()==ZoneType.Hand&&e.to().zoneType()==ZoneType.Battlefield
                    &&e.from().player().getName().equals("AbundanceProbe-0")&&e.card().getCurrentState().getType().isLand()){
                var land=state();land.put("kind","land-play");land.put("card",view(e.card()));emit(land);
            }
            if(choice==null||e.from()==null||e.to()==null||e.from().zoneType()!=ZoneType.Library||!e.from().player().getName().equals("AbundanceProbe-0"))return;
            if(e.to().zoneType()!=ZoneType.Hand&&e.to().zoneType()!=ZoneType.Library)return;
            Map<String,Object> r=new LinkedHashMap<>(view(e.card()));r.put("from","Library");r.put("to",String.valueOf(e.to().zoneType()));
            if(e.to().zoneType()==ZoneType.Library){var lib=actor().getCardsIn(ZoneType.Library);r.put("atBottom",!lib.isEmpty()&&lib.get(lib.size()-1).getId()==e.card().getId());}
            movements.add(r);
        }
        SpellAbility find(int id){for(var entry:game.getStack())if(entry.getSpellAbility().getId()==id)return entry.getSpellAbility();return null;}
        Map<String,Object> action(SpellAbility s,String kind){
            Map<String,Object> r=state();r.put("kind",kind);r.put("abilityId",s.getId());r.put("source",card(s.getHostCard()));r.put("spell",s.isSpell());r.put("stackSize",game.getStack().size());r.put("turnPlayer",game.getPhaseHandler().getPlayerTurn().getName());
            List<Map<String,Object>> mana=new ArrayList<>();for(var m:s.getPayingMana())mana.add(Map.of("color",m.toString(),"source",m.getSourceCard().getName()));r.put("mana",mana);return r;
        }
        @Subscribe public void cast(GameEventSpellAbilityCast e){SpellAbility s=find(e.sa().getId());if(s==null||!s.isSpell()||!s.getActivatingPlayer().getName().equals("AbundanceProbe-0"))return;settle();var r=action(s,"cast");emit(r);casts.put(s.getId(),r);}
        @Subscribe public void resolved(GameEventSpellResolved e){settle();SpellAbility s=find(e.spell().getId());if(s==null||!casts.containsKey(s.getId()))return;var r=action(s,"resolved");r.put("before",casts.remove(s.getId()));r.put("fizzled",e.hasFizzled());emit(r);}
        @Subscribe public void phase(GameEventTurnPhase e){settle();}
        void end(){System.out.println("ABUNDANCE_OBSERVER_END "+new Gson().toJson(Map.of("pendingCasts",casts.size(),"pendingChoice",choice!=null)));}
    }
    public static void main(String[] args)throws Exception{
        GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(Long.parseLong(args[0])));AiOverrideProfile.setActive(args[1].equals("on")?"abundance":"");
        List<RegisteredPlayer> players=new ArrayList<>();for(int i=0;i<4;i++){Deck d=DeckSerializer.fromFile(new File(args[i==0?3:4]));LobbyPlayerAi lobby=new LobbyPlayerAi("AbundanceProbe-"+i,null);lobby.setAiProfile("Default");players.add(RegisteredPlayer.forCommander(d).setPlayer(lobby));}
        GameRules rules=new GameRules(GameType.Commander);rules.addAppliedVariant(GameType.Commander);rules.setGamesPerMatch(1);Match match=new Match(rules,players,"Abundance custom Commander probe");Game game=match.createGame();game.setNoGUIUser();Observer observer=new Observer(game);game.subscribeToEvents(observer);
        System.out.println("ABUNDANCE_PROBE seed="+args[0]+" profile="+args[1]+" clock="+args[2]);
        try{TimeLimitedCodeBlock.runWithTimeout(()->match.startGame(game),Integer.parseInt(args[2]),TimeUnit.SECONDS);}catch(TimeoutException expected){System.out.println("ABUNDANCE_PROBE_CLOCK_STOP");}finally{if(!game.isGameOver())game.setGameOver(GameEndReason.Draw);}
        observer.end();List<GameLogEntry> entries=game.getGameLog().getLogEntries(null);Collections.reverse(entries);for(GameLogEntry entry:entries)System.out.println(entry);
        System.out.println("ABUNDANCE_PROBE_DONE card-use only; no winner or completion claim");
    }
}
