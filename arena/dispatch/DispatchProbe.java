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

/** Ordinary native Match with a read-only observer. No forced gameplay. */
public class DispatchProbe {
    public static class Observer {
        final Game game;
        Observer(Game game) { this.game=game; }
        SpellAbility find(int id) {
            for(SpellAbilityStackInstance item:game.getStack()) {
                SpellAbility sa=item.getSpellAbility();
                if(sa.getId()==id && sa.isSpell() && "Dispatch".equals(sa.getHostCard().getName())) { return sa; }
            }
            return null;
        }
        void snapshot(String stage,SpellAbility sa,boolean fizzled) {
            Player player=sa.getActivatingPlayer(); List<Map<String,Object>> targets=new ArrayList<>();
            for(Card card:sa.getTargets().getTargetCards()) {
                Map<String,Object> target=new LinkedHashMap<>(); target.put("id",card.getId()); target.put("name",card.getName());
                target.put("creature",card.isCreature()); target.put("opponent",card.getController().isOpponentOf(player));
                target.put("legal",sa.canTarget(card)); target.put("tapped",card.isTapped());
                target.put("indestructible",card.hasKeyword(Keyword.INDESTRUCTIBLE));
                target.put("exiled",game.getCardsIn(ZoneType.Exile).stream().anyMatch(c->c.getId()==card.getId()));
                target.put("onBattlefield",game.getCardsIn(ZoneType.Battlefield).stream().anyMatch(c->c.getId()==card.getId()));
                targets.add(target);
            }
            Map<String,Object> row=new LinkedHashMap<>(); row.put("stage",stage); row.put("abilityId",sa.getId());
            row.put("sourceId",sa.getHostCard().getId()); row.put("turn",game.getPhaseHandler().getTurn());
            row.put("phase",game.getPhaseHandler().getPhase().name()); row.put("player",player.getName()); row.put("fizzled",fizzled);
            row.put("artifacts",player.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isArtifact).count());
            row.put("metalcraft",player.hasMetalcraft()); row.put("targets",targets);
            System.out.println("DISPATCH_WITNESS "+new Gson().toJson(row));
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
        MyRandom.setRandom(new Random(Long.parseLong(args[0]))); AiOverrideProfile.setActive(args[1].equals("on")?"dispatch":"");
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            Deck deck=DeckSerializer.fromFile(new File(args[i==0?3:4]));
            LobbyPlayerAi lobby=new LobbyPlayerAi("DispatchProbe-"+i,null); lobby.setAiProfile("Default");
            players.add(new RegisteredPlayer(deck).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Constructed); rules.setGamesPerMatch(1);
        Match match=new Match(rules,players,"Dispatch custom card-use probe"); Game game=match.createGame(); game.setNoGUIUser();
        game.subscribeToEvents(new Observer(game));
        System.out.println("DISPATCH_PROBE seed="+args[0]+" profile="+args[1]+" clock="+args[2]);
        try { TimeLimitedCodeBlock.runWithTimeout(()->match.startGame(game),Integer.parseInt(args[2]),TimeUnit.SECONDS); }
        catch(TimeoutException expected) { System.out.println("DISPATCH_PROBE_CLOCK_STOP"); }
        finally { if(!game.isGameOver()) { game.setGameOver(GameEndReason.Draw); } }
        List<GameLogEntry> entries=game.getGameLog().getLogEntries(null); Collections.reverse(entries);
        for(GameLogEntry entry:entries) { System.out.println(entry); }
        System.out.println("DISPATCH_PROBE_DONE card-use only; no result evaluation");
    }
}
