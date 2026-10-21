import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.*;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Original-script spell casting, hybrid payment, native base-P/T layers and cleanup. */
public class BiomassMutationTests {
    static Game game; static Player ai; static Card mutation; static SpellAbility spell; static int passed;
    static void check(boolean ok,String label) { if(!ok) { throw new AssertionError(label); } passed++; System.out.println("PASS "+label); }
    static Player p(int i) { return game.getPlayers().get(i); }
    static Card add(String name,Player owner,ZoneType zone) {
        Card c=Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name),owner);
        c.setGameTimestamp(game.getNextTimestamp()); owner.getZone(zone).add(c); c.setSickness(false); return c;
    }
    static void refresh() { game.getAction().checkStaticAbilities(); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState(); }
    static Card creature(String name,int seat) { Card c=add(name,p(seat),ZoneType.Battlefield); refresh(); return c; }
    static void lands(String name,int count) { for(int i=0;i<count;i++) { add(name,ai,ZoneType.Battlefield); } refresh(); }
    static void setup(String mana,int count) {
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) { LobbyPlayerAi lobby=new LobbyPlayerAi("Biomass-"+i,null); lobby.setAiProfile("Default"); players.add(new RegisteredPlayer(new Deck()).setPlayer(lobby)); }
        GameRules rules=new GameRules(GameType.Constructed); game=new Game(players,rules,new Match(rules,players,"Biomass Mutation native fixtures"));
        game.setAge(GameStage.Play); ai=p(0); game.getPhaseHandler().devModeSet(PhaseType.COMBAT_BEGIN,ai);
        lands(mana,count); mutation=add("Biomass Mutation",ai,ZoneType.Hand); spell=mutation.getFirstSpellAbility(); spell.setActivatingPlayer(ai);
        AiOverrideProfile.setActive("biomass-mutation"); refresh();
    }
    static void army() { creature("Grizzly Bears",0); creature("Grizzly Bears",0); }
    static boolean willing() { return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai,spell).willingToPlay(); }
    static SpellAbility selection() {
        List<SpellAbility> list=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();
        return list==null?null:list.stream().filter(sa->sa.getHostCard()==mutation).findFirst().orElse(null);
    }
    static void expect(int x,String label) { check(willing(),label+": API accepts"); check(spell.getXManaCostPaid()==x,label+": chosen X="+x); }
    static void choices() {
        setup("Forest",5); check(!willing() && spell.getXManaCostPaid()==0,"empty board clears X and holds");
        Card first=creature("Grizzly Bears",0); check(!willing(),"one eligible attacker is insufficient");
        Card second=creature("Grizzly Bears",0); expect(3,"two eligible attackers");
        second.setSickness(true); check(!willing(),"summoning sickness excludes participant");
        second.setSickness(false); second.setTapped(true); check(!willing(),"tapped creature excludes participant");
        second.setTapped(false); second.setPhasedOut(ai); check(!willing(),"phased-out creature excludes participant");
        setup("Forest",5); creature("Wall of Wood",0); creature("Grizzly Bears",0); check(!willing(),"defender is not an eligible attacker");
        setup("Forest",5); army(); for(PhaseType phase:List.of(PhaseType.MAIN1,PhaseType.MAIN2,PhaseType.COMBAT_DECLARE_ATTACKERS,PhaseType.COMBAT_DAMAGE)) {
            game.getPhaseHandler().devModeSet(phase,ai); check(!willing() && spell.getXManaCostPaid()==0,"hold outside offensive window: "+phase);
        }
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_BEGIN,p(1)); check(!willing(),"opponent beginning of combat does not qualify");
        for(String mana:List.of("Forest","Island")) { setup(mana,5); army(); expect(3,"native hybrid payment using "+mana); }
        setup("Forest",1); lands("Island",1); lands("Wastes",3); army(); expect(3,"mixed hybrids and generic mana");
        setup("Forest",0); lands("Wastes",5); army(); check(!willing() && selection()==null,"colorless mana cannot pay either hybrid symbol");
        setup("Forest",1); lands("Wastes",4); army(); check(!willing() && selection()==null,"one colored mana cannot pay both hybrid symbols");
        for(int count=0;count<5;count++) { setup("Forest",count); army(); check(!willing() && spell.getXManaCostPaid()==0,"insufficient mana/minimum X: "+count); }
        setup("Forest",8); army(); expect(6,"largest native X after both hybrid symbols");
        add("Sphere of Resistance",p(2),ZoneType.Battlefield); refresh(); expect(5,"public casting tax reduces X");
        for(Card c:ai.getLandsInPlay()) { c.setTapped(true); } check(!willing() && selection()==null,"tapped mana cannot be reused");
    }
    static void safety() {
        setup("Forest",5); army(); Card large=creature("Serra Angel",0); large.setTapped(true); check(!willing(),"nonparticipant shrinking vetoes cast");
        lands("Forest",1); expect(4,"equal large creature stats permit improvement elsewhere");
        setup("Forest",5); army(); Card c=ai.getCreaturesInPlay().get(0); c.setCounters(CounterEnumType.P1P1,5); refresh(); expect(3,"counters apply after base setting");
        Card preview=CombatBaseStatsAi.preview(ai,c,spell);
        check(preview.getNetPower()==8 && preview.getNetToughness()==8 && c.getNetPower()==7,"preview retains counters and leaves live stats intact");
        check(preview.getType().hasSubtype("Bear") && !preview.getType().hasSubtype("Elf"),"Biomass preview preserves creature types");
        setup("Forest",5); army(); add("Glorious Anthem",ai,ZoneType.Battlefield); refresh(); expect(3,"constant additive anthem supported");
        preview=CombatBaseStatsAi.preview(ai,ai.getCreaturesInPlay().get(0),spell); check(preview.getNetPower()==4 && preview.getNetToughness()==4,"native anthem retained once");
        setup("Forest",5); army(); add("Coat of Arms",p(1),ZoneType.Battlefield); refresh(); check(!willing(),"uncertain variable continuous effect holds");
        setup("Forest",5); army(); add("Zilortha, Strength Incarnate",p(1),ZoneType.Battlefield); refresh(); check(!willing(),"uncertain lethal-by-power continuous effect holds");
        setup("Forest",5); army(); ai.getCreaturesInPlay().get(0).setDamage(3); check(!willing(),"lethal marked damage holds");
        setup("Forest",5); army(); SpellAbility pending=spell.copy(ai); pending.setXManaCostPaid(3); game.getStack().add(pending); check(!willing(),"pending own Biomass prevents duplicate cast");
        setup("Forest",5); army(); Card other=add("Biomass Mutation",p(1),ZoneType.Hand); pending=other.getFirstSpellAbility(); pending.setActivatingPlayer(p(1)); pending.setXManaCostPaid(3); game.getStack().add(pending); expect(3,"opponent's pending spell does not veto ours");
        setup("Forest",5); army(); ai.setTeam(0); p(1).setTeam(0); creature("Craw Wurm",1); expect(3,"allied creatures outside original YouCtrl effect");
        int power=ai.getCreaturesInPlay().get(0).getNetPower(); for(int i=0;i<4;i++) { expect(3,"stable evaluation "+i); }
        check(ai.getCreaturesInPlay().get(0).getNetPower()==power && !ai.getLandsInPlay().get(0).isTapped() && mutation.isInZone(ZoneType.Hand),"evaluation preserves live stats, mana and spell zone");
    }
    static void cast(int x,int paid,String label) {
        spell=selection(); check(spell!=null,label+": normal controller selects spell");
        check(spell.getXManaCostPaid()==x,label+": controller retains X");
        check(ComputerUtil.playStack(spell,ai,game),label+": native casting and payment succeed");
        check(mutation.isInZone(ZoneType.Stack),label+": original spell enters stack");
        check(ai.getLandsInPlay().stream().filter(Card::isTapped).count()==paid,label+": native X plus hybrid/tax payment");
        game.getStack().resolveStack(); refresh();
        check(game.getCardState(mutation,null).isInZone(ZoneType.Graveyard),label+": original spell resolves to graveyard");
    }
    static void nativeResolution() {
        for(String mana:List.of("Forest","Island")) {
            setup(mana,5); army(); Card enemy=creature("Eager Cadet",1); Card ally=ai.getCreaturesInPlay().get(0); ally.setCounters(CounterEnumType.P1P1,2); refresh();
            cast(3,5,"offensive "+mana);
            check(ally.getNetPower()==5 && ally.getNetToughness()==5 && ai.getCreaturesInPlay().get(1).getNetPower()==3,"native counters and base stats applied");
            check(ally.getType().hasSubtype("Bear") && !ally.getType().hasSubtype("Elf") && enemy.getNetPower()==1,"native types and opposing stats preserved");
            spell=add("Biomass Mutation",ai,ZoneType.Hand).getFirstSpellAbility(); spell.setActivatingPlayer(ai);
            ai.getLandsInPlay().forEach(land->land.setTapped(false)); check(!willing(),"same-X resolved effect gives no further benefit");
            game.getEndOfTurn().executeUntil(); refresh(); check(ally.getNetPower()==4 && ally.getNetToughness()==4,"native end-of-turn restores base but retains counters");
        }
        setup("Forest",1); lands("Island",1); lands("Wastes",3); army(); cast(3,5,"mixed hybrid payment");
        setup("Forest",6); army(); add("Sphere of Resistance",p(2),ZoneType.Battlefield); refresh(); cast(3,6,"casting tax");
        setup("Forest",5); army(); add("Cursed Totem",p(2),ZoneType.Battlefield); refresh(); cast(3,5,"spell is not a creature activation");
        setup("Island",6); Card first=creature("Wall of Wood",0),second=creature("Wall of Wood",0);
        Card attack1=creature("Grizzly Bears",1),attack2=creature("Grizzly Bears",1);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS,p(1)); Combat combat=new Combat(p(1)); game.getPhaseHandler().setCombat(combat);
        combat.addAttacker(attack1,ai); combat.addAttacker(attack2,ai); combat.addBlocker(attack1,first); refresh();
        check(!willing(),"one actual blocker is insufficient"); combat.addBlocker(attack2,second); expect(4,"two actual blockers improve");
        cast(4,6,"defensive casting"); check(first.getNetPower()==4 && first.getNetToughness()==4 && combat.isBlocking(first) && combat.isBlocking(second),"native blocker stats improve and assignments remain");
    }
    static void isolation() {
        setup("Forest",5); army(); check(mutation.getRules().getAiHints().getRemAIDecks(),"original deck-building prohibition retained");
        check(!spell.hasParam("AILogic") && !spell.hasParam("AddAllCreatureTypes"),"original script params remain unchanged");
        for(String profile:List.of("","mirror-entity","nesting-grounds","adaptive-enchantment","toxic-deluge","ahoy-mateys")) {
            AiOverrideProfile.setActive(profile); check(!BiomassMutationAi.uses(spell) && selection()==null,"default/unrelated profile retains blocked spell: "+profile);
        }
        AiOverrideProfile.setActive("biomass-mutation"); check(selection()!=null,"explicit profile admits spell");
        Card mirror=creature("Mirror Entity",0); SpellAbility activation=mirror.getSpellAbilities().stream().filter(sa->sa.getApi()==forge.game.ability.ApiType.AnimateAll).findFirst().orElseThrow();
        check(!MirrorEntityAi.uses(activation) && !AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(mirror),"Biomass profile does not admit Mirror Entity");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null); MyRandom.setRandom(new Random(96781));
        choices(); safety(); nativeResolution(); isolation(); AiOverrideProfile.setActive(""); System.out.println("DONE "+passed+" Biomass checks");
    }
}
