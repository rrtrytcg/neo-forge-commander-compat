import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.*;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Native baseline, profile isolation, and storage-counter payment checks. */
public class MoltenSlagheapTests {
    static Game game; static Player ai; static Card land; static SpellAbility mana; static int passed;
    static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); passed++; System.out.println("PASS " + label); }
    static Player p(int i) { return game.getPlayers().get(i); }
    static Card add(String name, Player owner, ZoneType zone) { Card c=Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name),owner); c.setGameTimestamp(game.getNextTimestamp()); owner.getZone(zone).add(c); c.setSickness(false); return c; }
    static void refresh() { game.getAction().checkStaticAbilities(); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState(); }
    static void setup(String profile, int externalLands, int counters, boolean followup, String followupCard) {
        AiOverrideProfile.setActive(profile);
        List<RegisteredPlayer> regs=new ArrayList<>(); for(int i=0;i<2;i++){LobbyPlayerAi lobby=new LobbyPlayerAi("Molten fixture "+i,null);lobby.setAiProfile("Default");regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));}
        GameRules rules=new GameRules(GameType.Constructed);game=new Game(regs,rules,new Match(rules,regs,"Molten Slagheap fixtures"));game.setAge(GameStage.Play);ai=p(0);game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);
        for(int i=0;i<externalLands;i++) add("Forest",ai,ZoneType.Battlefield);
        for(Player player:game.getPlayers()) for(int i=0;i<30;i++) add("Forest",player,ZoneType.Library);
        land=add("Molten Slagheap",ai,ZoneType.Battlefield); if(counters>0) land.addCounterInternal(CounterEnumType.STORAGE,counters,ai,false,null,null); mana=land.getSpellAbilities().stream().filter(s->s.getPayCosts().hasSpecificCostType(forge.game.cost.CostRemoveCounter.class)).findFirst().orElseThrow();mana.setActivatingPlayer(ai);
        if(followup) add(followupCard,ai,ZoneType.Hand); refresh();
    }
    static boolean willing(){ return SpellApiToAi.Converter.get(mana).canPlayWithSubs(ai,mana).willingToPlay(); }
    static SpellAbility chosen(){List<SpellAbility> a=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();return a==null||a.isEmpty()?null:a.get(0);}
    static void baseline(){setup("",4,2,true,"Craw Wurm");check(!willing(),"baseline: native Mana AI declines stored-counter ability without AILogic");check(!ComputerUtilMana.getAIPlayableMana(land).contains(mana),"baseline: native payment source filter excludes mana abilities with a mana cost");}
    static void enablesCastAndPaysOnlyNeededCounters(){setup("molten-slagheap",1,4,true,"Hedron Archive");check("ManaRitualBattery.0".equals(AiOverrideProfile.get().getAiLogic(land)),"profile attaches existing battery logic to Molten only");check(ComputerUtilMana.getAIPlayableMana(land).contains(mana),"profile admits the storage ability to native mana-payment source selection");check(willing(),"activates when stored mana enables a castable noninstant");check(mana.getXManaCostPaid()==4,"uses only the four counters needed beyond the land tap and activation cost");SpellAbility archive=ai.getCardsIn(ZoneType.Hand).stream().filter(c->c.getName().equals("Hedron Archive")).findFirst().orElseThrow().getFirstSpellAbility();archive.setActivatingPlayer(ai);check(ComputerUtil.playStack(archive,ai,game),"native spell payment activates Molten Slagheap");land=game.getCardState(land);check(land.getCounters(CounterEnumType.STORAGE)==0,"native payment spends exactly the selected four storage counters");}
    static void holdsAndIsolation(){setup("molten-slagheap",4,2,false,"");check(!willing(),"holds when no noninstant spell is in hand");setup("molten-slagheap",4,2,true,"Murder");check(!willing(),"holds when only an instant is enabled");setup("molten-slagheap",4,2,true,"Mind Stone");check(!willing(),"holds when ordinary mana already covers the noninstant");setup("molten-slagheap",0,2,true,"Craw Wurm");check(!willing(),"holds when land sources plus stored counters cannot cover activation and cast");for(String profile:List.of("","winter-cynical-opportunist")){setup(profile,4,2,true,"Hedron Archive");check(!"ManaRitualBattery.0".equals(AiOverrideProfile.get().getAiLogic(land))&&!willing()&&!ComputerUtilMana.getAIPlayableMana(land).contains(mana),"profile isolation keeps Molten blocked under '"+profile+"'");}}
    public static void main(String[] args){GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(20261020));if(args.length>0&&"baseline".equals(args[0]))baseline();else{enablesCastAndPaysOnlyNeededCounters();holdsAndIsolation();}AiOverrideProfile.setActive("");System.out.println("DONE "+passed+" Molten Slagheap checks");}
}
