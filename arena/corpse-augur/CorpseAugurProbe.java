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

/** Read-only event observer on a native Match. No gameplay, draws or trigger choices are forced. */
public class CorpseAugurProbe {
    public static class Observer {
        final Game game;
        Observer(Game game) { this.game=game; }
        SpellAbility find(int id) {
            for(SpellAbilityStackInstance item:game.getStack()) {
                SpellAbility sa=item.getSpellAbility();
                if(sa.getId()==id && "Corpse Augur".equals(sa.getHostCard().getName())) { return sa; }
            }
            return null;
        }
        void snapshot(String stage,SpellAbility sa,boolean fizzled) {
            Map<String,Object> row=new LinkedHashMap<>(); Player actor=sa.getActivatingPlayer();
            row.put("stage",stage); row.put("abilityId",sa.getId()); row.put("sourceId",sa.getHostCard().getId());
            row.put("spell",sa.isSpell()); row.put("trigger",sa.isTrigger()); row.put("turn",game.getPhaseHandler().getTurn());
            row.put("player",actor.getName()); row.put("fizzled",fizzled); row.put("sourceZone",sa.getHostCard().getZone().getZoneType().name());
            row.put("life",actor.getLife()); row.put("library",actor.getCardsIn(ZoneType.Library).size()); row.put("hand",actor.getCardsIn(ZoneType.Hand).size());
            row.put("target",sa.getTargets().getFirstTargetedPlayer()==null?"":sa.getTargets().getFirstTargetedPlayer().getName());
            List<Map<String,Object>> players=new ArrayList<>();
            for(Player p:game.getPlayers()) {
                Map<String,Object> entry=new LinkedHashMap<>(); entry.put("player",p.getName()); entry.put("legal",sa.canTarget(p));
                entry.put("creaturesInGraveyard",p.getCardsIn(ZoneType.Graveyard).stream().filter(Card::isCreature).count());
                entry.put("life",p.getLife()); entry.put("hand",p.getCardsIn(ZoneType.Hand).size()); entry.put("library",p.getCardsIn(ZoneType.Library).size()); players.add(entry);
            }
            row.put("players",players); row.put("bodyOnBattlefield",actor.getCreaturesInPlay().stream().anyMatch(c->c.getId()==sa.getHostCard().getId()));
            System.out.println("AUGUR_WITNESS "+new Gson().toJson(row));
        }
        @Subscribe public void cast(GameEventSpellAbilityCast e) { SpellAbility sa=find(e.sa().getId()); if(sa!=null) { snapshot("stacked",sa,false); } }
        @Subscribe public void resolved(GameEventSpellResolved e) { SpellAbility sa=find(e.spell().getId()); if(sa!=null) { snapshot("resolved",sa,e.hasFizzled()); } }
    }
    public static void main(String[] args) throws Exception {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null); MyRandom.setRandom(new Random(Long.parseLong(args[0])));
        AiOverrideProfile.setActive(args[1].equals("on")?"corpse-augur":"");
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            Deck deck=DeckSerializer.fromFile(new File(args[i==0?3:4]));
            LobbyPlayerAi lobby=new LobbyPlayerAi("AugurProbe-"+i,null); lobby.setAiProfile("Default");
            players.add(new RegisteredPlayer(deck).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Constructed); rules.setGamesPerMatch(1);
        Match match=new Match(rules,players,"Corpse Augur custom card-use probe"); Game game=match.createGame(); game.setNoGUIUser();
        game.subscribeToEvents(new Observer(game)); System.out.println("AUGUR_PROBE seed="+args[0]+" profile="+args[1]+" clock="+args[2]);
        try { TimeLimitedCodeBlock.runWithTimeout(()->match.startGame(game),Integer.parseInt(args[2]),TimeUnit.SECONDS); }
        catch(TimeoutException expected) { System.out.println("AUGUR_PROBE_CLOCK_STOP"); }
        finally { if(!game.isGameOver()) { game.setGameOver(GameEndReason.Draw); } }
        List<GameLogEntry> entries=game.getGameLog().getLogEntries(null); Collections.reverse(entries);
        for(GameLogEntry entry:entries) { System.out.println(entry); }
        System.out.println("AUGUR_PROBE_DONE card-use only; no result evaluation");
    }
}
