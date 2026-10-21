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
import forge.game.combat.CombatUtil;
import forge.game.event.*;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.*;
import forge.util.MyRandom;
import forge.view.TimeLimitedCodeBlock;
import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Native Match launcher with a read-only event subscriber. Never chooses or forces gameplay. */
public class MirrorEntityProbe {
    public static class Observer {
        private final Game game;
        Observer(Game game) { this.game=game; }
        private SpellAbility find(int id) {
            for(SpellAbilityStackInstance item:game.getStack()) {
                SpellAbility sa=item.getSpellAbility();
                if(sa.getId()==id && sa.getApi()==ApiType.AnimateAll && "Mirror Entity".equals(sa.getHostCard().getName())) { return sa; }
            }
            return null;
        }
        private void snapshot(String stage,SpellAbility sa,boolean fizzled) {
            List<Map<String,Object>> creatures=new ArrayList<>();
            for(Card c:sa.getActivatingPlayer().getCreaturesInPlay()) {
                Map<String,Object> card=new LinkedHashMap<>(); card.put("id",c.getId()); card.put("name",c.getName());
                card.put("power",c.getNetPower()); card.put("toughness",c.getNetToughness()); card.put("tapped",c.isTapped());
                card.put("canAttack",CombatUtil.canAttack(c)); card.put("blocking",game.getCombat()!=null && game.getCombat().isBlocking(c));
                card.put("elfAndGoblin",c.getType().hasSubtype("Elf") && c.getType().hasSubtype("Goblin")); creatures.add(card);
            }
            Map<String,Object> row=new LinkedHashMap<>(); row.put("stage",stage); row.put("abilityId",sa.getId());
            row.put("sourceId",sa.getHostCard().getId()); row.put("turn",game.getPhaseHandler().getTurn());
            row.put("phase",game.getPhaseHandler().getPhase().name()); row.put("turnPlayer",game.getPhaseHandler().getPlayerTurn().getName());
            row.put("player",sa.getActivatingPlayer().getName()); row.put("x",sa.getXManaCostPaid()); row.put("fizzled",fizzled); row.put("creatures",creatures);
            System.out.println("ENTITY_WITNESS "+new Gson().toJson(row));
        }
        @Subscribe public void cast(GameEventSpellAbilityCast event) {
            SpellAbility sa=find(event.sa().getId()); if(sa!=null) { snapshot("activated",sa,false); }
        }
        @Subscribe public void resolved(GameEventSpellResolved event) {
            SpellAbility sa=find(event.spell().getId()); if(sa!=null) { snapshot("resolved",sa,event.hasFizzled()); }
        }
    }
    public static void main(String[] args) throws Exception {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null);
        MyRandom.setRandom(new Random(Long.parseLong(args[0]))); AiOverrideProfile.setActive(args[1].equals("on")?"mirror-entity":"");
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            Deck deck=DeckSerializer.fromFile(new File(args[i==0?3:4]));
            LobbyPlayerAi lobby=new LobbyPlayerAi("EntityProbe-"+i,null); lobby.setAiProfile("Default");
            players.add(new RegisteredPlayer(deck).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Constructed); rules.setGamesPerMatch(1);
        Match match=new Match(rules,players,"Mirror Entity custom card-use probe"); Game game=match.createGame(); game.setNoGUIUser();
        game.subscribeToEvents(new Observer(game));
        System.out.println("ENTITY_PROBE seed="+args[0]+" profile="+args[1]+" clock="+args[2]);
        try { TimeLimitedCodeBlock.runWithTimeout(()->match.startGame(game),Integer.parseInt(args[2]),TimeUnit.SECONDS); }
        catch(TimeoutException expected) { System.out.println("ENTITY_PROBE_CLOCK_STOP"); }
        finally { if(!game.isGameOver()) { game.setGameOver(GameEndReason.Draw); } }
        List<GameLogEntry> entries=game.getGameLog().getLogEntries(null); Collections.reverse(entries);
        for(GameLogEntry entry:entries) { System.out.println(entry); }
        System.out.println("ENTITY_PROBE_DONE card-use only; no result evaluation");
    }
}
