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

/** Public read-only events. No policy calls, gameplay injection, target choices or phase edits. */
public class HullBreachProbe {
    public static class Observer {
        final Game game;int sequence;final Map<Integer,Map<String,Object>> pending=new HashMap<>();
        Observer(Game game){this.game=game;}
        SpellAbility find(int id){for(var entry:game.getStack()){if(entry.getSpellAbility().getId()==id)return entry.getSpellAbility();}return null;}
        Map<String,Object> card(Card c){Map<String,Object> r=new LinkedHashMap<>();r.put("id",c.getId());r.put("stamp",c.getGameTimestamp());r.put("name",c.getName());r.put("controller",c.getController().getName());r.put("owner",c.getOwner().getName());r.put("zone",String.valueOf(c.getZone().getZoneType()));r.put("artifact",c.isArtifact());r.put("enchantment",c.isEnchantment());r.put("creature",c.isCreature());r.put("cmc",c.getCMC());return r;}
        Map<String,Object> row(SpellAbility s,String kind){
            Map<String,Object> r=new LinkedHashMap<>();r.put("kind",kind);r.put("abilityId",s.getId());r.put("turn",game.getPhaseHandler().getTurn());r.put("phase",String.valueOf(game.getPhaseHandler().getPhase()));r.put("turnPlayer",game.getPhaseHandler().getPlayerTurn().getName());r.put("player",s.getActivatingPlayer().getName());r.put("source",card(s.getHostCard()));r.put("spell",s.isSpell());r.put("stackSize",game.getStack().size());
            List<Map<String,Object>> chain=new ArrayList<>(),targets=new ArrayList<>();
            for(SpellAbility part=s;part!=null;part=part.getSubAbility()){
                Map<String,Object> node=new LinkedHashMap<>();node.put("api",String.valueOf(part.getApi()));node.put("valid",part.getParamOrDefault("ValidTgts",""));node.put("defined",part.getParamOrDefault("Defined",""));chain.add(node);
                for(Card target:part.getTargets().getTargetCards()){Card current=game.getCardState(target,null);targets.add(card(current==null?target:current));}
            }
            r.put("chain",chain);r.put("targets",targets);
            List<Map<String,Object>> mana=new ArrayList<>();for(var m:s.getPayingMana()){mana.add(Map.of("color",m.toString(),"source",m.getSourceCard().getName(),"sourceId",m.getSourceCard().getId()));}r.put("mana",mana);
            List<Map<String,Object>> board=new ArrayList<>();for(Card c:game.getCardsIn(ZoneType.Battlefield)){board.add(card(c));}r.put("board",board);return r;
        }
        void emit(Map<String,Object> r){r.put("event",++sequence);System.out.println("HULL_WITNESS "+new Gson().toJson(r));}
        @Subscribe public void cast(GameEventSpellAbilityCast e){SpellAbility s=find(e.sa().getId());if(s==null||!s.isSpell()||!s.getActivatingPlayer().getName().equals("HullProbe-0"))return;Map<String,Object> r=row(s,"cast");emit(r);pending.put(s.getId(),r);}
        @Subscribe public void resolved(GameEventSpellResolved e){SpellAbility s=find(e.spell().getId());if(s==null||!pending.containsKey(s.getId()))return;Map<String,Object> r=row(s,"resolved");r.put("before",pending.remove(s.getId()));r.put("fizzled",e.hasFizzled());emit(r);}
    }
    public static void main(String[] args)throws Exception{
        GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(Long.parseLong(args[0])));AiOverrideProfile.setActive(args[1].equals("on")?"hull-breach":"");
        List<RegisteredPlayer> players=new ArrayList<>();for(int i=0;i<4;i++){Deck d=DeckSerializer.fromFile(new File(args[i==0?3:4]));LobbyPlayerAi lobby=new LobbyPlayerAi("HullProbe-"+i,null);lobby.setAiProfile("Default");players.add(RegisteredPlayer.forCommander(d).setPlayer(lobby));}
        GameRules rules=new GameRules(GameType.Commander);rules.addAppliedVariant(GameType.Commander);rules.setGamesPerMatch(1);Match match=new Match(rules,players,"Hull Breach custom Commander probe");Game game=match.createGame();game.setNoGUIUser();game.subscribeToEvents(new Observer(game));
        System.out.println("HULL_PROBE seed="+args[0]+" profile="+args[1]+" clock="+args[2]);
        try{TimeLimitedCodeBlock.runWithTimeout(()->match.startGame(game),Integer.parseInt(args[2]),TimeUnit.SECONDS);}catch(TimeoutException expected){System.out.println("HULL_PROBE_CLOCK_STOP");}finally{if(!game.isGameOver())game.setGameOver(GameEndReason.Draw);}
        List<GameLogEntry> entries=game.getGameLog().getLogEntries(null);Collections.reverse(entries);for(GameLogEntry entry:entries)System.out.println(entry);
        System.out.println("HULL_PROBE_DONE card-use only; no winner or completion claim");
    }
}
