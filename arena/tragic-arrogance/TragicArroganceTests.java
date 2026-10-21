import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.ai.ability.ChooseCardAi;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.*;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import com.google.common.collect.HashMultiset;
import java.util.*;

/** Native four-player decision and original-script resolution, with a separate Cartesian oracle. */
public class TragicArroganceTests {
    static Game game;
    static Player ai;
    static Card source;
    static SpellAbility spell;
    static int passed;
    static final String[] TYPES={"Artifact","Creature","Enchantment","Planeswalker"};
    static void check(boolean condition,String label) {
        if(!condition) { throw new AssertionError(label); }
        passed++; System.out.println("PASS "+label);
    }
    static Player player(int i) { return game.getPlayers().get(i); }
    static Card add(String name,Player owner,ZoneType zone) {
        Card card=Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name),owner);
        card.setGameTimestamp(game.getNextTimestamp()); owner.getZone(zone).add(card); card.setSickness(false);
        if(card.isPlaneswalker() && zone==ZoneType.Battlefield) {
            var counters=HashMultiset.<CounterType>create(); counters.add(CounterEnumType.LOYALTY,4); card.setCounters(counters);
        }
        return card;
    }
    static Card permanent(String name,Player owner) { Card card=add(name,owner,ZoneType.Battlefield); refresh(); return card; }
    static void refresh() { game.getAction().checkStaticAbilities(); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState(); }
    static void setup(int mana) {
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            LobbyPlayerAi lobby=new LobbyPlayerAi("Arrogance-"+i,null); lobby.setAiProfile("Default");
            players.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Constructed);
        game=new Game(players,rules,new Match(rules,players,"Tragic Arrogance native fixtures"));
        game.setAge(GameStage.Play); ai=player(0); game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);
        for(int i=0;i<mana;i++) { add("Plains",ai,ZoneType.Battlefield); }
        for(int i=0;i<12;i++) { add("Plains",ai,ZoneType.Library); }
        source=add("Tragic Arrogance",ai,ZoneType.Hand); spell=source.getFirstSpellAbility(); spell.setActivatingPlayer(ai);
        AiOverrideProfile.setActive("tragic-arrogance"); refresh();
    }
    static SpellAbility chooseAbility() {
        SpellAbility sa=spell.getAdditionalAbility("RepeatSubAbility"); sa.setActivatingPlayer(ai); return sa;
    }
    static boolean willing() { return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai,spell).willingToPlay(); }
    static boolean selected() {
        List<SpellAbility> result=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();
        return result!=null && result.stream().anyMatch(sa->sa.getHostCard()==source);
    }
    static void enemyCreatures(int count,Player owner) { for(int i=0;i<count;i++) { permanent("Grizzly Bears",owner); } }
    static void cast() {
        check(selected(),"native controller selects original sorcery");
        check(willing() && ComputerUtil.playStack(spell,ai,game),"native payment puts original spell on stack");
        check(ai.getLandsInPlay().stream().filter(Card::isTapped).count()==5,"native five Plains pay 3WW");
    }
    static void resolve() {
        game.getStack().resolveStack(); game.getAction().checkStateEffects(true); refresh();
        check(game.getStack().isEmpty(),"native root resolution finishes without extra decision actions");
        check(!spell.getHostCard().getRemembered().iterator().hasNext(),"native remembered-survivor cleanup finishes");
    }
    static boolean battlefield(Card card) { return game.getCardsIn(ZoneType.Battlefield).stream().anyMatch(c->c.getId()==card.getId()); }
    static void gate() {
        setup(5); check(!willing(),"gate: empty board holds");
        enemyCreatures(2,player(1)); check(!willing(),"gate: only one opposing loss holds");
        enemyCreatures(1,player(1)); check(willing(),"gate: two opposing losses with no friendly losses qualifies");
        permanent("Eager Cadet",ai); check(willing(),"gate: sole own creature is preserved");
        permanent("Eager Cadet",ai); check(!willing(),"gate: only one net opposing permanent lost holds");
        enemyCreatures(1,player(2)); check(!willing(),"gate: sole permanent at another opponent is kept");
        enemyCreatures(2,player(2)); check(willing(),"gate: counts losses across all opponents");
        setup(4); enemyCreatures(4,player(1)); check(!willing() && !selected(),"payment: four mana cannot cast");
        setup(5); enemyCreatures(4,player(1)); permanent("Thalia, Guardian of Thraben",player(2));
        check(!willing() && !selected(),"payment: native spell tax holds");
        add("Plains",ai,ZoneType.Battlefield); refresh(); check(willing(),"payment: sixth mana pays native tax");
        setup(5); enemyCreatures(4,player(1)); game.getPhaseHandler().devModeSet(PhaseType.UPKEEP,player(1));
        check(!willing() && !selected(),"legality: sorcery timing is preserved");
        setup(5); permanent("Darksteel Colossus",ai); permanent("Blightsteel Colossus",ai); permanent("Ulamog, the Infinite Gyre",ai);
        enemyCreatures(5,player(1));
        check(!willing(),"value gate: net two cards does not justify losing more native permanent value");
    }
    static int oracle(Player owner,boolean maximize) {
        List<List<Card>> groups=new ArrayList<>();
        for(String type:TYPES) {
            List<Card> group=new ArrayList<>();
            for(Card card:owner.getCardsIn(ZoneType.Battlefield)) { if(card.getType().hasStringType(type)) { group.add(card); } }
            if(group.isEmpty()) { group.add(null); } groups.add(group);
        }
        int best=maximize?Integer.MIN_VALUE:Integer.MAX_VALUE;
        SpellAbility cause=spell.getSubAbility().copy(ai);
        for(Card a:groups.get(0)) for(Card c:groups.get(1)) for(Card e:groups.get(2)) for(Card p:groups.get(3)) {
            Set<Card> distinct=new HashSet<>(Arrays.asList(a,c,e,p)); int score=0;
            for(Card card:distinct) {
                if(card!=null && !card.isLand() && card.canBeSacrificedBy(cause,true)) { score+=card.getCMC()+1; }
            }
            best=maximize?Math.max(best,score):Math.min(best,score);
        }
        return best;
    }
    static void plansAndOracle() {
        String[][] boards={
            {"Ornithopter","Gilded Lotus","Serra Angel"},
            {"Darksteel Citadel","Darksteel Relic","Eager Cadet"},
            {"Ornithopter","Courser of Kruphix","Ghostly Prison","Ajani, Caller of the Pride","Jace Beleren"},
            {"Dryad Arbor","Ornithopter","Gilded Lotus","Grizzly Bears"},
            {"Darksteel Relic","Steel Wall","Courser of Kruphix","Serra Angel","Ghostly Prison","Oblivion Ring"},
            {"Eager Cadet"}, {}
        };
        for(int b=0;b<boards.length;b++) {
            setup(5);
            for(int index:new int[]{0,1}) {
                for(String name:boards[b]) { permanent(name,player(index)); }
                var plan=TragicArroganceAi.plan(ai,player(index),spell);
                check(plan.savedValue==oracle(player(index),index==0),"independent Cartesian oracle: board "+b+" player "+index);
                for(int type=0;type<4;type++) {
                    final String category=TYPES[type];
                    boolean exists=player(index).getCardsIn(ZoneType.Battlefield).stream().anyMatch(c->c.getType().hasStringType(category));
                    check((plan.choices[type]!=null)==exists && (!exists || plan.choices[type].getType().hasStringType(category)),"mandatory category coverage: board "+b+" player "+index+" "+category);
                }
            }
        }
    }
    static void nativeChoices() {
        setup(5); Card lotus=permanent("Gilded Lotus",ai); Card angel=permanent("Serra Angel",ai); Card ownOrni=permanent("Ornithopter",ai);
        Card bad=permanent("Ornithopter",player(1)); Card enemyLotus=permanent("Gilded Lotus",player(1)); Card enemyAngel=permanent("Serra Angel",player(1));
        Card enchantment=permanent("Ghostly Prison",player(2)); Card walker=permanent("Ajani, Caller of the Pride",player(2));
        enemyCreatures(3,player(3)); cast(); resolve();
        check(battlefield(lotus) && battlefield(angel) && !battlefield(ownOrni),"own native choices keep separate valuable artifact and creature");
        check(battlefield(bad) && !battlefield(enemyLotus) && !battlefield(enemyAngel),"opponent native choices reuse one artifact creature for both slots");
        check(battlefield(enchantment) && battlefield(walker),"native missing categories do not invent choices; singleton enchantment and walker stay");
        check(player(3).getCreaturesInPlay().size()==1,"native each opponent loses all unchosen creatures");
        check(ai.getLandsInPlay().size()==5,"native all ordinary lands survive");
        setup(5); Card land=permanent("Darksteel Citadel",player(1)); Card relic=permanent("Darksteel Relic",player(1));
        Card arbor=permanent("Dryad Arbor",player(1)); Card enemy=permanent("Serra Angel",player(1));
        enemyCreatures(3,player(2)); cast(); resolve();
        check(battlefield(land) && battlefield(arbor) && !battlefield(relic) && !battlefield(enemy),"native artifact/creature land choices preserve lands while clearing nonlands");
        setup(5); Card indestructible=permanent("Darksteel Colossus",player(1)); permanent("Ornithopter",player(1)); permanent("Eager Cadet",player(1)); enemyCreatures(2,player(2));
        cast(); resolve(); check(!battlefield(indestructible),"native indestructible does not protect against sacrifice");
        setup(5); permanent("Tajuru Preserver",player(1)); enemyCreatures(4,player(1));
        check(!willing(),"native sacrifice protection contributes no predicted removals");
        var protectedPlan=TragicArroganceAi.plan(ai,player(1),spell);
        check(protectedPlan.savedValue==0,"immune survivors are already saved, not marginal value");
        enemyCreatures(3,player(2)); cast(); resolve(); check(player(1).getCreaturesInPlay().size()==5,"native sacrifice protection remains effective");
        setup(5); enemyCreatures(3,player(1)); cast(); Card late=permanent("Ornithopter",player(1)); resolve();
        check(battlefield(late) && player(1).getCreaturesInPlay().size()==1,"resolution recomputes new multi-type survivor instead of freezing cast-time choices");
    }
    static void lifecycleAndIsolation() {
        setup(5); Card orni=permanent("Ornithopter",player(1)); Card lotus=permanent("Gilded Lotus",player(1)); Card angel=permanent("Serra Angel",player(1));
        SpellAbility choose=chooseAbility();
        CardCollection artifacts=new CardCollection(Arrays.asList(orni,lotus)); CardCollection creatures=new CardCollection(Arrays.asList(orni,angel));
        check(TragicArroganceAi.choose(ai,choose,artifacts)==orni,"callback: first overlapping category uses planned choice");
        TragicArroganceAi.consider(ai,spell);
        check(TragicArroganceAi.choose(ai,choose,artifacts)==orni && TragicArroganceAi.choose(ai,choose,creatures)==orni,"callback: interrupted plan clears on reevaluation and then completes");
        Card defaultChoice=((ChooseCardAi)SpellApiToAi.Converter.get(choose)).chooseSingleCard(ai,choose,artifacts,false,null,null);
        check(defaultChoice==orni,"native choice entry point uses opt-in plan");
        AiOverrideProfile.setActive("");
        check(((ChooseCardAi)SpellApiToAi.Converter.get(choose)).chooseSingleCard(ai,choose,artifacts,false,null,null)==lotus,"default native choice still keeps opponent's best artifact");
        for(String profile:List.of("","dispatch","oreskos-explorer","mirror-entity","adaptive-enchantment","ahoy-mateys","decree-of-pain","curse-of-the-swine")) {
            setup(5); enemyCreatures(4,player(1)); AiOverrideProfile.setActive(profile);
            check(!TragicArroganceAi.uses(spell) && !AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(source) && !selected(),"profile isolation: '"+profile+"'");
        }
        setup(5); check(ComputerUtilCard.isCardRemAIDeck(source),"original deck-building warning preserved");
        check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(add("Dispatch",ai,ZoneType.Hand)),"profile does not admit unrelated cards");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null); MyRandom.setRandom(new Random(7411));
        gate(); plansAndOracle(); nativeChoices(); lifecycleAndIsolation(); AiOverrideProfile.setActive("");
        System.out.println("DONE "+passed+" Tragic Arrogance checks");
    }
}
