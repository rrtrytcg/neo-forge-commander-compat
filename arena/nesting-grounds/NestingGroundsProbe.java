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
import forge.game.card.*;
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

/** Autonomous native match. The event subscriber only reads and reports state. */
public class NestingGroundsProbe {
    public static class Observer {
        final Game game;
        SpellAbility watched;
        boolean resolved;
        Observer(Game game) { this.game=game; }
        void emit(Map<String,Object> row) { System.out.println("NESTING_WITNESS "+new Gson().toJson(row)); }
        SpellAbility find(int id) {
            for(SpellAbilityStackInstance item:game.getStack()) {
                SpellAbility sa=item.getSpellAbility();
                if(sa.getId()==id && "Nesting Grounds".equals(sa.getHostCard().getName()) && sa.getApi()==ApiType.Pump) { return sa; }
            }
            return null;
        }
        Map<String,Object> card(Card original) {
            Card c=game.getCardState(original,null); Map<String,Object> row=new LinkedHashMap<>();
            row.put("id",c.getId()); row.put("name",c.getName()); row.put("timestamp",c.getGameTimestamp());
            row.put("controller",c.getController().getName()); row.put("opponent",c.getController().isOpponentOf(watched.getActivatingPlayer()));
            row.put("battlefield",c.isInZone(ZoneType.Battlefield)); row.put("creature",c.isCreature());
            row.put("plus",c.getCounters(CounterEnumType.P1P1)); row.put("minus",c.getCounters(CounterEnumType.M1M1));
            row.put("power",c.getNetPower()); row.put("toughness",c.getNetToughness()); row.put("damage",c.getDamage());
            row.put("value",ComputerUtilCard.evaluateCreature(c));
            return row;
        }
        void snapshot(String stage,boolean fizzled) {
            Map<String,Object> row=new LinkedHashMap<>(); Player actor=watched.getActivatingPlayer();
            row.put("stage",stage); row.put("abilityId",watched.getId()); row.put("sourceId",watched.getHostCard().getId());
            row.put("player",actor.getName()); row.put("turn",game.getPhaseHandler().getTurn()); row.put("phase",game.getPhaseHandler().getPhase().name());
            row.put("playerTurn",game.getPhaseHandler().getPlayerTurn().getName()); row.put("fizzled",fizzled);
            row.put("spell",watched.isSpell()); row.put("trigger",watched.isTrigger()); row.put("landTapped",watched.getHostCard().isTapped());
            row.put("donor",card(watched.getTargetCard())); row.put("recipient",card(watched.getSubAbility().getTargetCard()));
            row.put("scales",actor.getCardsIn(ZoneType.Battlefield).stream().filter(c->c.getName().equals("Hardened Scales")).count());
            emit(row);
        }
        void settle() { if(watched!=null && resolved) { snapshot("settled",false); watched=null; resolved=false; } }
        @Subscribe public void cast(GameEventSpellAbilityCast e) {
            settle(); SpellAbility sa=find(e.sa().getId());
            if(sa!=null) { watched=sa; resolved=false; snapshot("stacked",false); }
        }
        @Subscribe public void resolved(GameEventSpellResolved e) {
            if(watched!=null && e.spell().getId()==watched.getId()) { snapshot("resolved",e.hasFizzled()); resolved=true; }
        }
        @Subscribe public void phase(GameEventTurnPhase e) { settle(); }
    }
    public static void main(String[] args) throws Exception {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null); MyRandom.setRandom(new Random(Long.parseLong(args[0])));
        AiOverrideProfile.setActive(args[1].equals("on")?"nesting-grounds":""); List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            Deck deck=DeckSerializer.fromFile(new File(args[i==0?3:4]));
            LobbyPlayerAi lobby=new LobbyPlayerAi("NestingProbe-"+i,null); lobby.setAiProfile("Default"); players.add(new RegisteredPlayer(deck).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Constructed); rules.setGamesPerMatch(1);
        Match match=new Match(rules,players,"Nesting Grounds custom card-use probe"); Game game=match.createGame(); game.setNoGUIUser();
        game.subscribeToEvents(new Observer(game)); System.out.println("NESTING_PROBE seed="+args[0]+" profile="+args[1]+" clock="+args[2]);
        try { TimeLimitedCodeBlock.runWithTimeout(()->match.startGame(game),Integer.parseInt(args[2]),TimeUnit.SECONDS); }
        catch(TimeoutException expected) { System.out.println("NESTING_PROBE_CLOCK_STOP"); }
        finally { if(!game.isGameOver()) { game.setGameOver(GameEndReason.Draw); } }
        List<GameLogEntry> entries=game.getGameLog().getLogEntries(null); Collections.reverse(entries);
        for(GameLogEntry entry:entries) { System.out.println(entry); }
        System.out.println("NESTING_PROBE_DONE card-use only; no result evaluation");
    }
}
