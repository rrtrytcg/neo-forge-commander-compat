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
import forge.game.mana.Mana;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import forge.view.TimeLimitedCodeBlock;
import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Read-only native Commander events; no policy calls or forced gameplay. */
public class PrismProbe {
    public static class Observer {
        final Game game;final Map<Integer,Map<String,Object>> casts=new HashMap<>();final Map<Integer,Map<String,Object>> entries=new HashMap<>();int next;
        Observer(Game g){game=g;}
        SpellAbility find(int id){for(var e:game.getStack()){if(e.getSpellAbility().getId()==id){return e.getSpellAbility();}}return null;}
        void emit(Map<String,Object> row){System.out.println("PRISM_WITNESS "+new Gson().toJson(row));}
        Map<String,Object> row(SpellAbility sa,String kind){
            Map<String,Object> r=new LinkedHashMap<>();r.put("kind",kind);r.put("abilityId",sa.getId());r.put("sourceId",sa.getHostCard().getId());r.put("name",sa.getHostCard().getName());r.put("player",sa.getActivatingPlayer().getName());r.put("spell",sa.isSpell());r.put("phase",String.valueOf(game.getPhaseHandler().getPhase()));r.put("turn",game.getPhaseHandler().getTurn());
            List<Map<String,Object>> mana=new ArrayList<>();for(Mana m:sa.getPayingMana()){
                Map<String,Object> v=new LinkedHashMap<>();v.put("color",m.toString());v.put("source",m.getSourceCard().getName());v.put("sourceId",m.getSourceCard().getId());v.put("produced",m.getManaAbility()==null?"":m.getManaAbility().getOrigProduced());mana.add(v);
            }r.put("mana",mana);r.put("api",String.valueOf(sa.getApi()));r.put("trigger",sa.isTrigger());r.put("librarySize",sa.getActivatingPlayer().getCardsIn(ZoneType.Library).size());r.put("handSize",sa.getActivatingPlayer().getCardsIn(ZoneType.Hand).size());r.put("life",sa.getActivatingPlayer().getLife());r.put("handIds",sa.getActivatingPlayer().getCardsIn(ZoneType.Hand).stream().map(Card::getId).toList());
            List<Map<String,Object>> paid=new ArrayList<>();for(SpellAbility a:sa.getPayingManaAbilities()){
                Map<String,Object> v=new LinkedHashMap<>();v.put("source",a.getHostCard().getName());v.put("sourceId",a.getHostCard().getId());v.put("cost",a.getPayCosts().toString());v.put("produced",a.getParamOrDefault("Produced",""));v.put("paidColors",a.getPayingMana().stream().map(Object::toString).toList());paid.add(v);
            }r.put("paidAbilities",paid);
            List<Map<String,Object>> rocks=new ArrayList<>();for(Card c:sa.getActivatingPlayer().getCardsIn(ZoneType.Battlefield)){if(c.getName().equals("Prophetic Prism")){Map<String,Object> v=new LinkedHashMap<>();v.put("id",c.getId());v.put("tapped",c.isTapped());rocks.add(v);}}r.put("prisms",rocks);return r;
        }
        @Subscribe public void cast(GameEventSpellAbilityCast event){SpellAbility sa=find(event.sa().getId());if(sa==null || !sa.getActivatingPlayer().getName().equals("PrismProbe-0") || (!sa.isSpell() && !(sa.getHostCard().getName().equals("Prophetic Prism") && sa.getApi()==forge.game.ability.ApiType.Draw))){return;}Map<String,Object> r=row(sa,sa.isSpell()?"cast":"draw-cast");if(sa.isSpell()){r.put("witnessId",++next);}else{r.put("entry",entries.get(sa.getHostCard().getId()));}casts.put(sa.getId(),r);emit(r);}
        @Subscribe public void resolved(GameEventSpellResolved event){SpellAbility sa=find(event.spell().getId());if(sa==null || !casts.containsKey(sa.getId())){return;}Map<String,Object> r=row(sa,sa.isSpell()?"resolved":"draw");r.put("before",casts.remove(sa.getId()));r.put("fizzled",event.hasFizzled());r.put("onBattlefield",game.getCardState(sa.getHostCard(),null).isInZone(ZoneType.Battlefield));if(sa.isSpell() && sa.getHostCard().getName().equals("Prophetic Prism") && !event.hasFizzled()){entries.put(sa.getHostCard().getId(),r);}emit(r);}
    }
    public static void main(String[] args)throws Exception{
        GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(Long.parseLong(args[0])));AiOverrideProfile.setActive(args[1].equals("on")?"prophetic-prism":"");
        List<RegisteredPlayer> players=new ArrayList<>();for(int i=0;i<4;i++){Deck d=DeckSerializer.fromFile(new File(args[i==0?3:4]));LobbyPlayerAi l=new LobbyPlayerAi("PrismProbe-"+i,null);l.setAiProfile("Default");players.add(RegisteredPlayer.forCommander(d).setPlayer(l));}
        GameRules rules=new GameRules(GameType.Commander);rules.addAppliedVariant(GameType.Commander);rules.setGamesPerMatch(1);Match match=new Match(rules,players,"Prophetic Prism custom Commander probe");Game game=match.createGame();game.setNoGUIUser();game.subscribeToEvents(new Observer(game));
        System.out.println("PRISM_PROBE seed="+args[0]+" profile="+args[1]+" clock="+args[2]);
        try{TimeLimitedCodeBlock.runWithTimeout(()->match.startGame(game),Integer.parseInt(args[2]),TimeUnit.SECONDS);}catch(TimeoutException expected){System.out.println("PRISM_PROBE_CLOCK_STOP");}
        finally{if(!game.isGameOver()){game.setGameOver(GameEndReason.Draw);}}
        List<GameLogEntry> entries=game.getGameLog().getLogEntries(null);Collections.reverse(entries);for(GameLogEntry e:entries){System.out.println(e);}System.out.println("PRISM_PROBE_DONE card-use only; no winner or completion claim");
    }
}
