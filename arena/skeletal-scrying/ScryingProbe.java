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

/** Read-only native events; no policy calls, injected graveyards or forced gameplay. */
public class ScryingProbe {
    public static class Observer {
        final Game game;int next;
        final Map<Integer,Map<String,Object>> pending=new HashMap<>();
        final Map<Integer,Map<String,Object>> finished=new HashMap<>();
        Observer(Game g){game=g;}
        SpellAbility find(int id){for(var e:game.getStack()){if(e.getSpellAbility().getId()==id){return e.getSpellAbility();}}return null;}
        List<Map<String,Object>> cards(Iterable<Card> cards){
            List<Map<String,Object>> out=new ArrayList<>();for(Card c:cards){Map<String,Object> r=new LinkedHashMap<>();r.put("id",c.getId());r.put("name",c.getName());r.put("basic",c.isBasicLand());r.put("permanent",c.isPermanent());r.put("creature",c.isCreature());r.put("instant",c.isInstant());r.put("sorcery",c.isSorcery());r.put("owner",c.getOwner().getName());r.put("keywords",c.getKeywords().stream().map(k->k.getOriginal()).toList());r.put("inExile",game.getCardState(c,null).isInZone(ZoneType.Exile));r.put("lastZone",String.valueOf(c.getLastKnownZone()==null?null:c.getLastKnownZone().getZoneType()));out.add(r);}return out;
        }
        Map<String,Object> row(SpellAbility sa,String kind){
            Player p=sa.getActivatingPlayer();Map<String,Object> r=new LinkedHashMap<>();r.put("kind",kind);r.put("abilityId",sa.getId());r.put("sourceId",sa.getHostCard().getId());r.put("name",sa.getHostCard().getName());r.put("api",String.valueOf(sa.getApi()));r.put("spell",sa.isSpell());r.put("player",p.getName());r.put("phase",String.valueOf(game.getPhaseHandler().getPhase()));r.put("turn",game.getPhaseHandler().getTurn());r.put("turnPlayer",game.getPhaseHandler().getPlayerTurn().getName());r.put("nextPlayer",game.getPhaseHandler().getNextTurn().getName());r.put("otherStack",game.getStack().size()-1);
            r.put("x",sa.getXManaCostPaid());r.put("librarySize",p.getCardsIn(ZoneType.Library).size());r.put("handSize",p.getCardsIn(ZoneType.Hand).size());r.put("handIds",p.getCardsIn(ZoneType.Hand).stream().map(Card::getId).toList());r.put("life",p.getLife());r.put("mana",sa.getPayingMana().stream().map(Object::toString).toList());
            r.put("paidLKI",cards(sa.getPaidList("Exiled")));r.put("paidExiles",cards(sa.getPaidList("ExiledCards")));r.put("graveyard",cards(p.getCardsIn(ZoneType.Graveyard)));r.put("sourceInGraveyard",game.getCardState(sa.getHostCard(),null).isInZone(ZoneType.Graveyard));return r;
        }
        void emit(Map<String,Object> r){System.out.println("SCRYING_WITNESS "+new Gson().toJson(r));}
        boolean scrying(SpellAbility sa){return sa.isSpell() && sa.getHostCard().getName().equals("Skeletal Scrying");}
        boolean mill(SpellAbility sa){return sa.getApi()==ApiType.Mill && sa.getHostCard().getName().equals("Stitcher's Supplier");}
        @Subscribe public void cast(GameEventSpellAbilityCast event){SpellAbility sa=find(event.sa().getId());if(sa==null || !sa.getActivatingPlayer().getName().equals("ScryingProbe-0") || !(scrying(sa)||mill(sa))){return;}Map<String,Object> r=row(sa,scrying(sa)?"cast":"mill-start");r.put("witnessId",++next);pending.put(sa.getId(),r);emit(r);}
        @Subscribe public void resolved(GameEventSpellResolved event){SpellAbility sa=find(event.spell().getId());if(sa==null || !pending.containsKey(sa.getId())){return;}Map<String,Object> r=row(sa,scrying(sa)?"resolved":"mill");Map<String,Object> before=pending.remove(sa.getId());r.put("before",before);r.put("fizzled",event.hasFizzled());emit(r);if(scrying(sa)){finished.put(sa.getHostCard().getId(),before);}}
        @Subscribe public void moved(GameEventCardChangeZone event){Map<String,Object> b=finished.get(event.card().getId());if(b==null || event.from()==null || event.from().zoneType()!=ZoneType.Stack){return;}Map<String,Object> r=new LinkedHashMap<>();r.put("kind","destination");r.put("sourceId",event.card().getId());r.put("witnessId",b.get("witnessId"));r.put("from",String.valueOf(event.from().zoneType()));r.put("to",String.valueOf(event.to()==null?null:event.to().zoneType()));emit(r);finished.remove(event.card().getId());}
    }
    public static void main(String[] args)throws Exception{
        GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(Long.parseLong(args[0])));AiOverrideProfile.setActive(args[1].equals("on")?"skeletal-scrying":"");
        List<RegisteredPlayer> players=new ArrayList<>();for(int i=0;i<4;i++){Deck d=DeckSerializer.fromFile(new File(args[i==0?3:4]));LobbyPlayerAi l=new LobbyPlayerAi("ScryingProbe-"+i,null);l.setAiProfile("Default");players.add(RegisteredPlayer.forCommander(d).setPlayer(l));}
        GameRules rules=new GameRules(GameType.Commander);rules.addAppliedVariant(GameType.Commander);rules.setGamesPerMatch(1);Match match=new Match(rules,players,"Skeletal Scrying custom Commander probe");Game game=match.createGame();game.setNoGUIUser();game.subscribeToEvents(new Observer(game));
        System.out.println("SCRYING_PROBE seed="+args[0]+" profile="+args[1]+" clock="+args[2]);
        try{TimeLimitedCodeBlock.runWithTimeout(()->match.startGame(game),Integer.parseInt(args[2]),TimeUnit.SECONDS);}catch(TimeoutException expected){System.out.println("SCRYING_PROBE_CLOCK_STOP");}
        finally{if(!game.isGameOver()){game.setGameOver(GameEndReason.Draw);}}
        List<GameLogEntry> entries=game.getGameLog().getLogEntries(null);Collections.reverse(entries);for(GameLogEntry e:entries){System.out.println(e);}System.out.println("SCRYING_PROBE_DONE card-use only; no winner or completion claim");
    }
}
