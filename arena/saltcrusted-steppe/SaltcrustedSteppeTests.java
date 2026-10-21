import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.game.*;
import forge.game.card.*;
import forge.game.cost.CostRemoveCounter;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.deck.Deck;
import forge.util.MyRandom;
import java.util.*;

/** Native Saltcrusted Steppe mana-evaluation, profile and payment fixtures. */
public class SaltcrustedSteppeTests {
    static Game game; static Player ai; static Card steppe; static SpellAbility storage; static int passed;
    static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); passed++; System.out.println("PASS " + label); }
    static Player p(int i) { return game.getPlayers().get(i); }
    static Card add(String name, Player owner, ZoneType zone) {
        Card c=Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name),owner);
        c.setGameTimestamp(game.getNextTimestamp()); owner.getZone(zone).add(c); c.setSickness(false); return c;
    }
    static void refresh() { game.getAction().checkStaticAbilities(); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState(); }
    static void setup(String profile, int plains, int counters, String followup) {
        AiOverrideProfile.setActive(profile);
        List<RegisteredPlayer> regs=new ArrayList<>();
        for(int i=0;i<2;i++){LobbyPlayerAi lobby=new LobbyPlayerAi("Saltcrusted fixture "+i,null);lobby.setAiProfile("Default");regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));}
        GameRules rules=new GameRules(GameType.Constructed); game=new Game(regs,rules,new Match(rules,regs,"Saltcrusted Steppe fixtures"));
        game.setAge(GameStage.Play); ai=p(0); game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);
        for(int i=0;i<plains;i++) add("Plains",ai,ZoneType.Battlefield);
        for(Player player:game.getPlayers()) for(int i=0;i<30;i++) add("Forest",player,ZoneType.Library);
        steppe=add("Saltcrusted Steppe",ai,ZoneType.Battlefield);
        if(counters>0) steppe.addCounterInternal(CounterEnumType.STORAGE,counters,ai,false,null,null);
        storage=steppe.getSpellAbilities().stream().filter(s->s.getPayCosts().hasSpecificCostType(CostRemoveCounter.class)).findFirst().orElseThrow();
        storage.setActivatingPlayer(ai);
        if(followup!=null&&!followup.isEmpty()) add(followup,ai,ZoneType.Hand);
        refresh();
    }
    static boolean willing(){ return SpellApiToAi.Converter.get(storage).canPlayWithSubs(ai,storage).willingToPlay(); }
    static SpellAbility followup(String name){ return ai.getCardsIn(ZoneType.Hand).stream().filter(c->c.getName().equals(name)).findFirst().orElseThrow().getFirstSpellAbility(); }
    static SpellAbility baseMana(){ return steppe.getSpellAbilities().stream().filter(s->!s.getPayCosts().hasSpecificCostType(CostRemoveCounter.class)).findFirst().orElseThrow(); }
    static void baseline(){ setup("",2,4,"Serra Angel"); check(!willing(),"baseline: native Mana AI declines storage-counter ability without AILogic"); check(!ComputerUtilMana.getAIPlayableMana(steppe).contains(storage),"baseline: mana-source filter excludes storage ability with {1} cost"); }
    static void enablesCastAndPaysOnlyNeededCounters(){
        setup("molten-slagheap",2,4,"Serra Angel");
        check("ManaRitualBattery.0".equals(AiOverrideProfile.get().getAiLogic(steppe)),"profile attaches existing battery logic to Saltcrusted Steppe");
        check(ComputerUtilMana.getAIPlayableMana(steppe).contains(storage),"profile admits the storage ability to native mana-payment source selection");
        check(willing(),"activates when stored mana enables a currently castable noninstant");
        check(storage.getXManaCostPaid()!=null&&storage.getXManaCostPaid()==4,"uses exactly four counters after accounting for two untapped Plains and activation cost");
        SpellAbility angel=followup("Serra Angel"); angel.setActivatingPlayer(ai);
        check(ComputerUtil.playStack(angel,ai,game),"native Serra Angel payment activates Saltcrusted Steppe");
        steppe=game.getCardState(steppe);
        check(steppe.getCounters(CounterEnumType.STORAGE)==0,"native payment removes only the four selected storage counters");
        check(!game.getStack().isEmpty()&&game.getStack().peekAbility().getHostCard().getName().equals("Serra Angel"),"native payment places the original noninstant spell on stack");
    }
    static void holdsAndIsolation(){
        setup("molten-slagheap",2,4,""); check(!willing(),"holds when no noninstant spell is in hand");
        setup("molten-slagheap",2,4,"Giant Growth"); check(!willing(),"holds when only an instant can use stored mana");
        setup("molten-slagheap",5,4,"Serra Angel"); check(!willing(),"holds when ordinary mana already covers the spell");
        setup("molten-slagheap",2,2,"Serra Angel"); check(!willing(),"holds when available counters cannot bridge the remaining cost");
        setup("molten-slagheap",0,4,"Serra Angel"); check(!willing(),"holds when activation and spell cannot both be funded");
        for(String profile:List.of("","sylvan-offering")){
            setup(profile,2,4,"Serra Angel");
            check(!"ManaRitualBattery.0".equals(AiOverrideProfile.get().getAiLogic(steppe))&&!willing()&&!ComputerUtilMana.getAIPlayableMana(steppe).contains(storage),"profile isolation keeps Saltcrusted Steppe unavailable under '"+profile+"'");
        }
    }
    public static void main(String[] args){GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(20261020));if(args.length>0&&"baseline".equals(args[0]))baseline();else{enablesCastAndPaysOnlyNeededCounters();holdsAndIsolation();}AiOverrideProfile.setActive("");System.out.println("DONE "+passed+" Saltcrusted Steppe checks");}
}
