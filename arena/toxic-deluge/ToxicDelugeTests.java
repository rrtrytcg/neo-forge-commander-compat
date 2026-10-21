import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.*;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Original-script native costs and state actions; no script flags or synthetic spell resolution. */
public class ToxicDelugeTests {
    static Game game;
    static Player ai;
    static Card deluge;
    static SpellAbility spell;
    static int passed;
    static void check(boolean ok,String label) {
        if(!ok) { throw new AssertionError(label); }
        passed++; System.out.println("PASS "+label);
    }
    static Player p(int i) { return game.getPlayers().get(i); }
    static Card add(String name,Player owner,ZoneType zone) {
        Card c=Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name),owner);
        c.setGameTimestamp(game.getNextTimestamp()); owner.getZone(zone).add(c); c.setSickness(false); return c;
    }
    static void refresh() { game.getAction().checkStaticAbilities(); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState(); }
    static void setup(int life) {
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            LobbyPlayerAi lobby=new LobbyPlayerAi("Deluge-"+i,null); lobby.setAiProfile("Default");
            players.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Constructed); game=new Game(players,rules,new Match(rules,players,"Toxic Deluge fixtures"));
        game.setAge(GameStage.Play); ai=p(0); ai.setLife(life,null); game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);
        for(int i=0;i<5;i++) { add("Swamp",ai,ZoneType.Battlefield); }
        for(int i=0;i<20;i++) { add("Swamp",ai,ZoneType.Library); }
        deluge=add("Toxic Deluge",ai,ZoneType.Hand); spell=deluge.getFirstSpellAbility(); spell.setActivatingPlayer(ai);
        AiOverrideProfile.setActive("toxic-deluge"); refresh();
    }
    static Card creature(String name,int seat) { Card c=add(name,p(seat),ZoneType.Battlefield); refresh(); return c; }
    static List<Card> creatures(String name,int seat,int count) {
        List<Card> result=new ArrayList<>(); for(int i=0;i<count;i++) { result.add(creature(name,seat)); } return result;
    }
    static boolean willing() { return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai,spell).willingToPlay(); }
    static void expect(int x,String label) {
        check(willing(),label+": API accepts"); check(spell.getXManaCostPaid()==x,label+": minimum X="+x);
    }
    static SpellAbility selection() {
        List<SpellAbility> list=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();
        return list==null?null:list.stream().filter(sa->sa.getHostCard()==deluge).findFirst().orElse(null);
    }
    static void choices() {
        setup(40); check(!willing() && spell.getXManaCostPaid()==0,"empty board holds and clears X");
        creature("Grizzly Bears",1); check(!willing(),"one opposing creature is insufficient");
        creature("Grizzly Bears",2); expect(2,"two opponents jointly qualify");
        setup(7); creatures("Grizzly Bears",1,2); expect(2,"five-life reserve equality");
        setup(6); creatures("Grizzly Bears",1,2); check(!willing() && selection()==null,"cannot pay below five life");
        setup(5); creatures("Darksteel Myr",1,3); check(!willing(),"five life holds even X=1");
        setup(1); creatures("Darksteel Myr",1,3); check(!willing(),"never pays lethal life");
        setup(40); creatures("Eager Cadet",1,2); creatures("Craw Wurm",2,3); expect(1,"smallest qualifying X instead of largest wipe");
        setup(40); creature("Walking Corpse",0); creatures("Grizzly Bears",1,2); check(!willing(),"two enemies for one friendly does not qualify");
        creature("Grizzly Bears",2); expect(2,"three enemies for one friendly qualify");
        setup(40); ai.setTeam(0); p(1).setTeam(0); creature("Walking Corpse",1); creatures("Grizzly Bears",2,2);
        check(!willing(),"allied losses count as friendly losses"); creature("Grizzly Bears",3); expect(2,"all opponents and allied loss counted");
        setup(40); creature("Darksteel Sentinel",0); creatures("Ornithopter",1,3); creatures("Eager Cadet",2,3);
        // Native evaluator values the protected friendly Sentinel above these harmless bodies at X=3.
        expect(1,"life-minimal choice preserves valuable friendly creature");
        setup(40); Card champion=creature("Yargle and Multani",0); champion.setDamage(5);
        List<Card> cheap=creatures("Ornithopter",1,3); refresh();
        check(ComputerUtilCard.evaluateCreature(champion)>cheap.stream().mapToInt(ComputerUtilCard::evaluateCreature).sum(),"value-loss fixture independently exceeds opposing value");
        check(!willing(),"count advantage cannot justify losing a more valuable friendly board");
        setup(40); creatures("Grizzly Bears",1,2); creature("Platinum Emperion",0); check(!willing(),"native life-payment prohibition respected");
        setup(40); creatures("Grizzly Bears",1,2); creature("Blood Artist",2); check(!willing(),"uncertain public death-trigger consequences hold");
        setup(40); creatures("Grizzly Bears",1,2); creature("Soul Warden",2); expect(2,"unrelated ETB trigger does not veto a simple wipe");
        setup(40); creatures("Elvish Mystic",1,2); creature("Elvish Archdruid",1); check(!willing(),"dying lord with static abilities is uncertain");
        setup(40); creatures("Grizzly Bears",1,2); expect(2,"initial board qualifies"); ai.setLife(6,null);
        check(!willing() && spell.getXManaCostPaid()==0,"life change clears stale X and decision");
        setup(40); creatures("Grizzly Bears",1,2); int life=ai.getLife(); long timestamp=game.getTimestamp();
        for(int i=0;i<5;i++) { expect(2,"stable preview "+i); }
        check(ai.getLife()==life && game.getTimestamp()==timestamp && game.getStack().isEmpty(),"preview consumes no life, timestamps or stack actions");
        check(p(1).getCreaturesInPlay().stream().allMatch(c->c.getNetToughness()==2),"preview leaves live stats intact");
    }
    static void cast(int x,int mana,String label,List<Card> die,List<Card> survive) {
        int life=ai.getLife(); spell=selection(); check(spell!=null,label+": native controller selects spell");
        check(spell.getXManaCostPaid()==x,label+": controller retains chosen life X");
        check(ComputerUtil.playStack(spell,ai,game),label+": native casting pays costs");
        check(ai.getLife()==life-x,label+": exactly X life paid before resolution");
        check(ai.getLandsInPlay().stream().filter(Card::isTapped).count()==mana,label+": native mana payment is independent of X");
        check(deluge.isInZone(ZoneType.Stack),label+": original spell on stack");
        game.getStack().resolveStack(); game.getAction().checkStateEffects(true); refresh();
        check(game.getCardState(deluge,null).isInZone(ZoneType.Graveyard),label+": original spell resolves to graveyard");
        for(Card c:die) { check(game.getCardState(c,null).isInZone(ZoneType.Graveyard),label+": actual casualty "+c.getName()); }
        for(Card c:survive) { check(game.getCardState(c,null).isInZone(ZoneType.Battlefield),label+": actual survivor "+c.getName()); }
        check(ai.getLife()==life-x,label+": resolution does not charge life twice");
    }
    static void consequences() {
        setup(40); List<Card> die=creatures("Grizzly Bears",1,2); Card survivor=creature("Craw Wurm",0);
        cast(2,3,"ordinary X2",die,List.of(survivor));
        check(survivor.getNetPower()==4 && survivor.getNetToughness()==2,"survivor receives native -2/-2");
        game.getEndOfTurn().executeUntil(); refresh(); check(survivor.getNetPower()==6 && survivor.getNetToughness()==4,"native end-of-turn boost cleanup");
        setup(40); die=creatures("Darksteel Sentinel",1,2); check(die.stream().allMatch(c->c.hasKeyword(Keyword.INDESTRUCTIBLE)),"fixture threats indestructible");
        cast(3,3,"indestructible X3",die,List.of());
        setup(9); die=creatures("Craw Wurm",1,2); cast(4,3,"X4 leaves five life",die,List.of());
        setup(40); die=creatures("Grizzly Bears",1,2); for(Card c:die) { c.setCounters(CounterEnumType.SHIELD,1); c.incShieldCount(); } refresh();
        cast(2,3,"zero toughness bypasses shields and regeneration",die,List.of());
        setup(40); die=creatures("Centaur Courser",1,2); for(Card c:die) { c.setDamage(1); } refresh();
        cast(2,3,"marked damage lowers necessary X",die,List.of());
        setup(40); die=creatures("Darksteel Sentinel",1,2); for(Card c:die) { c.setDamage(1); } refresh();
        cast(3,3,"marked damage does not defeat indestructibility",die,List.of());
        setup(40); die=creatures("Grizzly Bears",1,2); add("Sphere of Resistance",p(2),ZoneType.Battlefield); refresh();
        cast(2,4,"native public tax",die,List.of());
    }
    static void boundaries() {
        setup(40); creatures("Grizzly Bears",1,2);
        check(deluge.getRules().getAiHints().getRemAIDecks(),"original deck-building prohibition retained");
        check(!spell.isCurse(),"original spell parameters unchanged");
        for(String profile:List.of("","corpse-augur","darksteel-mutation","decree-of-pain","ahoy-mateys")) {
            AiOverrideProfile.setActive(profile); check(!ToxicDelugeAi.uses(spell) && selection()==null,"default/unrelated profile remains blocked: "+profile);
        }
        AiOverrideProfile.setActive("toxic-deluge"); check(selection()!=null,"opt-in admission enables native play");
        check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(add("Corpse Augur",ai,ZoneType.Hand)),"profile admits no other blocked card");
        setup(40); creatures("Grizzly Bears",1,2); for(Card land:ai.getLandsInPlay()) { land.setTapped(true); }
        check(selection()==null,"tapped lands cannot pay native mana");
        setup(40); creatures("Grizzly Bears",1,2); for(Card land:new ArrayList<>(ai.getLandsInPlay())) { ai.getZone(ZoneType.Battlefield).remove(land); add("Wastes",ai,ZoneType.Battlefield); } refresh();
        check(selection()==null,"native casting still needs black mana");
        setup(40); creatures("Grizzly Bears",1,2); game.getPhaseHandler().devModeSet(PhaseType.COMBAT_BEGIN,ai);
        check(selection()==null,"native sorcery timing retained");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null); MyRandom.setRandom(new Random(96561));
        choices(); consequences(); boundaries(); AiOverrideProfile.setActive(""); System.out.println("DONE "+passed+" Toxic Deluge checks");
    }
}
