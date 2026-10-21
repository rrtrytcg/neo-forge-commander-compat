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

/** Native Match and read-only event subscriber; no forced gameplay or policy calls. */
public class SelvalaProbe {
    public static class Observer {
        final Game game;
        final Map<Integer,Map<String,Object>> before=new HashMap<>();
        Observer(Game game) { this.game=game; }
        SpellAbility find(int id) {
            for(SpellAbilityStackInstance item:game.getStack()) {
                SpellAbility sa=item.getSpellAbility();
                if(sa.getId()==id && "Selvala, Explorer Returned".equals(sa.getHostCard().getName())) { return sa; }
            }
            return null;
        }
        List<Map<String,Object>> players() {
            List<Map<String,Object>> result=new ArrayList<>();
            for(Player p:game.getPlayers()) {
                Map<String,Object> row=new LinkedHashMap<>();
                row.put("player",p.getName()); row.put("life",p.getLife());
                row.put("hand",p.getCardsIn(ZoneType.Hand).size()); row.put("library",p.getCardsIn(ZoneType.Library).size());
                if(!p.getCardsIn(ZoneType.Library).isEmpty()) {
                    Card top=p.getCardsIn(ZoneType.Library).get(0);
                    row.put("top",top.getName()); row.put("topLand",top.isLand());
                } else { row.put("top",""); row.put("topLand",false); }
                result.add(row);
            }
            return result;
        }
        void snapshot(String kind,SpellAbility sa) {
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("kind",kind); row.put("abilityId",sa.getId()); row.put("turn",game.getPhaseHandler().getTurn());
            row.put("phase",String.valueOf(game.getPhaseHandler().getPhase()));
            row.put("player",sa.getActivatingPlayer().getName()); row.put("spell",sa.isSpell());
            row.put("manaAbility",sa.isManaAbility()); row.put("tapped",sa.getHostCard().isTapped());
            row.put("players",players());
            if("parley".equals(kind)) { before.put(sa.getId(),row); }
            System.out.println("SELVALA_WITNESS "+new Gson().toJson(row));
        }
        @Subscribe public void cast(GameEventSpellAbilityCast event) {
            SpellAbility sa=find(event.sa().getId());
            if(sa!=null) { snapshot(sa.isSpell()?"cast":sa.isManaAbility()?"mana":"parley",sa); }
        }
        @Subscribe public void resolved(GameEventSpellResolved event) {
            SpellAbility sa=find(event.spell().getId());
            if(sa==null) { return; }
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("kind","resolved"); row.put("abilityId",sa.getId());
            row.put("source",sa.isSpell()?"cast":sa.isManaAbility()?"mana":"parley");
            row.put("turn",game.getPhaseHandler().getTurn()); row.put("fizzled",event.hasFizzled());
            row.put("players",players());
            Map<String,Object> prior=before.remove(sa.getId());
            row.put("before",prior);
            System.out.println("SELVALA_WITNESS "+new Gson().toJson(row));
        }
    }
    public static void main(String[] args) throws Exception {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null);
        MyRandom.setRandom(new Random(Long.parseLong(args[0])));
        AiOverrideProfile.setActive(args[1].equals("on")?"selvala-explorer-returned":"");
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            Deck deck=DeckSerializer.fromFile(new File(args[i==0?3:4]));
            LobbyPlayerAi lobby=new LobbyPlayerAi("SelvalaProbe-"+i,null); lobby.setAiProfile("Default");
            players.add(new RegisteredPlayer(deck).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Constructed); rules.setGamesPerMatch(1);
        Match match=new Match(rules,players,"Selvala custom card-use probe"); Game game=match.createGame(); game.setNoGUIUser();
        game.subscribeToEvents(new Observer(game));
        System.out.println("SELVALA_PROBE seed="+args[0]+" profile="+args[1]+" clock="+args[2]);
        try { TimeLimitedCodeBlock.runWithTimeout(()->match.startGame(game),Integer.parseInt(args[2]),TimeUnit.SECONDS); }
        catch(TimeoutException expected) { System.out.println("SELVALA_PROBE_CLOCK_STOP"); }
        finally { if(!game.isGameOver()) { game.setGameOver(GameEndReason.Draw); } }
        List<GameLogEntry> entries=game.getGameLog().getLogEntries(null); Collections.reverse(entries);
        for(GameLogEntry entry:entries) { System.out.println(entry); }
        System.out.println("SELVALA_PROBE_DONE card-use only; no result evaluation");
    }
}
