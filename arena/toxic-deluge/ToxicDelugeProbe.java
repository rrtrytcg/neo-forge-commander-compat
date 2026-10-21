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

/** Read-only event observer; native casting and state-based deaths are never forced. */
public class ToxicDelugeProbe {
    public static class Observer {
        final Game game;
        SpellAbility watched;
        boolean resolved;
        Observer(Game game) { this.game=game; }
        void emit(Map<String,Object> row) { System.out.println("DELUGE_WITNESS "+new Gson().toJson(row)); }
        SpellAbility find(int id) {
            for(SpellAbilityStackInstance item:game.getStack()) {
                SpellAbility sa=item.getSpellAbility();
                if(sa.getId()==id && "Toxic Deluge".equals(sa.getHostCard().getName())) { return sa; }
            }
            return null;
        }
        List<Map<String,Object>> creatures() {
            List<Map<String,Object>> result=new ArrayList<>();
            for(Card c:game.getCardsIn(ZoneType.Battlefield)) {
                if(!c.isCreature() || c.isPhasedOut()) { continue; }
                Map<String,Object> row=new LinkedHashMap<>(); row.put("id",c.getId()); row.put("name",c.getName());
                row.put("controller",c.getController().getName()); row.put("opponent",c.getController().isOpponentOf(watched.getActivatingPlayer()));
                row.put("power",c.getNetPower()); row.put("toughness",c.getNetToughness()); row.put("damage",c.getDamage());
                row.put("indestructible",c.hasKeyword(Keyword.INDESTRUCTIBLE)); row.put("value",ComputerUtilCard.evaluateCreature(c));
                result.add(row);
            }
            return result;
        }
        void snapshot(String stage,boolean fizzled) {
            Map<String,Object> row=new LinkedHashMap<>(); Player actor=watched.getActivatingPlayer();
            row.put("stage",stage); row.put("abilityId",watched.getId()); row.put("sourceId",watched.getHostCard().getId());
            row.put("player",actor.getName()); row.put("turn",game.getPhaseHandler().getTurn()); row.put("fizzled",fizzled);
            row.put("spell",watched.isSpell()); row.put("trigger",watched.isTrigger()); row.put("x",watched.getXManaCostPaid());
            row.put("lifePaid",watched.getAmountLifePaid()); row.put("life",actor.getLife()); row.put("creatures",creatures()); emit(row);
        }
        void settle() {
            if(watched!=null && resolved) { snapshot("settled",false); watched=null; resolved=false; }
        }
        @Subscribe public void cast(GameEventSpellAbilityCast e) {
            settle(); SpellAbility sa=find(e.sa().getId());
            if(sa!=null) { watched=sa; resolved=false; snapshot("stacked",false); }
        }
        @Subscribe public void resolved(GameEventSpellResolved e) {
            SpellAbility sa=find(e.spell().getId()); if(sa!=null) { snapshot("resolved",e.hasFizzled()); resolved=true; }
        }
        @Subscribe public void phase(GameEventTurnPhase e) { settle(); }
        @Subscribe public void moved(GameEventCardChangeZone e) {
            if(watched!=null && resolved && e.from()!=null && e.to()!=null
                    && e.from().zoneType()==ZoneType.Battlefield && e.to().zoneType()==ZoneType.Graveyard) {
                Map<String,Object> row=new LinkedHashMap<>(); row.put("stage","death"); row.put("abilityId",watched.getId());
                row.put("cardId",e.card().getId()); row.put("name",e.card().getName()); emit(row);
            }
        }
        @Subscribe public void life(GameEventPlayerLivesChanged e) {
            if(e.player().getName().equals("DelugeProbe-0")) {
                Map<String,Object> row=new LinkedHashMap<>(); row.put("stage","life"); row.put("oldLife",e.oldLives()); row.put("newLife",e.newLives()); emit(row);
            }
        }
    }
    public static void main(String[] args) throws Exception {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null); MyRandom.setRandom(new Random(Long.parseLong(args[0])));
        AiOverrideProfile.setActive(args[1].equals("on")?"toxic-deluge":""); List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            Deck deck=DeckSerializer.fromFile(new File(args[i==0?3:4]));
            LobbyPlayerAi lobby=new LobbyPlayerAi("DelugeProbe-"+i,null); lobby.setAiProfile("Default"); players.add(new RegisteredPlayer(deck).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Constructed); rules.setGamesPerMatch(1);
        Match match=new Match(rules,players,"Toxic Deluge custom card-use probe"); Game game=match.createGame(); game.setNoGUIUser();
        game.subscribeToEvents(new Observer(game)); System.out.println("DELUGE_PROBE seed="+args[0]+" profile="+args[1]+" clock="+args[2]);
        try { TimeLimitedCodeBlock.runWithTimeout(()->match.startGame(game),Integer.parseInt(args[2]),TimeUnit.SECONDS); }
        catch(TimeoutException expected) { System.out.println("DELUGE_PROBE_CLOCK_STOP"); }
        finally { if(!game.isGameOver()) { game.setGameOver(GameEndReason.Draw); } }
        List<GameLogEntry> entries=game.getGameLog().getLogEntries(null); Collections.reverse(entries);
        for(GameLogEntry entry:entries) { System.out.println(entry); }
        System.out.println("DELUGE_PROBE_DONE card-use only; no result evaluation");
    }
}
