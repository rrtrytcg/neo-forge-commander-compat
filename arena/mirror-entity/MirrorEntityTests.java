import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.game.*;
import forge.deck.Deck;
import forge.game.ability.ApiType;
import forge.game.card.*;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import com.google.common.collect.HashMultiset;
import forge.util.MyRandom;
import java.util.*;

class MirrorEntityTests {
    static Game game;
    static Player ai;
    static Card mirror;
    static SpellAbility ability;
    static int passed;
    static void check(boolean result,String label) {
        if(!result) { throw new AssertionError(label); }
        passed++; System.out.println("PASS "+label);
    }
    static Player opp(int i) { return game.getPlayers().get(i); }
    static Card add(String name,Player owner,ZoneType zone) {
        Card c=Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name),owner);
        c.setGameTimestamp(game.getNextTimestamp()); owner.getZone(zone).add(c); c.setSickness(false); return c;
    }
    static void refresh() { game.getAction().checkStaticAbilities(); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState(); }
    static Card creature(String name,Player owner) { Card c=add(name,owner,ZoneType.Battlefield); refresh(); return c; }
    static SpellAbility entityAbility(Card c,Player p) {
        SpellAbility sa=c.getSpellAbilities().stream().filter(a->a.getApi()==ApiType.AnimateAll).findFirst().orElseThrow();
        sa.setActivatingPlayer(p); return sa;
    }
    static void setup(int plains) {
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) { LobbyPlayerAi lobby=new LobbyPlayerAi("Entity-"+i,null); lobby.setAiProfile("Default"); players.add(new RegisteredPlayer(new Deck()).setPlayer(lobby)); }
        GameRules rules=new GameRules(GameType.Constructed); game=new Game(players,rules,new Match(rules,players,"Mirror Entity native fixtures"));
        game.setAge(GameStage.Play); ai=opp(0); game.getPhaseHandler().devModeSet(PhaseType.COMBAT_BEGIN,ai);
        for(int i=0;i<plains;i++) { add("Plains",ai,ZoneType.Battlefield); }
        mirror=creature("Mirror Entity",ai); ability=entityAbility(mirror,ai);
        AiOverrideProfile.setActive("mirror-entity"); refresh();
    }
    static boolean willing() { return SpellApiToAi.Converter.get(ability).canPlayWithSubs(ai,ability).willingToPlay(); }
    static void counters(Card c,int amount) { var set=HashMultiset.<CounterType>create(); set.add(CounterEnumType.P1P1,amount); c.setCounters(set); refresh(); }
    static void offense() {
        setup(3); check(!willing(),"offense: Entity alone is insufficient");
        Card ally=creature("Eager Cadet",ai);
        check(willing() && ability.getXManaCostPaid()==3,"offense: two ready creatures improve at X=3");
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1,ai);
        check(!willing(),"offense: hold before beginning of combat");
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);
        check(!willing(),"offense: hold after combat");
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_BEGIN,opp(1));
        check(!willing(),"offense: opponent beginning of combat does not qualify");
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_BEGIN,ai);
        ally.setSickness(true); check(!willing(),"offense: summoning-sick creature does not count");
        ally.setSickness(false); ally.setTapped(true); check(!willing(),"offense: tapped creature does not count");
        ally.setTapped(false); ally.setPhasedOut(ai); check(!willing(),"offense: phased-out creature does not count");
        setup(3); creature("Wall of Wood",ai); check(!willing(),"offense: defender does not count");
        setup(0); creature("Eager Cadet",ai); check(!willing() && ability.getXManaCostPaid()==0,"X: never activate for zero");
        setup(2); creature("Eager Cadet",ai); check(!willing(),"X: two mana below minimum");
        setup(5); creature("Eager Cadet",ai); check(willing() && ability.getXManaCostPaid()==5,"X: largest natively affordable value");
        add("Suppression Field",opp(1),ZoneType.Battlefield); refresh();
        check(willing() && ability.getXManaCostPaid()==3,"X: native activation tax reduces X");
        for(Card land:ai.getLandsInPlay()) { land.setTapped(true); }
        check(!willing(),"X: tapped mana cannot be reused");
    }
    static void safety() {
        setup(3); creature("Eager Cadet",ai); Card large=creature("Serra Angel",ai); large.setTapped(true);
        check(!willing(),"safety: shrinking any own creature holds, even a nonparticipant");
        add("Plains",ai,ZoneType.Battlefield); refresh();
        check(willing() && ability.getXManaCostPaid()==4,"safety: equal large creature stats permit smaller creatures to improve");
        setup(3); Card ally=creature("Eager Cadet",ai); counters(ally,5);
        check(willing(),"safety: base-setting preserves counters rather than comparing raw X to net stats");
        Card copy=MirrorEntityAi.preview(ai,ally,ability);
        check(copy.getNetPower()==8 && copy.getNetToughness()==8 && ally.getNetPower()==6,
                "preview: native counters apply after new base without mutating live stats");
        check(copy.getType().hasSubtype("Goblin") && copy.getType().hasSubtype("Elf"),"preview: all creature types applied");
        setup(3); ally=creature("Eager Cadet",ai); add("Glorious Anthem",ai,ZoneType.Battlefield); refresh();
        check(willing(),"preview: ordinary constant anthem supported");
        copy=MirrorEntityAi.preview(ai,ally,ability);
        check(copy.getNetPower()==4 && copy.getNetToughness()==4 && ally.getNetPower()==2,"preview: native anthem bonus retained once");
        setup(3); creature("Eager Cadet",ai); add("Coat of Arms",opp(1),ZoneType.Battlefield); refresh();
        check(!willing(),"preview: uncertain variable tribal effects hold");
        setup(3); ally=creature("Eager Cadet",ai); ally.setDamage(3);
        check(!willing(),"safety: lethal marked damage holds");
        setup(3); creature("Eager Cadet",ai); SpellAbility pending=ability.copy(ai); pending.setXManaCostPaid(3); game.getStack().add(pending);
        check(!willing(),"pending: do not stack another activation for our army");
        setup(3); creature("Eager Cadet",ai); Card enemy=creature("Mirror Entity",opp(1));
        pending=entityAbility(enemy,opp(1)); pending.setXManaCostPaid(3); game.getStack().add(pending);
        check(willing(),"pending: opponent's army effect does not veto ours");
    }
    static void defend() {
        setup(4); Card first=creature("Wall of Wood",ai); Card second=creature("Wall of Wood",ai);
        Card attacker1=creature("Grizzly Bears",opp(1)); Card attacker2=creature("Grizzly Bears",opp(1));
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS,opp(1));
        Combat combat=new Combat(opp(1)); game.getPhaseHandler().setCombat(combat);
        combat.addAttacker(attacker1,ai); combat.addAttacker(attacker2,ai); combat.addBlocker(attacker1,first); refresh();
        check(!willing(),"defense: one actual blocker is insufficient");
        combat.addBlocker(attacker2,second);
        check(willing() && ability.getXManaCostPaid()==4,"defense: two actual blockers improve");
        check(ComputerUtil.playStack(ability,ai,game),"native defense: activation pays and enters stack");
        game.getStack().resolveStack(); refresh();
        check(first.getNetPower()==4 && second.getNetPower()==4 && combat.isBlocking(first) && combat.isBlocking(second),
                "native defense: blockers improve without changing assignments");
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS,opp(1),false);
        check(!willing(),"defense: do not activate before blockers declared");
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DAMAGE,opp(1),false);
        check(!willing(),"defense: do not activate after blocker window");
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS,ai,false);
        check(!willing(),"defense: own blockers phase does not qualify");
    }
    static void nativeResolution() {
        setup(3); Card ally=creature("Eager Cadet",ai);
        List<SpellAbility> selected=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();
        check(selected!=null && !selected.isEmpty() && selected.get(0).getApi()==ApiType.AnimateAll,"native: normal selection chooses activation");
        check(willing() && ComputerUtil.playStack(ability,ai,game),"native: payment puts activation on stack");
        check(ai.getLandsInPlay().stream().filter(Card::isTapped).count()==3,"native: three Plains pay X=3");
        game.getStack().resolveStack(); refresh();
        check(mirror.getNetPower()==3 && ally.getNetPower()==3 && ally.getNetToughness()==3,"native: original ability makes own creatures 3/3");
        check(ally.getType().hasSubtype("Goblin") && ally.getType().hasSubtype("Elf"),"native: original ability grants every creature type");
        for(Card land:ai.getLandsInPlay()) { land.setTapped(false); }
        check(!willing(),"repeat: same X with no further improvement holds");
        game.getEndOfTurn().executeUntil(); refresh();
        check(mirror.getNetPower()==1 && ally.getNetPower()==1 && !ally.getType().hasSubtype("Goblin"),"native: end-of-turn restores base stats and types");
        setup(3); creature("Eager Cadet",ai); add("Cursed Totem",opp(1),ZoneType.Battlefield); refresh();
        check(((PlayerControllerAi)ai.getController()).getAi().canPlaySa(ability)!=AiPlayDecision.WillPlay,
                "native: activation prohibition enforced by normal controller");
    }
    static void isolation() {
        setup(3); creature("Eager Cadet",ai);
        for(String profile:List.of("","abzan-armor","adaptive-enchantment","ahoy-mateys","decree-of-pain","curse-of-the-swine")) {
            AiOverrideProfile.setActive(profile);
            check(!MirrorEntityAi.uses(ability) && !AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(mirror) && !willing(),
                    "isolation: no Entity policy in '"+profile+"'");
        }
        AiOverrideProfile.setActive(""); ability.getMapParams().put("AILogic","Always");
        check(willing(),"default: existing AnimateAll Always hint retains behavior");
        AiOverrideProfile.setActive("mirror-entity");
        check(AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(mirror) && !AiOverrideProfile.get().supportsLandManaAuras(),
                "isolation: Entity admitted without Adaptive mana family");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null); MyRandom.setRandom(new Random(7381));
        offense(); safety(); defend(); nativeResolution(); isolation(); AiOverrideProfile.setActive(""); System.out.println("DONE "+passed+" Entity checks");
    }
}
