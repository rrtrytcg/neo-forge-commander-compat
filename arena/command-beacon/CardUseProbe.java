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

/** Native Commander match; observer only records native events, never makes decisions or forces use. */
public class CardUseProbe {
    public static class Observer {
        final Game game;
        final Map<Integer,Map<String,Object>> before=new HashMap<>();
        Observer(Game game) {this.game=game;}
        SpellAbility find(int id) {
            for(SpellAbilityStackInstance item:game.getStack()) {
                SpellAbility sa=item.getSpellAbility();
                if(sa.getId()==id && (sa.getHostCard().getName().equals("Command Beacon") || sa.getHostCard().isCommander())) {return sa;}
            }
            return null;
        }
        Map<String,Object> card(Card c) {
            Map<String,Object> row=new LinkedHashMap<>();row.put("id",c.getId());row.put("name",c.getName());
            row.put("zone",String.valueOf(c.getZone().getZoneType()));row.put("tapped",c.isTapped());
            row.put("commander",c.isCommander());row.put("commandCasts",c.getOwner().getCommanderCast(c));return row;
        }
        List<Map<String,Object>> players() {
            List<Map<String,Object>> result=new ArrayList<>();
            for(Player p:game.getPlayers()) {
                Map<String,Object> row=new LinkedHashMap<>();row.put("player",p.getName());row.put("life",p.getLife());
                row.put("lands",p.getLandsInPlay().stream().map(this::card).toList());
                row.put("hand",p.getCardsIn(ZoneType.Hand).stream().map(this::card).toList());
                row.put("command",p.getCardsIn(ZoneType.Command).stream().filter(c->!c.isImmutable()).map(this::card).toList());
                row.put("graveyard",p.getCardsIn(ZoneType.Graveyard).stream().map(this::card).toList());
                row.put("battlefield",p.getCardsIn(ZoneType.Battlefield).stream().map(this::card).toList());result.add(row);
            }
            return result;
        }
        Map<String,Object> snapshot(SpellAbility sa, String kind) {
            Map<String,Object> row=new LinkedHashMap<>();row.put("kind",kind);row.put("abilityId",sa.getId());
            row.put("turn",game.getPhaseHandler().getTurn());row.put("phase",String.valueOf(game.getPhaseHandler().getPhase()));
            row.put("turnPlayer",game.getPhaseHandler().getPlayerTurn().getName());row.put("player",sa.getActivatingPlayer().getName());
            row.put("spell",sa.isSpell());row.put("api",String.valueOf(sa.getApi()));row.put("source",card(sa.getHostCard()));
            row.put("manaPaid",sa.getPayingMana().size());
            row.put("from",sa.getHostCard().getCastFrom()==null?null:String.valueOf(sa.getHostCard().getCastFrom().getZoneType()));
            row.put("players",players());return row;
        }
        @Subscribe public void cast(GameEventSpellAbilityCast event) {
            SpellAbility sa=find(event.sa().getId());if(sa==null) {return;}
            Map<String,Object> row=snapshot(sa,"cast");before.put(sa.getId(),row);
            System.out.println("CARD_WITNESS "+new Gson().toJson(row));
        }
        @Subscribe public void resolved(GameEventSpellResolved event) {
            SpellAbility sa=find(event.spell().getId());if(sa==null) {return;}
            Map<String,Object> row=snapshot(sa,"resolved");row.put("fizzled",event.hasFizzled());row.put("before",before.remove(sa.getId()));
            System.out.println("CARD_WITNESS "+new Gson().toJson(row));
        }
    }
    public static void main(String[] args) throws Exception {
        GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(Long.parseLong(args[0])));
        AiOverrideProfile.setActive("on".equals(args[1])?"command-beacon":"");
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            Deck deck=DeckSerializer.fromFile(new File(args[i==0?3:4]));
            LobbyPlayerAi lobby=new LobbyPlayerAi("Beacon-"+i,null);lobby.setAiProfile("Default");
            players.add(RegisteredPlayer.forCommander(deck).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Commander);rules.addAppliedVariant(GameType.Commander);rules.setGamesPerMatch(1);
        Match match=new Match(rules,players,"Command Beacon custom-deck Commander probe");Game game=match.createGame();game.setNoGUIUser();
        game.subscribeToEvents(new Observer(game));
        System.out.println("CARD_PROBE Commander seed="+args[0]+" mode="+args[1]+" clock="+args[2]);
        try {TimeLimitedCodeBlock.runWithTimeout(()->match.startGame(game),Integer.parseInt(args[2]),TimeUnit.SECONDS);}
        catch(TimeoutException expected) {System.out.println("CARD_PROBE_CLOCK_STOP");}
        finally {if(!game.isGameOver()) {game.setGameOver(GameEndReason.Draw);}}
        List<GameLogEntry> entries=game.getGameLog().getLogEntries(null);Collections.reverse(entries);
        for(GameLogEntry entry:entries) {System.out.println(entry);}
        System.out.println("CARD_PROBE_DONE card-use only; no winner or completion claim");
    }
}
