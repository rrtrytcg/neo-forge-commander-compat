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
import forge.game.card.CounterEnumType;
import forge.game.combat.CombatUtil;
import forge.game.event.*;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.util.MyRandom;
import forge.view.TimeLimitedCodeBlock;
import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Native Match and read-only observer. No gameplay choices or states are forced. */
public class BiomassMutationProbe {
    public static class Observer {
        final Game game; SpellAbility watched; boolean resolved;
        Observer(Game game) { this.game=game; }
        SpellAbility find(int id) {
            for(SpellAbilityStackInstance item:game.getStack()) {
                SpellAbility sa=item.getSpellAbility();
                if(sa.getId()==id && sa.getApi()==ApiType.AnimateAll && "Biomass Mutation".equals(sa.getHostCard().getName())) { return sa; }
            }
            return null;
        }
        void snapshot(String stage,boolean fizzled) {
            List<Map<String,Object>> creatures=new ArrayList<>(); Player actor=watched.getActivatingPlayer();
            for(Card c:actor.getCreaturesInPlay()) {
                Map<String,Object> card=new LinkedHashMap<>(); card.put("id",c.getId()); card.put("name",c.getName());
                card.put("power",c.getNetPower()); card.put("toughness",c.getNetToughness()); card.put("damage",c.getDamage());
                card.put("canAttack",CombatUtil.canAttack(c)); card.put("blocking",game.getCombat()!=null && game.getCombat().isBlocking(c));
                card.put("types",c.getType().toString()); card.put("plus",c.getCounters(CounterEnumType.P1P1)); card.put("minus",c.getCounters(CounterEnumType.M1M1));
                creatures.add(card);
            }
            Map<String,Object> row=new LinkedHashMap<>(); row.put("stage",stage); row.put("abilityId",watched.getId());
            row.put("sourceId",watched.getHostCard().getId()); row.put("turn",game.getPhaseHandler().getTurn());
            row.put("phase",game.getPhaseHandler().getPhase().name()); row.put("turnPlayer",game.getPhaseHandler().getPlayerTurn().getName());
            row.put("player",actor.getName()); row.put("x",watched.getXManaCostPaid()); row.put("fizzled",fizzled);
            row.put("spell",watched.isSpell()); row.put("trigger",watched.isTrigger()); row.put("creatures",creatures);
            row.put("untappedLands",actor.getLandsInPlay().stream().filter(c->!c.isTapped()).count());
            System.out.println("BIOMASS_WITNESS "+new Gson().toJson(row));
        }
        void settle() { if(watched!=null && resolved) { snapshot("settled",false); watched=null; resolved=false; } }
        @Subscribe public void cast(GameEventSpellAbilityCast event) {
            settle(); SpellAbility sa=find(event.sa().getId()); if(sa!=null) { watched=sa; resolved=false; snapshot("stacked",false); }
        }
        @Subscribe public void resolved(GameEventSpellResolved event) {
            if(watched!=null && event.spell().getId()==watched.getId()) { snapshot("resolved",event.hasFizzled()); resolved=true; }
        }
        @Subscribe public void phase(GameEventTurnPhase event) { settle(); }
    }
    public static void main(String[] args) throws Exception {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null);
        MyRandom.setRandom(new Random(Long.parseLong(args[0]))); AiOverrideProfile.setActive(args[1].equals("on")?"biomass-mutation":"");
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            Deck deck=DeckSerializer.fromFile(new File(args[i==0?3:4]));
            LobbyPlayerAi lobby=new LobbyPlayerAi("BiomassProbe-"+i,null); lobby.setAiProfile("Default"); players.add(new RegisteredPlayer(deck).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Constructed); rules.setGamesPerMatch(1);
        Match match=new Match(rules,players,"Biomass Mutation custom card-use probe"); Game game=match.createGame(); game.setNoGUIUser();
        game.subscribeToEvents(new Observer(game)); System.out.println("BIOMASS_PROBE seed="+args[0]+" profile="+args[1]+" clock="+args[2]);
        try { TimeLimitedCodeBlock.runWithTimeout(()->match.startGame(game),Integer.parseInt(args[2]),TimeUnit.SECONDS); }
        catch(TimeoutException expected) { System.out.println("BIOMASS_PROBE_CLOCK_STOP"); }
        finally { if(!game.isGameOver()) { game.setGameOver(GameEndReason.Draw); } }
        List<GameLogEntry> entries=game.getGameLog().getLogEntries(null); Collections.reverse(entries);
        for(GameLogEntry entry:entries) { System.out.println(entry); }
        System.out.println("BIOMASS_PROBE_DONE card-use only; no result evaluation");
    }
}
