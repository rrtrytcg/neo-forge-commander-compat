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

/** Read-only observer around native Match.startGame. It never chooses or forces an action. */
public class OreskosExplorerProbe {
    public static class Observer {
        final Game game;
        Observer(Game game) { this.game=game; }
        SpellAbility find(int id) {
            for(SpellAbilityStackInstance item:game.getStack()) {
                SpellAbility sa=item.getSpellAbility();
                if(sa.getId()==id && "Oreskos Explorer".equals(sa.getHostCard().getName())) { return sa; }
            }
            return null;
        }
        List<Map<String,Object>> cards(Player player,ZoneType zone) {
            List<Map<String,Object>> result=new ArrayList<>();
            for(Card c:player.getCardsIn(zone)) {
                Map<String,Object> card=new LinkedHashMap<>(); card.put("id",c.getId()); card.put("name",c.getName());
                card.put("plains",c.getType().hasSubtype("Plains")); card.put("basic",c.isBasicLand()); result.add(card);
            }
            return result;
        }
        void snapshot(String stage,SpellAbility sa,boolean fizzled) {
            Player player=sa.getActivatingPlayer();
            Map<String,Object> row=new LinkedHashMap<>(); row.put("stage",stage); row.put("abilityId",sa.getId());
            row.put("sourceId",sa.getHostCard().getId()); row.put("spell",sa.isSpell()); row.put("trigger",sa.isTrigger());
            row.put("turn",game.getPhaseHandler().getTurn()); row.put("player",player.getName()); row.put("fizzled",fizzled);
            row.put("lands",player.getLandsInPlay().size()); row.put("tappedLands",player.getLandsInPlay().stream().filter(Card::isTapped).count());
            row.put("x",game.getPlayers().stream().filter(p->p.getLandsInPlay().size()>player.getLandsInPlay().size()).count());
            row.put("hand",cards(player,ZoneType.Hand)); row.put("library",cards(player,ZoneType.Library));
            row.put("battlefield",cards(player,ZoneType.Battlefield));
            System.out.println("EXPLORER_WITNESS "+new Gson().toJson(row));
        }
        @Subscribe public void cast(GameEventSpellAbilityCast event) {
            SpellAbility sa=find(event.sa().getId()); if(sa!=null) { snapshot("stacked",sa,false); }
        }
        @Subscribe public void resolved(GameEventSpellResolved event) {
            SpellAbility sa=find(event.spell().getId()); if(sa!=null) { snapshot("resolved",sa,event.hasFizzled()); }
        }
    }
    public static void main(String[] args) throws Exception {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null);
        MyRandom.setRandom(new Random(Long.parseLong(args[0]))); AiOverrideProfile.setActive(args[1].equals("on")?"oreskos-explorer":"");
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            Deck deck=DeckSerializer.fromFile(new File(args[i==0?3:4]));
            LobbyPlayerAi lobby=new LobbyPlayerAi("ExplorerProbe-"+i,null); lobby.setAiProfile("Default");
            players.add(new RegisteredPlayer(deck).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Constructed); rules.setGamesPerMatch(1);
        Match match=new Match(rules,players,"Oreskos Explorer custom card-use probe"); Game game=match.createGame(); game.setNoGUIUser();
        game.subscribeToEvents(new Observer(game));
        System.out.println("EXPLORER_PROBE seed="+args[0]+" profile="+args[1]+" clock="+args[2]);
        try { TimeLimitedCodeBlock.runWithTimeout(()->match.startGame(game),Integer.parseInt(args[2]),TimeUnit.SECONDS); }
        catch(TimeoutException expected) { System.out.println("EXPLORER_PROBE_CLOCK_STOP"); }
        finally { if(!game.isGameOver()) { game.setGameOver(GameEndReason.Draw); } }
        List<GameLogEntry> entries=game.getGameLog().getLogEntries(null); Collections.reverse(entries);
        for(GameLogEntry entry:entries) { System.out.println(entry); }
        System.out.println("EXPLORER_PROBE_DONE card-use only; no result evaluation");
    }
}
