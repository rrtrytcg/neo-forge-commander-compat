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
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import forge.view.TimeLimitedCodeBlock;
import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Native events and getters only. No policy helpers, previews, forced actions or injected combat. */
public class SignpostProbe {
    public static class Observer {
        final Game game;int next,sequence;
        final Map<Integer,Map<String,Object>> pending=new HashMap<>();
        Observer(Game g){game=g;}
        SpellAbility find(int id){for(var e:game.getStack()){if(e.getSpellAbility().getId()==id){return e.getSpellAbility();}}return null;}
        Map<String,Object> entity(GameEntity e){Map<String,Object> r=new LinkedHashMap<>();if(e==null){return r;}r.put("id",e.getId());r.put("name",e.getName());r.put("type",e instanceof Player?"player":e instanceof Card c && c.isPlaneswalker()?"planeswalker":"card");if(e instanceof Card c){r.put("controller",c.getController().getName());r.put("owner",c.getOwner().getName());}if(e instanceof Player p){r.put("life",p.getLife());r.put("team",p.getTeam());}return r;}
        List<Map<String,Object>> attackers(){List<Map<String,Object>> out=new ArrayList<>();var combat=game.getCombat();if(combat==null){return out;}for(Card c:combat.getAttackers()){Map<String,Object> r=new LinkedHashMap<>();r.put("id",c.getId());r.put("name",c.getName());r.put("power",c.getNetPower());r.put("controller",c.getController().getName());r.put("tapped",c.isTapped());r.put("defender",entity(combat.getDefenderByAttacker(c)));r.put("bandSize",combat.getBandOfAttacker(c).getAttackers().size());out.add(r);}return out;}
        List<Map<String,Object>> rocks(){List<Map<String,Object>> out=new ArrayList<>();for(Card c:game.getCardsIn(ZoneType.Battlefield)){if(!c.getName().equals("Misleading Signpost")){continue;}Map<String,Object> r=new LinkedHashMap<>();r.put("id",c.getId());r.put("controller",c.getController().getName());r.put("tapped",c.isTapped());out.add(r);}return out;}
        Map<String,Object> row(SpellAbility s,String kind){Map<String,Object> r=new LinkedHashMap<>();r.put("kind",kind);r.put("abilityId",s.getId());r.put("sourceId",s.getHostCard().getId());r.put("name",s.getHostCard().getName());r.put("api",String.valueOf(s.getApi()));r.put("spell",s.isSpell());r.put("player",s.getActivatingPlayer().getName());r.put("phase",String.valueOf(game.getPhaseHandler().getPhase()));r.put("turn",game.getPhaseHandler().getTurn());r.put("turnPlayer",game.getPhaseHandler().getPlayerTurn().getName());r.put("nextPlayer",game.getPhaseHandler().getNextTurn().getName());r.put("stackSize",game.getStack().size());r.put("attackers",attackers());r.put("rocks",rocks());r.put("onBattlefield",game.getCardState(s.getHostCard(),null).isInPlay());List<Map<String,Object>> players=new ArrayList<>();for(Player p:game.getPlayers()){players.add(entity(p));}r.put("players",players);List<Map<String,Object>> defs=new ArrayList<>();if(game.getCombat()!=null){for(GameEntity d:game.getCombat().getDefenders()){defs.add(entity(d));}}r.put("defenders",defs);r.put("targetId",s.getTargetCard()==null?null:s.getTargetCard().getId());List<Map<String,Object>> mana=new ArrayList<>();for(var m:s.getPayingMana()){Map<String,Object> item=new LinkedHashMap<>();item.put("color",m.toString());item.put("sourceId",m.getSourceCard().getId());item.put("source",m.getSourceCard().getName());mana.add(item);}r.put("mana",mana);return r;}
        void emit(Map<String,Object> r){r.put("event",++sequence);System.out.println("SIGNPOST_WITNESS "+new Gson().toJson(r));}
        boolean spell(SpellAbility s){return s.isSpell() && s.getActivatingPlayer().getName().equals("SignpostProbe-0");}
        boolean redirect(SpellAbility s){return !s.isSpell() && s.getApi()==ApiType.ChangeCombatants && s.getHostCard().getName().equals("Misleading Signpost") && s.getActivatingPlayer().getName().equals("SignpostProbe-0");}
        @Subscribe public void cast(GameEventSpellAbilityCast e){SpellAbility s=find(e.sa().getId());if(s==null || !(spell(s)||redirect(s))){return;}Map<String,Object> r=row(s,spell(s)?"cast":"redirect-cast");r.put("witnessId",++next);emit(r);pending.put(s.getId(),r);}
        @Subscribe public void resolved(GameEventSpellResolved e){SpellAbility s=find(e.spell().getId());if(s==null || !pending.containsKey(s.getId())){return;}Map<String,Object> r=row(s,spell(s)?"resolved":"redirect");r.put("before",pending.remove(s.getId()));r.put("fizzled",e.hasFizzled());emit(r);}
        @Subscribe public void tapped(GameEventCardTapped e){if(!e.card().getName().equals("Misleading Signpost")){return;}Map<String,Object> r=new LinkedHashMap<>();r.put("kind",e.tapped()?"tap":"untap");r.put("sourceId",e.card().getId());r.put("phase",String.valueOf(game.getPhaseHandler().getPhase()));r.put("turn",game.getPhaseHandler().getTurn());emit(r);}
    }
    public static void main(String[] args)throws Exception{
        GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(Long.parseLong(args[0])));AiOverrideProfile.setActive(args[1].equals("on")?"misleading-signpost":"");List<RegisteredPlayer> players=new ArrayList<>();for(int i=0;i<4;i++){Deck d=DeckSerializer.fromFile(new File(args[i==0?3:4]));LobbyPlayerAi l=new LobbyPlayerAi("SignpostProbe-"+i,null);l.setAiProfile("Default");players.add(RegisteredPlayer.forCommander(d).setPlayer(l));}GameRules rules=new GameRules(GameType.Commander);rules.addAppliedVariant(GameType.Commander);rules.setGamesPerMatch(1);Match match=new Match(rules,players,"Misleading Signpost custom Commander probe");Game game=match.createGame();game.setNoGUIUser();game.subscribeToEvents(new Observer(game));System.out.println("SIGNPOST_PROBE seed="+args[0]+" profile="+args[1]+" clock="+args[2]);try{TimeLimitedCodeBlock.runWithTimeout(()->match.startGame(game),Integer.parseInt(args[2]),TimeUnit.SECONDS);}catch(TimeoutException expected){System.out.println("SIGNPOST_PROBE_CLOCK_STOP");}finally{if(!game.isGameOver()){game.setGameOver(GameEndReason.Draw);}}List<GameLogEntry> entries=game.getGameLog().getLogEntries(null);Collections.reverse(entries);for(GameLogEntry e:entries){System.out.println(e);}System.out.println("SIGNPOST_PROBE_DONE card-use only; no winner or completion claim");
    }
}
