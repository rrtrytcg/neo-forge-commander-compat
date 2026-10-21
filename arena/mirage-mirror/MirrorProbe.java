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

/** Read-only events and public characteristics; no policy calls, injected actions or phase changes. */
public class MirrorProbe {
    public static class Observer {
        final Game game;int sequence;final Map<Integer,Map<String,Object>> pending=new HashMap<>();
        Observer(Game g){game=g;}
        boolean mirror(Card c){return c.getPaperCard()!=null && c.getPaperCard().getName().equals("Mirage Mirror");}
        SpellAbility find(int id){for(var e:game.getStack()){if(e.getSpellAbility().getId()==id){return e.getSpellAbility();}}return null;}
        Map<String,Object> card(Card c){Map<String,Object> r=new LinkedHashMap<>();r.put("id",c.getId());r.put("name",c.getName());r.put("original",c.getPaperCard()==null?"":c.getPaperCard().getName());r.put("controller",c.getController().getName());r.put("zone",String.valueOf(c.getZone().getZoneType()));r.put("stamp",c.getGameTimestamp());r.put("creature",c.isCreature());r.put("artifact",c.isArtifact());r.put("cloned",c.isCloned());r.put("tapped",c.isTapped());r.put("sick",c.isSick());r.put("damage",c.getDamage());r.put("flying",c.hasKeyword(Keyword.FLYING));r.put("vigilance",c.hasKeyword(Keyword.VIGILANCE));r.put("hasClone",c.getSpellAbilities().stream().anyMatch(s->s.getApi()==ApiType.Clone));if(c.isCreature()){r.put("power",c.getNetPower());r.put("toughness",c.getNetToughness());r.put("value",ComputerUtilCard.evaluateCreature(c));}return r;}
        List<Map<String,Object>> mirrors(){List<Map<String,Object>> r=new ArrayList<>();for(Card c:game.getCardsIn(ZoneType.Battlefield)){if(mirror(c)){r.add(card(c));}}return r;}
        Map<String,Object> combat(){Map<String,Object> r=new LinkedHashMap<>();List<Map<String,Object>> attackers=new ArrayList<>(),blocks=new ArrayList<>();var co=game.getCombat();if(co!=null){for(Card c:co.getAttackers()){Map<String,Object> a=card(c);GameEntity d=co.getDefenderByAttacker(c);a.put("defender",d==null?"":d.getName());attackers.add(a);for(Card b:co.getBlockers(c)){Map<String,Object> pair=new LinkedHashMap<>();pair.put("attacker",card(c));pair.put("blocker",card(b));blocks.add(pair);}}}r.put("attackers",attackers);r.put("blocks",blocks);return r;}
        Map<String,Object> base(String kind){Map<String,Object> r=new LinkedHashMap<>();r.put("kind",kind);r.put("phase",String.valueOf(game.getPhaseHandler().getPhase()));r.put("turn",game.getPhaseHandler().getTurn());r.put("turnPlayer",game.getPhaseHandler().getPlayerTurn().getName());r.put("mirrors",mirrors());r.put("combat",combat());return r;}
        Map<String,Object> row(SpellAbility s,String kind){Map<String,Object> r=base(kind);r.put("abilityId",s.getId());r.put("source",card(s.getHostCard()));r.put("api",String.valueOf(s.getApi()));r.put("spell",s.isSpell());r.put("player",s.getActivatingPlayer().getName());r.put("stackSize",game.getStack().size());List<Map<String,Object>> targets=new ArrayList<>();for(Card c:s.getTargets().getTargetCards()){targets.add(card(game.getCardState(c,null)));}r.put("targets",targets);List<Map<String,Object>> mana=new ArrayList<>();for(var m:s.getPayingMana()){mana.add(Map.of("color",m.toString(),"source",m.getSourceCard().getName(),"sourceId",m.getSourceCard().getId()));}r.put("mana",mana);return r;}
        void emit(Map<String,Object> r){r.put("event",++sequence);System.out.println("MIRROR_WITNESS "+new Gson().toJson(r));}
        @Subscribe public void cast(GameEventSpellAbilityCast e){SpellAbility s=find(e.sa().getId());if(s==null || !s.getActivatingPlayer().getName().equals("MirrorProbe-0") || !(s.isSpell() || s.getApi()==ApiType.Clone && mirror(s.getHostCard()))){return;}Map<String,Object> r=row(s,"cast");emit(r);pending.put(s.getId(),r);}
        @Subscribe public void resolved(GameEventSpellResolved e){SpellAbility s=find(e.spell().getId());if(s==null||!pending.containsKey(s.getId())){return;}Map<String,Object> r=row(s,"resolved");r.put("before",pending.remove(s.getId()));r.put("fizzled",e.hasFizzled());emit(r);}
        @Subscribe public void attacks(GameEventAttackersDeclared e){if(!mirrors().isEmpty()){emit(base("attackers"));}}
        @Subscribe public void blocks(GameEventBlockersDeclared e){if(!mirrors().isEmpty()){emit(base("blockers"));}}
        @Subscribe public void phase(GameEventTurnPhase e){if(!mirrors().isEmpty()){emit(base("phase"));}}
    }
    public static void main(String[] args)throws Exception{
        GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(Long.parseLong(args[0])));AiOverrideProfile.setActive(args[1].equals("on")?"mirage-mirror":"");List<RegisteredPlayer> players=new ArrayList<>();for(int i=0;i<4;i++){Deck d=DeckSerializer.fromFile(new File(args[i==0?3:4]));LobbyPlayerAi l=new LobbyPlayerAi("MirrorProbe-"+i,null);l.setAiProfile("Default");players.add(RegisteredPlayer.forCommander(d).setPlayer(l));}GameRules rules=new GameRules(GameType.Commander);rules.addAppliedVariant(GameType.Commander);rules.setGamesPerMatch(1);Match match=new Match(rules,players,"Mirage Mirror custom Commander probe");Game game=match.createGame();game.setNoGUIUser();game.subscribeToEvents(new Observer(game));System.out.println("MIRROR_PROBE seed="+args[0]+" profile="+args[1]+" clock="+args[2]);try{TimeLimitedCodeBlock.runWithTimeout(()->match.startGame(game),Integer.parseInt(args[2]),TimeUnit.SECONDS);}catch(TimeoutException expected){System.out.println("MIRROR_PROBE_CLOCK_STOP");}finally{if(!game.isGameOver()){game.setGameOver(GameEndReason.Draw);}}List<GameLogEntry> entries=game.getGameLog().getLogEntries(null);Collections.reverse(entries);for(GameLogEntry e:entries){System.out.println(e);}System.out.println("MIRROR_PROBE_DONE card-use only; no winner or completion claim");
    }
}
