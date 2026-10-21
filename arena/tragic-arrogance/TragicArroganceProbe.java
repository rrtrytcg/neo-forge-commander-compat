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

/** Read-only native event observer; does not choose, plan or force gameplay. */
public class TragicArroganceProbe {
    public static class Observer {
        final Game game;
        final Map<Integer,List<Integer>> sacrifices=new HashMap<>();
        Observer(Game game) { this.game=game; }
        SpellAbility find(int id) {
            for(SpellAbilityStackInstance item:game.getStack()) {
                SpellAbility sa=item.getSpellAbility();
                if(sa.getId()==id && sa.isSpell() && "Tragic Arrogance".equals(sa.getHostCard().getName())) { return sa; }
            }
            return null;
        }
        void snapshot(String stage,SpellAbility sa,boolean fizzled) {
            Player caster=sa.getActivatingPlayer(); List<Map<String,Object>> board=new ArrayList<>();
            for(Player player:game.getPlayers()) {
                List<Map<String,Object>> cards=new ArrayList<>();
                for(Card card:player.getCardsIn(ZoneType.Battlefield)) {
                    int mask=0; String[] types={"Artifact","Creature","Enchantment","Planeswalker"};
                    for(int i=0;i<4;i++) { if(card.getType().hasStringType(types[i])) { mask|=1<<i; } }
                    Map<String,Object> state=new LinkedHashMap<>(); state.put("id",card.getId()); state.put("name",card.getName());
                    state.put("types",mask); state.put("land",card.isLand()); state.put("value",card.getCMC()+1);
                    state.put("sacrificable",card.canBeSacrificedBy(sa.getSubAbility(),true));
                    state.put("indestructible",card.hasKeyword(Keyword.INDESTRUCTIBLE)); cards.add(state);
                }
                Map<String,Object> state=new LinkedHashMap<>(); state.put("id",player.getId()); state.put("name",player.getName());
                state.put("opponent",player.isOpponentOf(caster)); state.put("cards",cards); board.add(state);
            }
            Map<String,Object> row=new LinkedHashMap<>(); row.put("stage",stage); row.put("abilityId",sa.getId());
            row.put("sourceId",sa.getHostCard().getId()); row.put("turn",game.getPhaseHandler().getTurn());
            row.put("player",caster.getName()); row.put("fizzled",fizzled); row.put("board",board);
            row.put("rememberedEmpty",!sa.getHostCard().getRemembered().iterator().hasNext());
            row.put("nativeSacrifices",sacrifices.getOrDefault(sa.getId(),Collections.emptyList()));
            System.out.println("ARROGANCE_WITNESS "+new Gson().toJson(row));
        }
        @Subscribe public void cast(GameEventSpellAbilityCast event) {
            SpellAbility sa=find(event.sa().getId());
            if(sa!=null) { sacrifices.put(sa.getId(),new ArrayList<>()); snapshot("stacked",sa,false); }
        }
        @Subscribe public void sacrificed(GameEventCardSacrificed event) {
            if(game.getStack().isEmpty()) { return; }
            SpellAbility sa=find(game.getStack().peekAbility().getId());
            if(sa!=null && sacrifices.containsKey(sa.getId())) { sacrifices.get(sa.getId()).add(event.card().getId()); }
        }
        @Subscribe public void resolved(GameEventSpellResolved event) {
            SpellAbility sa=find(event.spell().getId());
            if(sa!=null) { snapshot("resolved",sa,event.hasFizzled()); sacrifices.remove(sa.getId()); }
        }
    }
    public static void main(String[] args) throws Exception {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null);
        MyRandom.setRandom(new Random(Long.parseLong(args[0]))); AiOverrideProfile.setActive(args[1].equals("on")?"tragic-arrogance":"");
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            Deck deck=DeckSerializer.fromFile(new File(args[i==0?3:4]));
            LobbyPlayerAi lobby=new LobbyPlayerAi("ArroganceProbe-"+i,null); lobby.setAiProfile("Default");
            players.add(new RegisteredPlayer(deck).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Constructed); rules.setGamesPerMatch(1);
        Match match=new Match(rules,players,"Tragic Arrogance custom card-use probe"); Game game=match.createGame(); game.setNoGUIUser();
        game.subscribeToEvents(new Observer(game));
        System.out.println("ARROGANCE_PROBE seed="+args[0]+" profile="+args[1]+" clock="+args[2]);
        try { TimeLimitedCodeBlock.runWithTimeout(()->match.startGame(game),Integer.parseInt(args[2]),TimeUnit.SECONDS); }
        catch(TimeoutException expected) { System.out.println("ARROGANCE_PROBE_CLOCK_STOP"); }
        finally { if(!game.isGameOver()) { game.setGameOver(GameEndReason.Draw); } }
        List<GameLogEntry> entries=game.getGameLog().getLogEntries(null); Collections.reverse(entries);
        for(GameLogEntry entry:entries) { System.out.println(entry); }
        System.out.println("ARROGANCE_PROBE_DONE card-use only; no result evaluation");
    }
}
