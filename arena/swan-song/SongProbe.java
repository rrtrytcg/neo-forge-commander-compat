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

/** Read-only native stack, mana, counter and original Bird observation. No policy calls. */
public class SongProbe {
    public static class Observer {
        final Game game;int next;
        final Map<Integer,Map<String,Object>> pending=new HashMap<>(),finished=new HashMap<>();
        final Map<Integer,SpellAbility> targets=new HashMap<>();
        final Set<Integer> resolvedDraws=new HashSet<>();
        Observer(Game g){game=g;}
        SpellAbility find(int id){for(var e:game.getStack()){if(e.getSpellAbility().getId()==id){return e.getSpellAbility();}}return null;}
        List<Map<String,Object>> birds(Player p){List<Map<String,Object>> out=new ArrayList<>();for(Card c:p.getCreaturesInPlay()){if(!c.isToken() || !c.getType().hasSubtype("Bird")){continue;}Map<String,Object> r=new LinkedHashMap<>();r.put("id",c.getId());r.put("owner",c.getOwner().getName());r.put("controller",c.getController().getName());r.put("power",c.getNetPower());r.put("toughness",c.getNetToughness());r.put("blue",c.getColor().hasBlue());r.put("flying",c.hasKeyword(Keyword.FLYING));r.put("haste",c.hasKeyword(Keyword.HASTE));r.put("tapped",c.isTapped());out.add(r);}return out;}
        List<Map<String,Object>> players(){List<Map<String,Object>> out=new ArrayList<>();for(Player p:game.getPlayers()){Map<String,Object> r=new LinkedHashMap<>();r.put("player",p.getName());r.put("life",p.getLife());r.put("librarySize",p.getCardsIn(ZoneType.Library).size());r.put("handIds",p.getCardsIn(ZoneType.Hand).stream().map(Card::getId).toList());r.put("birds",birds(p));out.add(r);}return out;}
        Map<String,Object> row(SpellAbility sa,String kind){Map<String,Object> r=new LinkedHashMap<>();r.put("kind",kind);r.put("abilityId",sa.getId());r.put("sourceId",sa.getHostCard().getId());r.put("name",sa.getHostCard().getName());r.put("spell",sa.isSpell());r.put("player",sa.getActivatingPlayer().getName());r.put("phase",String.valueOf(game.getPhaseHandler().getPhase()));r.put("turn",game.getPhaseHandler().getTurn());r.put("turnPlayer",game.getPhaseHandler().getPlayerTurn().getName());r.put("mana",sa.getPayingMana().stream().map(Object::toString).toList());r.put("players",players());List<Integer> stack=new ArrayList<>();for(var e:game.getStack()){stack.add(e.getSpellAbility().getId());}r.put("stack",stack);SpellAbility t=targets.get(sa.getId());if(t!=null){Map<String,Object> target=new LinkedHashMap<>();target.put("abilityId",t.getId());target.put("sourceId",t.getHostCard().getId());target.put("name",t.getHostCard().getName());target.put("controller",t.getActivatingPlayer().getName());target.put("manaValue",t.getHostCard().getCMC());target.put("counterable",t.isCounterableBy(sa));target.put("inGraveyard",game.getCardState(t.getHostCard(),null).isInZone(ZoneType.Graveyard));target.put("resolved",resolvedDraws.contains(t.getId()));r.put("target",target);}return r;}
        void emit(Map<String,Object> r){System.out.println("SONG_WITNESS "+new Gson().toJson(r));}
        boolean song(SpellAbility s){return s.isSpell() && s.getHostCard().getName().equals("Swan Song") && s.getActivatingPlayer().getName().equals("SongProbe-0");}
        boolean draw(SpellAbility s){return s.isSpell() && (s.getHostCard().getName().equals("Tidings") || s.getHostCard().getName().equals("Concentrate") || s.getHostCard().getName().equals("Divination") || s.getHostCard().getName().equals("Counsel of the Soratami"));}
        @Subscribe public void cast(GameEventSpellAbilityCast event){SpellAbility s=find(event.sa().getId());if(s==null || !(song(s)||draw(s))){return;}if(song(s)){targets.put(s.getId(),s.getTargets().getFirstTargetedSpell());}Map<String,Object> r=row(s,song(s)?"cast":"draw-cast");r.put("witnessId",++next);pending.put(s.getId(),r);emit(r);}
        @Subscribe public void resolved(GameEventSpellResolved event){SpellAbility s=find(event.spell().getId());if(s==null || !pending.containsKey(s.getId())){return;}if(draw(s)){resolvedDraws.add(s.getId());}Map<String,Object> r=row(s,song(s)?"resolved":"draw");Map<String,Object> b=pending.remove(s.getId());r.put("before",b);r.put("fizzled",event.hasFizzled());emit(r);if(song(s)){finished.put(s.getHostCard().getId(),b);targets.remove(s.getId());}}
        @Subscribe public void moved(GameEventCardChangeZone event){Map<String,Object> b=finished.get(event.card().getId());if(b==null || event.from()==null || event.from().zoneType()!=ZoneType.Stack){return;}Map<String,Object> r=new LinkedHashMap<>();r.put("kind","destination");r.put("sourceId",event.card().getId());r.put("witnessId",b.get("witnessId"));r.put("from",String.valueOf(event.from().zoneType()));r.put("to",String.valueOf(event.to()==null?null:event.to().zoneType()));emit(r);finished.remove(event.card().getId());}
    }
    public static void main(String[] args)throws Exception{
        GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(Long.parseLong(args[0])));AiOverrideProfile.setActive(args[1].equals("on")?"swan-song":"");
        List<RegisteredPlayer> players=new ArrayList<>();for(int i=0;i<4;i++){Deck d=DeckSerializer.fromFile(new File(args[i==0?3:4]));LobbyPlayerAi l=new LobbyPlayerAi("SongProbe-"+i,null);l.setAiProfile("Default");players.add(RegisteredPlayer.forCommander(d).setPlayer(l));}
        GameRules rules=new GameRules(GameType.Commander);rules.addAppliedVariant(GameType.Commander);rules.setGamesPerMatch(1);Match match=new Match(rules,players,"Swan Song custom Commander probe");Game game=match.createGame();game.setNoGUIUser();game.subscribeToEvents(new Observer(game));
        System.out.println("SONG_PROBE seed="+args[0]+" profile="+args[1]+" clock="+args[2]);
        try{TimeLimitedCodeBlock.runWithTimeout(()->match.startGame(game),Integer.parseInt(args[2]),TimeUnit.SECONDS);}catch(TimeoutException expected){System.out.println("SONG_PROBE_CLOCK_STOP");}
        finally{if(!game.isGameOver()){game.setGameOver(GameEndReason.Draw);}}
        List<GameLogEntry> entries=game.getGameLog().getLogEntries(null);Collections.reverse(entries);for(GameLogEntry e:entries){System.out.println(e);}System.out.println("SONG_PROBE_DONE card-use only; no winner or completion claim");
    }
}
