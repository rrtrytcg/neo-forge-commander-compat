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

/** Native Match and read-only event subscriber for one card; no forced gameplay or policy calls. */
public class CardUseProbe {
    public static class Observer {
        final Game game;
        final String card;
        final Map<Integer,Map<String,Object>> before=new HashMap<>();
        forge.game.combat.Combat lastCombat;
        int combatId;
        int combatId() {if(game.getCombat()!=lastCombat) {lastCombat=game.getCombat(); combatId++;} return combatId;}
        Observer(Game game,String card) { this.game=game; this.card=card; }
        SpellAbility find(int id) {
            for(SpellAbilityStackInstance item:game.getStack()) {
                SpellAbility sa=item.getSpellAbility();
                if(sa.getId()==id && sa.getHostCard().getName().startsWith(card)) { return sa; }
            }
            return null;
        }
        List<Map<String,Object>> players() {
            List<Map<String,Object>> result=new ArrayList<>();
            for(Player p:game.getPlayers()) {
                Map<String,Object> row=new LinkedHashMap<>();
                row.put("player",p.getName()); row.put("life",p.getLife());
                row.put("handSize",p.getCardsIn(ZoneType.Hand).size());
                row.put("librarySize",p.getCardsIn(ZoneType.Library).size());
                row.put("hand",p.getCardsIn(ZoneType.Hand).stream().map(Card::getName).toList());
                row.put("graveyard",p.getCardsIn(ZoneType.Graveyard).stream().map(Card::getName).toList());
                List<Map<String,Object>> field=new ArrayList<>();
                for(Card c:p.getCardsIn(ZoneType.Battlefield)) {
                    Map<String,Object> b=new LinkedHashMap<>();
                    b.put("id",c.getId()); b.put("name",c.getName()); b.put("types",c.getType().toString());
                    b.put("cmc",c.getCMC()); b.put("controller",c.getController().getName()); b.put("token",c.isToken());
                    b.put("power",c.getNetCombatDamage()); b.put("firstStrike",c.hasFirstStrike()); b.put("doubleStrike",c.hasDoubleStrike());
                    b.put("attacking",game.getCombat()!=null && game.getCombat().isAttacking(c));
                    b.put("unblocked",game.getCombat()!=null && game.getCombat().isUnblocked(c));
                    field.add(b);
                }
                row.put("battlefield",field);
                result.add(row);
            }
            return result;
        }
        void snapshot(SpellAbility sa) {
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("kind","cast"); row.put("abilityId",sa.getId()); row.put("turn",game.getPhaseHandler().getTurn());
            row.put("phase",String.valueOf(game.getPhaseHandler().getPhase()));
            row.put("player",sa.getActivatingPlayer().getName()); row.put("spell",sa.isSpell());
            row.put("trigger",sa.isTrigger()); row.put("api",String.valueOf(sa.getApi()));
            row.put("x",sa.getXManaCostPaid());
            row.put("combatId",combatId());
            row.put("draw",sa.getApi()==forge.game.ability.ApiType.Draw?forge.game.ability.AbilityUtils.calculateAmount(sa.getHostCard(),sa.getParamOrDefault("NumCards","1"),sa):0);
            List<Map<String,Object>> targets=new ArrayList<>();
            for(Card c:sa.getTargets().getTargetCards()) {
                Map<String,Object> t=new LinkedHashMap<>(); t.put("id",c.getId()); t.put("name",c.getName()); targets.add(t);
                t.put("power",c.getNetCombatDamage()); t.put("unblocked",game.getCombat()!=null && game.getCombat().isUnblocked(c));
                t.put("predicted",c.getNetCombatDamage());
            }
            row.put("targets",targets); row.put("players",players());
            before.put(sa.getId(),row);
            System.out.println("CARD_WITNESS "+new Gson().toJson(row));
        }
        @Subscribe public void cast(GameEventSpellAbilityCast event) {
            SpellAbility sa=find(event.sa().getId());
            if(sa!=null) { snapshot(sa); }
        }
        @Subscribe public void resolved(GameEventSpellResolved event) {
            SpellAbility sa=find(event.spell().getId());
            if(sa==null) { return; }
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("kind","resolved"); row.put("abilityId",sa.getId());
            row.put("api",String.valueOf(sa.getApi())); row.put("x",sa.getXManaCostPaid());
            row.put("turn",game.getPhaseHandler().getTurn()); row.put("fizzled",event.hasFizzled());
            List<Map<String,Object>> targets=new ArrayList<>();
            for(Card c:sa.getTargets().getTargetCards()) {
                Map<String,Object> t=new LinkedHashMap<>(); t.put("id",c.getId()); t.put("name",c.getName()); targets.add(t);
            }
            row.put("targets",targets); row.put("players",players());
            row.put("before",before.remove(sa.getId()));
            System.out.println("CARD_WITNESS "+new Gson().toJson(row));
        }
        @Subscribe public void damage(GameEventPlayerDamaged event) {
            if(event.combat()) {
                System.out.println("COMBAT_DAMAGE "+new Gson().toJson(Map.of("turn",game.getPhaseHandler().getTurn(),
                        "combatId",combatId(),"sourceId",event.source().getId(),"source",event.source().getName(),
                        "target",event.target().getName(),"amount",event.amount())));
            }
        }
    }
    public static void main(String[] args) throws Exception {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null);
        MyRandom.setRandom(new Random(Long.parseLong(args[0])));
        String mode=args[1];
        String profile=args[2];
        String card=args[3];
        AiOverrideProfile.setActive("on".equals(mode)?profile:"");
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            Deck deck=DeckSerializer.fromFile(new File(args[i==0?5:6]));
            LobbyPlayerAi lobby=new LobbyPlayerAi(card.replace(", ","-")+"-"+i,null); lobby.setAiProfile("Default");
            players.add(new RegisteredPlayer(deck).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Constructed); rules.setGamesPerMatch(1);
        Match match=new Match(rules,players,card+" custom card-use probe"); Game game=match.createGame(); game.setNoGUIUser();
        game.subscribeToEvents(new Observer(game,card));
        System.out.println("CARD_PROBE card="+card+" seed="+args[0]+" mode="+mode+" clock="+args[4]);
        try { TimeLimitedCodeBlock.runWithTimeout(()->match.startGame(game),Integer.parseInt(args[4]),TimeUnit.SECONDS); }
        catch(TimeoutException expected) { System.out.println("CARD_PROBE_CLOCK_STOP"); }
        finally { if(!game.isGameOver()) { game.setGameOver(GameEndReason.Draw); } }
        List<GameLogEntry> entries=game.getGameLog().getLogEntries(null); Collections.reverse(entries);
        for(GameLogEntry entry:entries) { System.out.println(entry); }
        System.out.println("CARD_PROBE_DONE card-use only; no result evaluation");
    }
}
