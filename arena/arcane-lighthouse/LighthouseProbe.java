import com.google.common.eventbus.Subscribe;
import com.google.gson.Gson;
import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.deck.io.DeckSerializer;
import forge.game.*;
import forge.game.ability.ApiType;
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

/** Read-only native events, no policy helpers or forced game actions. */
public class LighthouseProbe {
    public static class Observer {
        final Game game;int sequence;final Map<Integer,Map<String,Object>> pending=new HashMap<>();
        Observer(Game g){game=g;}
        SpellAbility find(int id){for(var e:game.getStack()){if(e.getSpellAbility().getId()==id){return e.getSpellAbility();}}return null;}
        Map<String,Object> card(Card c){Map<String,Object> r=new LinkedHashMap<>();r.put("id",c.getId());r.put("name",c.getName());r.put("controller",c.getController().getName());r.put("zone",String.valueOf(c.getZone().getZoneType()));r.put("stamp",c.getGameTimestamp());r.put("hexproof",c.hasKeyword(Keyword.HEXPROOF));r.put("shroud",c.hasKeyword(Keyword.SHROUD));r.put("tapped",c.isTapped());if(c.isCreature()){r.put("value",ComputerUtilCard.evaluateCreature(c));}return r;}
        Map<String,Object> row(SpellAbility s,String kind){
            Map<String,Object> r=new LinkedHashMap<>();r.put("kind",kind);r.put("abilityId",s.getId());r.put("source",card(s.getHostCard()));r.put("api",String.valueOf(s.getApi()));r.put("spell",s.isSpell());r.put("player",s.getActivatingPlayer().getName());r.put("phase",String.valueOf(game.getPhaseHandler().getPhase()));r.put("turn",game.getPhaseHandler().getTurn());r.put("turnPlayer",game.getPhaseHandler().getPlayerTurn().getName());r.put("stackSize",game.getStack().size());
            List<Map<String,Object>> board=new ArrayList<>();for(Card c:game.getCardsIn(ZoneType.Battlefield)){if(c.isCreature()||c.getName().equals("Arcane Lighthouse")){board.add(card(c));}}r.put("board",board);
            List<Map<String,Object>> targets=new ArrayList<>();for(Card c:s.getTargets().getTargetCards()){targets.add(card(game.getCardState(c,null)));}r.put("targets",targets);
            List<Map<String,Object>> mana=new ArrayList<>();for(var m:s.getPayingMana()){Map<String,Object> item=new LinkedHashMap<>();item.put("color",m.toString());item.put("sourceId",m.getSourceCard().getId());item.put("source",m.getSourceCard().getName());mana.add(item);}r.put("mana",mana);return r;
        }
        void emit(Map<String,Object> r){r.put("event",++sequence);System.out.println("LIGHTHOUSE_WITNESS "+new Gson().toJson(r));}
        boolean observed(SpellAbility s){return s.getActivatingPlayer().getName().equals("LighthouseProbe-0") && (s.isSpell()||s.getHostCard().getName().equals("Arcane Lighthouse") && s.getApi()==ApiType.AnimateAll);}
        @Subscribe public void cast(GameEventSpellAbilityCast e){SpellAbility s=find(e.sa().getId());if(s==null||!observed(s)){return;}Map<String,Object> r=row(s,"cast");emit(r);pending.put(s.getId(),r);}
        @Subscribe public void resolved(GameEventSpellResolved e){SpellAbility s=find(e.spell().getId());if(s==null||!pending.containsKey(s.getId())){return;}Map<String,Object> r=row(s,"resolved");r.put("before",pending.remove(s.getId()));r.put("fizzled",e.hasFizzled());emit(r);}
        @Subscribe public void tapped(GameEventCardTapped e){if(!e.card().getName().equals("Arcane Lighthouse")){return;}Map<String,Object> r=new LinkedHashMap<>();r.put("kind",e.tapped()?"tap":"untap");r.put("source",Map.of("id",e.card().getId(),"name",e.card().getName()));r.put("phase",String.valueOf(game.getPhaseHandler().getPhase()));r.put("turn",game.getPhaseHandler().getTurn());emit(r);}
    }
    public static void main(String[] args)throws Exception{
        GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(Long.parseLong(args[0])));AiOverrideProfile.setActive(args[1].equals("on")?"arcane-lighthouse":"");List<RegisteredPlayer> players=new ArrayList<>();for(int i=0;i<4;i++){Deck d=DeckSerializer.fromFile(new File(args[i==0?3:4]));LobbyPlayerAi l=new LobbyPlayerAi("LighthouseProbe-"+i,null);l.setAiProfile("Default");players.add(RegisteredPlayer.forCommander(d).setPlayer(l));}GameRules rules=new GameRules(GameType.Commander);rules.addAppliedVariant(GameType.Commander);rules.setGamesPerMatch(1);Match match=new Match(rules,players,"Arcane Lighthouse custom Commander probe");Game game=match.createGame();game.setNoGUIUser();game.subscribeToEvents(new Observer(game));System.out.println("LIGHTHOUSE_PROBE seed="+args[0]+" profile="+args[1]+" clock="+args[2]);try{TimeLimitedCodeBlock.runWithTimeout(()->match.startGame(game),Integer.parseInt(args[2]),TimeUnit.SECONDS);}catch(TimeoutException expected){System.out.println("LIGHTHOUSE_PROBE_CLOCK_STOP");}finally{if(!game.isGameOver()){game.setGameOver(GameEndReason.Draw);}}List<GameLogEntry> entries=game.getGameLog().getLogEntries(null);Collections.reverse(entries);for(GameLogEntry e:entries){System.out.println(e);}System.out.println("LIGHTHOUSE_PROBE_DONE card-use only; no winner or completion claim");
    }
}
