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

/** Native Match and read-only event subscriber; no forced gameplay or policy calls. */
public class KenrithsTransformationProbe {
    public static class Observer {
        final Game game;
        int witnessId;
        final Map<Integer,Integer> witnesses=new HashMap<>();
        final Map<Integer,Map<String,Object>> drawBefore=new HashMap<>();
        Observer(Game game) { this.game=game; }
        SpellAbility find(int id) {
            for(SpellAbilityStackInstance item:game.getStack()) {
                SpellAbility sa=item.getSpellAbility();
                if(sa.getId()==id && "Kenrith's Transformation".equals(sa.getHostCard().getName())) { return sa; }
            }
            return null;
        }
        List<Map<String,Object>> cards(Iterable<Card> cards) {
            List<Map<String,Object>> result=new ArrayList<>();
            for(Card card:cards) {
                Map<String,Object> row=new LinkedHashMap<>(); row.put("id",card.getId()); row.put("name",card.getName());
                row.put("power",card.getNetPower()); row.put("toughness",card.getNetToughness()); row.put("tapped",card.isTapped());
                row.put("creature",card.isCreature()); row.put("controller",card.getController().getName()); row.put("basePower",card.getCurrentPower()); row.put("baseToughness",card.getCurrentToughness()); row.put("artifact",card.isArtifact()); row.put("elk",card.getType().hasSubtype("Elk")); row.put("green",card.getColor().isMonoColor() && card.getColor().hasGreen()); row.put("indestructible",card.hasKeyword(Keyword.INDESTRUCTIBLE)); row.put("abilities",card.getSpellAbilities().stream().filter(a->a.isAbility()).count()); row.put("manaAbilities",card.getManaAbilities().size()); result.add(row);
            }
            return result;
        }
        void snapshot(String stage,SpellAbility sa,boolean fizzled) {
            Player player=sa.getActivatingPlayer(); Map<String,Object> row=new LinkedHashMap<>();
            row.put("stage",stage); row.put("api",String.valueOf(sa.getApi()));
            if(stage.equals("stacked") && sa.isSpell()) { witnesses.put(sa.getId(),++witnessId); }
            row.put("witnessId",witnesses.get(sa.getId()));
            row.put("librarySize",player.getCardsIn(ZoneType.Library).size()); row.put("handSize",player.getCardsIn(ZoneType.Hand).size()); row.put("life",player.getLife());
            if(stage.equals("resolved") && sa.isSpell()) { drawBefore.put(sa.getHostCard().getId(),new LinkedHashMap<>(row)); }
            if(stage.equals("resolved") && !sa.isSpell()) { row.put("beforeDraw",drawBefore.remove(sa.getHostCard().getId())); } row.put("abilityId",sa.getId()); row.put("sourceId",sa.getHostCard().getId());
            row.put("spell",sa.isSpell()); row.put("trigger",sa.isTrigger()); row.put("turn",game.getPhaseHandler().getTurn());
            row.put("player",player.getName()); row.put("fizzled",fizzled);
            row.put("attachedTo",sa.getHostCard().getEnchantingCard()==null?-1:sa.getHostCard().getEnchantingCard().getId());
            row.put("creatures",cards(player.getCreaturesInPlay())); row.put("graveyard",cards(player.getCardsIn(ZoneType.Graveyard)));
            row.put("lands",cards(player.getLandsInPlay())); row.put("targets",cards(sa.getTargets().getTargetCards()));
            row.put("auraOnBattlefield",player.getCardsIn(ZoneType.Battlefield).stream().anyMatch(c->c.getId()==sa.getHostCard().getId()));
            System.out.println("TRANSFORM_WITNESS "+new Gson().toJson(row));
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
        MyRandom.setRandom(new Random(Long.parseLong(args[0]))); AiOverrideProfile.setActive(args[1].equals("on")?"kenriths-transformation":"");
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            Deck deck=DeckSerializer.fromFile(new File(args[i==0?3:4]));
            LobbyPlayerAi lobby=new LobbyPlayerAi("TransformationProbe-"+i,null); lobby.setAiProfile("Default");
            players.add(new RegisteredPlayer(deck).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Constructed); rules.setGamesPerMatch(1);
        Match match=new Match(rules,players,"Kenrith's Transformation custom card-use probe"); Game game=match.createGame(); game.setNoGUIUser();
        game.subscribeToEvents(new Observer(game));
        System.out.println("TRANSFORM_PROBE seed="+args[0]+" profile="+args[1]+" clock="+args[2]);
        try { TimeLimitedCodeBlock.runWithTimeout(()->match.startGame(game),Integer.parseInt(args[2]),TimeUnit.SECONDS); }
        catch(TimeoutException expected) { System.out.println("TRANSFORM_PROBE_CLOCK_STOP"); }
        finally { if(!game.isGameOver()) { game.setGameOver(GameEndReason.Draw); } }
        List<GameLogEntry> entries=game.getGameLog().getLogEntries(null); Collections.reverse(entries);
        for(GameLogEntry entry:entries) { System.out.println(entry); }
        System.out.println("TRANSFORM_PROBE_DONE card-use only; no result evaluation");
    }
}


