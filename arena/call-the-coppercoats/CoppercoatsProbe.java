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
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import forge.view.TimeLimitedCodeBlock;
import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Read-only engine events in an autonomous native Commander match; no policy calls or forced gameplay. */
public class CoppercoatsProbe {
    public static class Observer {
        final Game game;
        int nextWitness;
        final Map<Integer,Map<String,Object>> before=new HashMap<>();
        final Map<Integer,Integer> tokenEvents=new HashMap<>();
        Observer(Game game){this.game=game;}
        SpellAbility find(int id){for(var entry:game.getStack()){SpellAbility sa=entry.getSpellAbility();if(sa.getId()==id && sa.getHostCard().getName().equals("Call the Coppercoats")){return sa;}}return null;}
        List<Map<String,Object>> tokens(Player p){
            List<Map<String,Object>> out=new ArrayList<>();for(Card c:p.getCreaturesInPlay()){
                if(!c.isToken()){continue;}Map<String,Object> t=new LinkedHashMap<>();t.put("id",c.getId());t.put("name",c.getName());t.put("controller",c.getController().getName());t.put("power",c.getNetPower());t.put("toughness",c.getNetToughness());t.put("human",c.getType().hasSubtype("Human"));t.put("soldier",c.getType().hasSubtype("Soldier"));t.put("white",c.getColor().hasWhite() && c.getColor().isMonoColor());out.add(t);
            }return out;
        }
        Map<String,Object> snapshot(SpellAbility sa,String kind){
            Player p=sa.getActivatingPlayer();Map<String,Object> r=new LinkedHashMap<>();r.put("kind",kind);r.put("abilityId",sa.getId());r.put("sourceId",sa.getHostCard().getId());r.put("spell",sa.isSpell());r.put("player",p.getName());r.put("turn",game.getPhaseHandler().getTurn());r.put("phase",String.valueOf(game.getPhaseHandler().getPhase()));r.put("turnPlayer",game.getPhaseHandler().getPlayerTurn().getName());r.put("nextPlayer",game.getPhaseHandler().getNextTurn().getName());r.put("manaPaid",sa.getPayingMana().size());r.put("manaColors",sa.getPayingMana().stream().map(Object::toString).toList());
            List<Map<String,Object>> targets=new ArrayList<>();for(Player t:sa.getTargets().getTargetPlayers()){Map<String,Object> v=new LinkedHashMap<>();v.put("id",t.getId());v.put("name",t.getName());v.put("creatures",t.getCreaturesInPlay().size());v.put("opponent",t.isOpponentOf(p));targets.add(v);}r.put("targets",targets);
            r.put("tokens",tokens(p));r.put("life",p.getLife());r.put("librarySize",p.getCardsIn(ZoneType.Library).size());r.put("handSize",p.getCardsIn(ZoneType.Hand).size());r.put("sourceInGraveyard",sa.getHostCard().isInZone(ZoneType.Graveyard));
            List<String> stack=new ArrayList<>();for(var entry:game.getStack()){if(entry.getSpellAbility().getId()!=sa.getId()){stack.add(entry.getSpellAbility().getHostCard().getName());}}r.put("otherStack",stack);return r;
        }
        void emit(Map<String,Object> r){System.out.println("COPPER_WITNESS "+new Gson().toJson(r));}
        @Subscribe public void cast(GameEventSpellAbilityCast event){SpellAbility sa=find(event.sa().getId());if(sa==null){return;}Map<String,Object> b=snapshot(sa,"cast");b.put("witnessId",++nextWitness);before.put(sa.getId(),b);tokenEvents.put(sa.getId(),0);emit(b);}
        @Subscribe public void tokens(GameEventTokenCreated event){for(var entry:game.getStack()){SpellAbility sa=entry.getSpellAbility();if(sa.getHostCard().getName().equals("Call the Coppercoats") && tokenEvents.containsKey(sa.getId())){tokenEvents.merge(sa.getId(),1,Integer::sum);return;}}}
        @Subscribe public void resolved(GameEventSpellResolved event){SpellAbility sa=find(event.spell().getId());if(sa==null){return;}Map<String,Object> r=snapshot(sa,"resolved");r.put("before",before.remove(sa.getId()));r.put("fizzled",event.hasFizzled());r.put("tokenEvents",tokenEvents.remove(sa.getId()));emit(r);}
    }
    public static void main(String[] args)throws Exception{
        GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(Long.parseLong(args[0])));AiOverrideProfile.setActive(args[1].equals("on")?"call-the-coppercoats":"");
        List<RegisteredPlayer> players=new ArrayList<>();for(int i=0;i<4;i++){Deck deck=DeckSerializer.fromFile(new File(args[i==0?3:4]));LobbyPlayerAi lobby=new LobbyPlayerAi("CopperProbe-"+i,null);lobby.setAiProfile("Default");players.add(RegisteredPlayer.forCommander(deck).setPlayer(lobby));}
        GameRules rules=new GameRules(GameType.Commander);rules.addAppliedVariant(GameType.Commander);rules.setGamesPerMatch(1);Match match=new Match(rules,players,"Coppercoats custom-deck Commander probe");Game game=match.createGame();game.setNoGUIUser();game.subscribeToEvents(new Observer(game));
        System.out.println("COPPER_PROBE seed="+args[0]+" profile="+args[1]+" clock="+args[2]);
        try{TimeLimitedCodeBlock.runWithTimeout(()->match.startGame(game),Integer.parseInt(args[2]),TimeUnit.SECONDS);}catch(TimeoutException expected){System.out.println("COPPER_PROBE_CLOCK_STOP");}
        finally{if(!game.isGameOver()){game.setGameOver(GameEndReason.Draw);}}
        List<GameLogEntry> entries=game.getGameLog().getLogEntries(null);Collections.reverse(entries);for(GameLogEntry entry:entries){System.out.println(entry);}System.out.println("COPPER_PROBE_DONE card-use only; no winner or completion claim");
    }
}
