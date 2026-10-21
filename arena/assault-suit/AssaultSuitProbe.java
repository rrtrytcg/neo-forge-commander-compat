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
public class AssaultSuitProbe {
    public static class Observer {
        final Game game;
        final Map<Integer,String> equippedBefore=new HashMap<>();
        Observer(Game game) { this.game=game; }
        SpellAbility find(int id) {
            for(SpellAbilityStackInstance item:game.getStack()) {
                SpellAbility sa=item.getSpellAbility();
                if(sa.getId()==id && "Assault Suit".equals(sa.getHostCard().getName())) { return sa; }
            }
            return null;
        }
        List<Map<String,Object>> creatures(Iterable<Card> cards) {
            List<Map<String,Object>> result=new ArrayList<>();
            for(Card card:cards) {
                Map<String,Object> row=new LinkedHashMap<>();
                row.put("id",card.getId()); row.put("name",card.getName());
                row.put("power",card.getNetPower()); row.put("toughness",card.getNetToughness());
                row.put("trample",card.hasKeyword(Keyword.TRAMPLE)); row.put("flying",card.hasKeyword(Keyword.FLYING));
                row.put("firstStrike",card.hasKeyword(Keyword.FIRST_STRIKE) || card.hasDoubleStrike());
                row.put("controller",card.getController().getName()); row.put("tapped",card.isTapped());
                result.add(row);
            }
            return result;
        }
        void snapshot(String kind,SpellAbility sa) {
            Player player=sa.getActivatingPlayer();
            Card equipped=sa.getHostCard().getEquipping();
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("kind",kind); row.put("abilityId",sa.getId()); row.put("turn",game.getPhaseHandler().getTurn());
            row.put("phase",String.valueOf(game.getPhaseHandler().getPhase()));
            row.put("player",player.getName()); row.put("spell",sa.isSpell()); row.put("trigger",sa.isTrigger());
            row.put("equip",sa.isEquip()); row.put("availableMana",ComputerUtilMana.getAvailableManaEstimate(player,true));
            row.put("untappedLands",player.getLandsInPlay().stream().filter(Card::isUntapped).count());
            row.put("targets",creatures(sa.getTargets().getTargetCards()));
            row.put("ownCreatures",creatures(player.getCreaturesInPlay()));
            row.put("equippedId",equipped==null?-1:equipped.getId());
            row.put("equippedName",equipped==null?"":equipped.getName());
            row.put("equippedController",equipped==null?"":equipped.getController().getName());
            if(equipped!=null) { equippedBefore.put(sa.getId(),equipped.getController().getName()); }
            System.out.println("SUIT_WITNESS "+new Gson().toJson(row));
        }
        @Subscribe public void cast(GameEventSpellAbilityCast event) {
            SpellAbility sa=find(event.sa().getId());
            if(sa!=null) {
                String kind=sa.isEquip()?"equip":sa.isTrigger()?"trigger":sa.isSpell()?"cast":"activate";
                snapshot(kind,sa);
            }
        }
        @Subscribe public void resolved(GameEventSpellResolved event) {
            SpellAbility sa=find(event.spell().getId());
            if(sa==null) { return; }
            Card equipped=sa.getHostCard().getEquipping();
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("kind","resolved"); row.put("abilityId",sa.getId());
            row.put("source",sa.isTrigger()?"trigger":sa.isEquip()?"equip":sa.isSpell()?"cast":"activate");
            row.put("turn",game.getPhaseHandler().getTurn()); row.put("fizzled",event.hasFizzled());
            row.put("equippedId",equipped==null?-1:equipped.getId());
            row.put("equippedName",equipped==null?"":equipped.getName());
            row.put("equippedController",equipped==null?"":equipped.getController().getName());
            row.put("controllerBefore",equippedBefore.getOrDefault(sa.getId(),""));
            System.out.println("SUIT_WITNESS "+new Gson().toJson(row));
        }
        @Subscribe public void attached(GameEventCardAttachment event) {
            if(!"Assault Suit".equals(event.equipment().getName())) { return; }
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("kind","attached"); row.put("turn",game.getPhaseHandler().getTurn());
            row.put("phase",String.valueOf(game.getPhaseHandler().getPhase()));
            row.put("equipmentId",event.equipment().getId());
            row.put("oldTarget",event.oldEntity()==null?null:event.oldEntity().getName());
            row.put("newTarget",event.newTarget()==null?null:event.newTarget().getName());
            row.put("newTargetId",event.newTarget()==null?-1:event.newTarget().getId());
            System.out.println("SUIT_WITNESS "+new Gson().toJson(row));
        }
    }
    public static void main(String[] args) throws Exception {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null);
        MyRandom.setRandom(new Random(Long.parseLong(args[0])));
        AiOverrideProfile.setActive(args[1].equals("on")?"assault-suit":"");
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            Deck deck=DeckSerializer.fromFile(new File(args[i==0?3:4]));
            LobbyPlayerAi lobby=new LobbyPlayerAi("SuitProbe-"+i,null); lobby.setAiProfile("Default");
            players.add(new RegisteredPlayer(deck).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Constructed); rules.setGamesPerMatch(1);
        Match match=new Match(rules,players,"Assault Suit custom card-use probe"); Game game=match.createGame(); game.setNoGUIUser();
        game.subscribeToEvents(new Observer(game));
        System.out.println("SUIT_PROBE seed="+args[0]+" profile="+args[1]+" clock="+args[2]);
        try { TimeLimitedCodeBlock.runWithTimeout(()->match.startGame(game),Integer.parseInt(args[2]),TimeUnit.SECONDS); }
        catch(TimeoutException expected) { System.out.println("SUIT_PROBE_CLOCK_STOP"); }
        finally { if(!game.isGameOver()) { game.setGameOver(GameEndReason.Draw); } }
        List<GameLogEntry> entries=game.getGameLog().getLogEntries(null); Collections.reverse(entries);
        for(GameLogEntry entry:entries) { System.out.println(entry); }
        System.out.println("SUIT_PROBE_DONE card-use only; no result evaluation");
    }
}
