import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.*;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Original card, native selection/payment/targeting/counter replacement and state actions. */
public class NestingGroundsTests {
    static Game game;
    static Player ai;
    static Card land;
    static SpellAbility ability;
    static int passed;
    static final CounterType PLUS=CounterEnumType.P1P1, MINUS=CounterEnumType.M1M1;
    static void check(boolean ok,String label) { if(!ok) { throw new AssertionError(label); } passed++; System.out.println("PASS "+label); }
    static Player p(int i) { return game.getPlayers().get(i); }
    static Card add(String name,Player owner) {
        Card c=Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name),owner);
        c.setGameTimestamp(game.getNextTimestamp()); owner.getZone(ZoneType.Battlefield).add(c); c.setSickness(false); return c;
    }
    static void refresh() { game.getAction().checkStaticAbilities(); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState(); }
    static void setup() {
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) { LobbyPlayerAi lobby=new LobbyPlayerAi("Nesting-"+i,null); lobby.setAiProfile("Default"); players.add(new RegisteredPlayer(new Deck()).setPlayer(lobby)); }
        GameRules rules=new GameRules(GameType.Constructed); game=new Game(players,rules,new Match(rules,players,"Nesting Grounds fixtures"));
        game.setAge(GameStage.Play); ai=p(0); game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);
        land=add("Nesting Grounds",ai); add("Forest",ai);
        ability=land.getSpellAbilities().stream().filter(sa->sa.getApi()==forge.game.ability.ApiType.Pump).findFirst().orElseThrow();
        ability.setActivatingPlayer(ai); AiOverrideProfile.setActive("nesting-grounds"); refresh();
    }
    static Card creature(String name,int seat,CounterType type,int count) { Card c=add(name,p(seat)); if(type!=null) { c.setCounters(type,count); } refresh(); return c; }
    static boolean willing() { return SpellApiToAi.Converter.get(ability).canPlayWithSubs(ai,ability).willingToPlay(); }
    static SpellAbility selection() {
        List<SpellAbility> list=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();
        return list==null?null:list.stream().filter(sa->sa.getHostCard()==land && sa.getApi()==forge.game.ability.ApiType.Pump).findFirst().orElse(null);
    }
    static CounterType choice(List<CounterType> options,Card from,Card to) {
        return SpellApiToAi.Converter.get(ability.getSubAbility()).chooseCounterType(options,ability.getSubAbility(),Map.of("Source",from,"Target",to));
    }
    static void pair(Card from,Card to,String label) {
        check(willing(),label+": API accepts"); check(ability.getTargetCard()==from,label+": coordinated donor");
        check(ability.getSubAbility().getTargetCard()==to,label+": coordinated recipient");
    }
    static void choices() {
        setup(); check(!willing(),"empty board holds");
        Card from=creature("Grizzly Bears",0,PLUS,2), to=creature("Grizzly Bears",0,null,0); pair(from,to,"spread");
        check(choice(List.of(PLUS),from,to).equals(PLUS),"resolution selects supported positive type");
        long timestamp=game.getTimestamp();
        for(int i=0;i<5;i++) { pair(from,to,"repeat preview "+i); }
        check(from.getCounters(PLUS)==2 && to.getCounters(PLUS)==0 && game.getTimestamp()==timestamp && !land.isTapped(),"preview has no live counter, timestamp or tap mutations");
        setup(); from=creature("Grizzly Bears",0,PLUS,1); to=creature("Grizzly Bears",0,null,0); check(!willing(),"neutral one-counter shuffle holds");
        setup(); from=creature("Grizzly Bears",0,PLUS,1); to=creature("Fencing Ace",0,null,0); pair(from,to,"strict evaluation gain without spreading threshold");
        setup(); from=creature("Grizzly Bears",0,PLUS,2); to=creature("Young Wolf",0,null,0); check(!willing(),"never disable useful undying with first positive counter");
        setup(); from=creature("Walking Ballista",0,PLUS,1); to=creature("Grizzly Bears",0,null,0); check(!willing(),"zero-toughness donor cannot lose its last counter");
        setup(); from=creature("Grizzly Bears",0,PLUS,2); from.setDamage(3); to=creature("Grizzly Bears",0,null,0); check(!willing(),"marked damage prevents fatal donor shrink");
        setup(); from=creature("Ornithopter",0,PLUS,2); from.setDamage(1); to=creature("Grizzly Bears",0,null,0); add("Zilortha, Strength Incarnate",ai); refresh();
        check(from.getLethal()==2 && from.getNetToughness()==4,"native lethal-by-power fixture differs from toughness");
        check(!willing(),"native lethal-by-power protects damaged donor");
        setup(); from=creature("Grizzly Bears",1,PLUS,3); to=creature("Grizzly Bears",0,null,0); check(!willing(),"cannot steal opposing counter");
        setup(); from=creature("Grizzly Bears",0,PLUS,2); to=creature("Grizzly Bears",1,null,0); check(!willing(),"never gives opponent positive counter");
        setup(); from=creature("Grizzly Bears",0,MINUS,1); to=creature("Grizzly Bears",1,null,0); pair(from,to,"negative transfer");
        Card kill=creature("Eager Cadet",2,null,0); pair(from,kill,"lethal recipient preferred");
        check(choice(List.of(MINUS,PLUS),from,kill).equals(MINUS),"mixed options choose supported beneficial negative counter");
        setup(); from=creature("Grizzly Bears",0,MINUS,1); to=creature("Eager Cadet",1,null,0); creature("Blood Artist",2,null,0); check(!willing(),"uncertain death consequences hold lethal transfer");
        setup(); from=creature("Grizzly Bears",0,MINUS,1); to=creature("Young Wolf",1,null,0); check(!willing(),"undying death is uncertain");
        setup(); from=creature("Grizzly Bears",0,MINUS,1); to=creature("Grizzly Bears",1,null,0); to.setDamage(1); to.setCounters(CounterEnumType.SHIELD,1); to.incShieldCount(); refresh();
        check(!willing(),"do not promise marked-damage kill through native shield protection");
        setup(); from=creature("Grizzly Bears",0,MINUS,1); to=creature("Grizzly Bears",0,null,0); check(!willing(),"never puts negative counter on friendly creature");
        setup(); from=creature("Grizzly Bears",0,PLUS,2); to=creature("Slippery Bogle",1,null,0); check(!willing(),"no positive opponent donation through hexproof");
        setup(); from=creature("Grizzly Bears",0,MINUS,1); to=creature("Slippery Bogle",1,null,0); check(!willing(),"native hexproof recipient excluded");
        setup(); from=creature("Grizzly Bears",0,MINUS,1); to=creature("Grizzly Bears",1,null,0); add("Melira, Sylvok Outcast",p(1)); refresh(); check(!willing(),"native negative-counter prohibition excluded");
        setup(); from=creature("Grizzly Bears",0,PLUS,2); to=creature("Deadly Insect",0,null,0); check(!willing(),"native shroud recipient excluded");
        setup(); from=creature("Grizzly Bears",0,PLUS,2); to=creature("Grizzly Bears",0,null,0); add("Solemnity",p(2)); refresh(); check(!willing(),"native counter prohibition respected");
        setup(); from=creature("Grizzly Bears",0,CounterEnumType.SHIELD,2); to=creature("Grizzly Bears",0,null,0); check(!willing(),"shield counter deliberately deferred");
        from.setCounters(PLUS,2); refresh(); pair(from,to,"supported counter alongside deferred shield");
        check(choice(List.of(CounterEnumType.SHIELD,PLUS),from,to).equals(PLUS),"not first-option shield when useful positive available");
        from.setCounters(PLUS,0); refresh(); check(!willing(),"changed board clears both stale targets");
        check(ability.getTargetCard()==null && ability.getSubAbility().getTargetCard()==null,"no stale target plan after refusal");
        setup(); from=creature("Grizzly Bears",0,PLUS,2); to=creature("Grizzly Bears",0,null,0);
        ai.setTeam(0); p(1).setTeam(0); creature("Grizzly Bears",1,null,0); pair(from,to,"positive recipient remains ours rather than ally");
        setup(); from=creature("Grizzly Bears",0,MINUS,1); ai.setTeam(0); p(1).setTeam(0); to=creature("Grizzly Bears",1,null,0); check(!willing(),"negative transfer never harms ally");
    }
    static void activate(Card from,Card to,CounterType type,int added,String label) {
        int before=from.getCounters(type), after=to.getCounters(type);
        ability=selection(); check(ability!=null,label+": normal controller chooses activation");
        check(ability.getTargetCard()==from && ability.getSubAbility().getTargetCard()==to,label+": native controller preserves both targets");
        check(ComputerUtil.playStack(ability,ai,game),label+": native payment succeeds");
        check(land.isTapped() && ai.getLandsInPlay().stream().filter(Card::isTapped).count()==2,label+": native one mana plus tap paid separately");
        game.getStack().resolveStack(); game.getAction().checkStateEffects(true); refresh();
        check(from.getCounters(type)==before-1,label+": exactly one removed from donor");
        check(to.getCounters(type)==after+added,label+": native recipient counter delta "+added);
        check(game.getCardState(from,null).isInZone(ZoneType.Battlefield),label+": donor survives native state actions");
    }
    static void consequences() {
        setup(); Card from=creature("Grizzly Bears",0,PLUS,2),to=creature("Grizzly Bears",0,null,0); activate(from,to,PLUS,1,"positive");
        check(from.getNetToughness()==3 && to.getNetToughness()==3,"actual positive stats updated");
        check(!willing(),"no ping-pong after equalizing counters");
        setup(); from=creature("Grizzly Bears",0,PLUS,2); to=creature("Grizzly Bears",0,null,0); add("Hardened Scales",ai); refresh(); activate(from,to,PLUS,2,"Hardened Scales");
        check(from.getNetToughness()==3 && to.getNetToughness()==4,"replacement affects recipient only");
        setup(); from=creature("Grizzly Bears",0,MINUS,1); to=creature("Grizzly Bears",1,null,0); activate(from,to,MINUS,1,"negative");
        check(from.getNetToughness()==2 && to.getNetToughness()==1,"actual negative stats updated");
        setup(); from=creature("Grizzly Bears",0,MINUS,1); to=creature("Eager Cadet",1,null,0); activate(from,to,MINUS,1,"lethal negative");
        check(game.getCardState(to,null).isInZone(ZoneType.Graveyard),"native zero-toughness recipient death");
    }
    static void boundaries() {
        setup(); Card from=creature("Grizzly Bears",0,PLUS,2),to=creature("Grizzly Bears",0,null,0);
        check(land.getRules().getAiHints().getRemAIDecks(),"original RemoveDeck retained");
        check(!ability.hasParam("AILogic") && ability.hasParam("SorcerySpeed"),"original ability parameters retained");
        for(String profile:List.of("","toxic-deluge","corpse-augur","adaptive-enchantment","ahoy-mateys")) {
            AiOverrideProfile.setActive(profile); check(!NestingGroundsAi.uses(ability) && selection()==null,"default/unrelated profile retains blocked activation: "+profile);
        }
        AiOverrideProfile.setActive("nesting-grounds"); check(selection()!=null,"opt-in enables activation");
        check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(add("Toxic Deluge",ai)),"profile does not admit other blocked cards");
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_BEGIN,ai); check(selection()==null,"native sorcery timing excludes combat");
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2,p(1)); check(selection()==null,"native sorcery timing excludes opponent turn");
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai); land.setTapped(true); check(selection()==null,"tapped Nesting Grounds cannot activate");
        land.setTapped(false); ai.getLandsInPlay().stream().filter(c->c!=land).forEach(c->c.setTapped(true)); check(selection()==null,"cannot tap same land to pay its own additional one mana");
        setup(); from=creature("Grizzly Bears",0,PLUS,2); check(!willing(),"TargetUnique prevents transfer to same permanent");
    }
    static void lifecycle() {
        setup(); Card from=creature("Grizzly Bears",0,PLUS,2),to=creature("Grizzly Bears",0,null,0);
        ability=selection(); check(ability!=null && ComputerUtil.playStack(ability,ai,game),"response fixture pays native activation");
        from.setCounters(PLUS,0); refresh(); game.getStack().resolveStack(); refresh();
        check(to.getCounters(PLUS)==0 && land.isTapped(),"vanished counters move nothing; paid tap is not refunded");
        setup(); from=creature("Grizzly Bears",0,PLUS,2); from.setCounters(CounterEnumType.SHIELD,1); to=creature("Grizzly Bears",0,null,0); refresh();
        ability=selection(); check(ability!=null && ComputerUtil.playStack(ability,ai,game),"mixed-type response fixture pays native activation");
        from.setCounters(PLUS,0); refresh();
        check(choice(List.of(CounterEnumType.SHIELD),from,to).equals(CounterEnumType.SHIELD),"mandatory changed-board choice retains native legal fallback");
        game.getStack().resolveStack(); refresh();
        check(from.getCounters(CounterEnumType.SHIELD)==0 && to.getCounters(CounterEnumType.SHIELD)==1,"native mandatory fallback resolves rather than invalid null choice");
        setup(); from=creature("Grizzly Bears",0,PLUS,2); to=creature("Grizzly Bears",0,null,0); ability=selection();
        check(ability!=null && ComputerUtil.playStack(ability,ai,game),"recipient response fixture pays native activation");
        game.getAction().moveToGraveyard(to,ability); refresh(); game.getStack().resolveStack(); refresh();
        check(from.getCounters(PLUS)==2,"departed recipient cannot receive counter or drain donor");
        setup(); from=creature("Grizzly Bears",0,PLUS,2); to=creature("Grizzly Bears",0,null,0); ability=selection();
        check(ability!=null && ComputerUtil.playStack(ability,ai,game),"donor response fixture pays native activation");
        game.getAction().moveToGraveyard(from,ability); refresh(); game.getStack().resolveStack(); refresh();
        check(to.getCounters(PLUS)==0,"departed donor cannot move counters from old object");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null); MyRandom.setRandom(new Random(96671));
        choices(); consequences(); boundaries(); lifecycle(); AiOverrideProfile.setActive(""); System.out.println("DONE "+passed+" Nesting Grounds checks");
    }
}
