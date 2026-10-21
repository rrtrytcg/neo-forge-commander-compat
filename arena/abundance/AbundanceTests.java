import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.game.card.*;
import forge.game.phase.PhaseType;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import java.util.*;

public class AbundanceTests extends DarksteelMutationTests {
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    static void fresh(){setup();ai.getZone(ZoneType.Hand).remove(aura);aura=add("Abundance",ai,ZoneType.Hand);spell=aura.getFirstSpellAbility();spell.setActivatingPlayer(ai);ai.getLandsInPlay().forEach(c->ai.getZone(ZoneType.Battlefield).remove(c));for(int i=0;i<4;i++)add("Forest",ai,ZoneType.Battlefield);AiOverrideProfile.setActive("abundance");refresh();}
    static boolean choose(){return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai,spell).willingToPlay();}
    static void ready(){fresh();ai.getZone(ZoneType.Hand).remove(aura);aura=permanent("Abundance",0);spell=aura.getFirstSpellAbility();spell.setActivatingPlayer(ai);}
    static void library(String... names){for(Card c:new ArrayList<>(ai.getCardsIn(ZoneType.Library)))ai.getZone(ZoneType.Library).remove(c);for(String n:names)add(n,ai,ZoneType.Library);refresh();}
    static List<String> lib(){return ai.getCardsIn(ZoneType.Library).stream().map(Card::getName).toList();}
    static List<String> hand(){return ai.getCardsIn(ZoneType.Hand).stream().map(Card::getName).sorted().toList();}
    static SpellAbility context(){var r=aura.getReplacementEffects().get(0);var sa=r.ensureAbility();sa.setActivatingPlayer(ai);sa.setReplacementEffect(r);sa.setReplacingObject(forge.game.ability.AbilityKey.OriginalParams,Map.of(forge.game.ability.AbilityKey.Affected,ai));return sa;}
    static boolean accept(){var sa=context();return ai.getController().confirmReplacementEffect(aura.getReplacementEffects().get(0),sa,ai,"");}
    static void nativeCast(String label){
        check(choose(),label+": permanent decision accepts");SpellAbility selected=selection();check(selected!=null,label+": native controller selects original permanent");spell=selected;
        check(AbundanceAi.preflight(ai,spell),label+": fresh source plan");check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null),label+": native payment enters stack");
        check(spell.getPayingMana().size()>=4&&spell.getPayingMana().stream().filter(m->m.toString().equals("G")).count()>=2,label+": native 2GG payment");
        game.getStack().resolveStack();refresh();aura=game.getCardState(aura,null);check(aura.isInPlay()&&game.getStack().isEmpty(),label+": native enchantment resolves");
    }
    static void effects(){
        ready();library("Centaur Courser","Forest","Island","Walking Corpse");check(accept(),"native optional confirmation accepted");var drawn=ai.drawCard();
        check(hand().equals(List.of("Forest")),"below six lands with no hand land chooses Land");check(lib().equals(List.of("Island","Walking Corpse","Centaur Courser")),"native land search preserves unrevealed prefix and bottoms skipped card");check(drawn.isEmpty()&&ai.getNumDrawnThisTurn()==0,"native replacement put into hand never counts as actual draw");
        ready();add("Forest",ai,ZoneType.Hand);library("Island","Centaur Courser","Walking Corpse");ai.drawCard();check(hand().equals(List.of("Centaur Courser","Forest")),"existing land in hand chooses Nonland");check(lib().equals(List.of("Walking Corpse","Island")),"native nonland search bottoms skipped land");
        ready();add("Forest",ai,ZoneType.Battlefield);add("Forest",ai,ZoneType.Battlefield);library("Island","Centaur Courser","Forest");ai.drawCard();check(hand().equals(List.of("Centaur Courser")),"six battlefield lands choose Nonland without hand land");
        ready();add("Forest",ai,ZoneType.Battlefield);library("Centaur Courser","Forest","Island");ai.drawCard();check(hand().equals(List.of("Forest")),"five lands remain below exact six-land boundary");
        ready();library("Forest","Centaur Courser","Island","Centaur Courser","Swamp");drawn=ai.drawCards(3);check(hand().equals(List.of("Centaur Courser","Centaur Courser","Forest")),"multi-card draw refreshes hand between Land and Nonland choices");check(lib().equals(List.of("Swamp","Island")),"multi-card native reveal and bottom movement");check(drawn.isEmpty()&&ai.getNumDrawnThisTurn()==0,"batch replacements are not three actual draws");
        ready();library();check(accept(),"empty library is not an invented draw-reserve veto");ai.drawCard();game.getAction().checkStateEffects(true);check(hand().isEmpty()&&lib().isEmpty()&&!ai.hasLost()&&ai.getNumDrawnThisTurn()==0,"native empty-library replacement does not attempt decking draw");
        ready();library("Forest");add("Forest",ai,ZoneType.Hand);ai.drawCards(3);game.getAction().checkStateEffects(true);check(hand().equals(List.of("Forest"))&&lib().equals(List.of("Forest"))&&!ai.hasLost(),"absent Nonland kind repeatedly reveals and bottoms without drawing or losing");
        ready();library("Centaur Courser");ai.drawCard();check(hand().isEmpty()&&lib().equals(List.of("Centaur Courser"))&&ai.getNumDrawnThisTurn()==0,"absent Land kind never peeks and switches to the known nonland");
        ready();library("Forest");ai.drawCard();check(hand().equals(List.of("Forest"))&&lib().isEmpty(),"single matching card moves to hand natively");ai.drawCard();check(hand().equals(List.of("Forest"))&&!ai.hasLost(),"next empty-library choice remains a replacement");
        ready();library("Forest","Centaur Courser");int n=p(1).getCardsIn(ZoneType.Hand).size();add("Island",p(1),ZoneType.Library);p(1).drawCard();check(p(1).getCardsIn(ZoneType.Hand).size()==n+1&&p(1).getNumDrawnThisTurn()==1&&hand().isEmpty(),"opponent draw is not replaced by our Abundance");
        ready();permanent("Maralen of the Mornsong",1);library("Forest");ai.drawCard();check(hand().isEmpty()&&lib().equals(List.of("Forest"))&&ai.getNumDrawnThisTurn()==0,"native cannot-draw restriction is not bypassed");
        ready();library("Forest","Centaur Courser");aura.setPhasedOut(ai);refresh();ai.drawCard();check(hand().equals(List.of("Forest"))&&ai.getNumDrawnThisTurn()==1,"phased-out source does not replace draw");
        ready();library("Forest","Centaur Courser");game.getAction().moveToGraveyard(aura,null);refresh();ai.drawCard();check(ai.getNumDrawnThisTurn()==1,"departed source leaves ordinary native draws intact");
    }
    static void choices(){
        ready();var sa=context();var offered=new ArrayList<SpellAbility>(sa.getAdditionalAbilityList("Choices"));
        check(AbundanceAi.choose(ai,sa,offered)==offered.get(0),"mode choice is native offered Land branch");
        Card land=add("Island",ai,ZoneType.Hand);check(AbundanceAi.choose(ai,sa,offered)==offered.get(1),"freshly acquired land changes pending choice to Nonland");ai.getZone(ZoneType.Hand).remove(land);
        check(AbundanceAi.choose(ai,sa,offered)==offered.get(0),"land leaving hand changes choice back to Land");
        add("Forest",ai,ZoneType.Battlefield);check(AbundanceAi.choose(ai,sa,offered)==offered.get(0),"five lands choose Land");add("Forest",ai,ZoneType.Battlefield);check(AbundanceAi.choose(ai,sa,offered)==offered.get(1),"fresh sixth land chooses Nonland");
        check(AbundanceAi.choose(ai,sa,List.of(offered.get(0)))==offered.get(0),"filtered mandatory choice uses only native offered legal branch");check(AbundanceAi.choose(ai,sa,List.of())==null,"no offered choices never fabricates an ability");
        check(!AbundanceAi.accept(p(1),aura.getReplacementEffects().get(0),sa,ai),"wrong controller cannot authorize replacement");check(!AbundanceAi.accept(ai,aura.getReplacementEffects().get(0),sa,p(1)),"wrong affected player cannot authorize replacement");
        ready();check(accept(),"unmodified original replacement accepted");aura.getReplacementEffects().get(0).putParam("Optional","False");check(!accept(),"modified optional replacement template held");
        ready();aura.getReplacementEffects().get(0).getOverridingAbility().getAdditionalAbilityList("Choices").get(0).putParam("FoundDestination","Graveyard");check(!accept(),"modified reveal/movement branch held");
        for(String engine:List.of("Teferi's Ageless Insight","Thought Reflection","Notion Thief","Sylvan Library","Howling Mine","Psychosis Crawler","Underworld Dreams")){
            fresh();permanent(engine,1);check(!choose(),"cast defers uncertain draw engine: "+engine);
            ready();permanent(engine,1);check(!accept(),"replacement defers uncertain draw engine: "+engine);
        }
        ready();Card other=permanent("Abundance",1);check(!accept(),"second public draw replacement conservatively deferred");other.setPhasedOut(p(1));refresh();check(accept(),"phased competing replacement does not block original");
    }
    interface Change{void apply();}
    static void stale(String label,Change change){fresh();check(choose(),label+": initial plan");change.apply();long tapped=ai.getLandsInPlay().stream().filter(Card::isTapped).count();int hand=ai.getCardsIn(ZoneType.Hand).size();check(!ComputerUtil.handlePlayingSpellAbility(ai,spell,null),label+": cancels before costs");check(ai.getLandsInPlay().stream().filter(Card::isTapped).count()==tapped&&ai.getCardsIn(ZoneType.Hand).size()==hand&&game.getStack().isEmpty(),label+": no source or mana mutation");}
    static void casting(){
        fresh();nativeCast("normal cast");
        fresh();permanent("Sphere of Resistance",1);check(!choose(),"unfunded native tax holds");add("Forest",ai,ZoneType.Battlefield);nativeCast("funded native tax");check(spell.getPayingMana().size()==5,"exact native tax paid");
        fresh();ai.getLandsInPlay().stream().limit(3).forEach(c->{c.setTapped(true);add("Plains",ai,ZoneType.Battlefield);});check(!choose(),"generic surplus does not replace second green");
        fresh();permanent("Abundance",0);check(!choose(),"duplicate own copy held");fresh();game.getPhaseHandler().devModeSet(PhaseType.MAIN1,ai);check(!choose(),"first main holds");game.getPhaseHandler().devModeSet(PhaseType.MAIN2,p(1));check(!choose(),"opponent second main holds");
        stale("new duplicate",()->permanent("Abundance",0));stale("lost green",()->ai.getLandsInPlay().forEach(c->c.setTapped(true)));stale("unfunded new tax",()->permanent("Sphere of Resistance",1));
        stale("new draw engine",()->permanent("Sylvan Library",2));stale("profile removed",()->AiOverrideProfile.setActive("default"));stale("source incarnation",()->aura.setGameTimestamp(game.getNextTimestamp()));stale("source left hand",()->game.getAction().moveToGraveyard(aura,null));stale("timing changed",()->game.getPhaseHandler().devModeSet(PhaseType.MAIN1,ai));
        stale("effect branch changed",()->aura.getReplacementEffects().get(0).getOverridingAbility().getAdditionalAbilityList("Choices").get(1).putParam("Valid","Card"));
        stale("new Silence",()->{var s=add("Silence",p(1),ZoneType.Hand).getFirstSpellAbility();s.setActivatingPlayer(p(1));forge.game.ability.AbilityUtils.resolve(s);refresh();});
        fresh();check(!AbundanceAi.preflight(ai,spell),"unselected spell cannot bypass native decision");var busy=add("Opt",p(1),ZoneType.Hand).getFirstSpellAbility();busy.setActivatingPlayer(p(1));game.getStack().add(busy);check(!choose(),"busy stack blocks normal hand cast");
        fresh();check(choose(),"old game plan selected");var old=spell;fresh();check(!AbundanceAi.preflight(ai,old),"old game plan cannot authorize new game's same card IDs");
        fresh();check(choose(),"interruptible cast selected");Thread.currentThread().interrupt();check(!AbundanceAi.preflight(ai,spell),"interruption cannot authorize cast");Thread.interrupted();check(AbundanceAi.preflight(ai,spell),"cleared interruption retains fresh plan");
    }
    static class CountRandom extends Random{int calls;@Override protected int next(int bits){calls++;return super.next(bits);}}
    static void commanderRules(){
        fresh();game.getRules().addAppliedVariant(forge.game.GameType.Commander);for(var p:game.getPlayers())p.createCommanderEffect();refresh();check(choose(),"exact native Commander return rule does not block casting");nativeCast("native Commander rule");library("Centaur Courser","Forest","Island");ai.drawCard();check(hand().equals(List.of("Forest"))&&ai.getNumDrawnThisTurn()==0,"native Commander game uses original replacement");
        ready();game.getRules().addAppliedVariant(forge.game.GameType.Commander);for(var p:game.getPlayers())p.createCommanderEffect();refresh();library("Yargle and Multani","Forest");var commander=ai.getCardsIn(ZoneType.Library).get(0);ai.addCommander(commander);add("Forest",ai,ZoneType.Hand);check(accept(),"native Commander rule remains eligible with commander in library");ai.drawCard();var current=game.getCardState(commander,null);check(current!=null&&(current.isInZone(ZoneType.Hand)||current.isInZone(ZoneType.Command))&&ai.getNumDrawnThisTurn()==0,"found commander obeys native hand/command replacement handling");
        ready();game.getRules().addAppliedVariant(forge.game.GameType.Commander);for(var p:game.getPlayers())p.createCommanderEffect();refresh();var eff=ai.getCardsIn(ZoneType.Command).stream().filter(c->c.getName().equals("Commander Effect")).findFirst().orElseThrow();eff.getReplacementEffects().get(0).putParam("ValidCard","Card.YouOwn");check(!accept(),"broadened Commander movement template remains deferred");
        ready();game.getRules().addAppliedVariant(forge.game.GameType.Commander);for(var p:game.getPlayers())p.createCommanderEffect();refresh();eff=ai.getCardsIn(ZoneType.Command).stream().filter(c->c.getName().equals("Commander Effect")).findFirst().orElseThrow();eff.getReplacementEffects().get(0).getOverridingAbility().putParam("Destination","Hand");check(!accept(),"altered Commander return destination is not exempted");
    }
    static void purity(){
        ready();library("Forest","Centaur Courser");add("Sylvan Library",p(1),ZoneType.Hand);var sa=context();var list=sa.getAdditionalAbilityList("Choices");CountRandom rng=new CountRandom();forge.util.MyRandom.setRandom(rng);var before=lib();int hand=hand().size();long timestamp=game.getTimestamp();check(accept()&&AbundanceAi.choose(ai,sa,list)==list.get(0),"pure decision ignores hidden opposing draw engine");check(rng.calls==0,"acceptance and type choice consume no policy RNG");check(before.equals(lib())&&hand().size()==hand&&game.getTimestamp()==timestamp&&ai.getManaPool().isEmpty(),"decisions preserve library order, zones, timestamp and resources");
        library("Centaur Courser","Forest");check(AbundanceAi.choose(ai,sa,list)==list.get(0),"reversing hidden library order cannot change Land decision");library("Walking Corpse");check(AbundanceAi.choose(ai,sa,list)==list.get(0),"even no matching hidden type cannot change Land decision");
        fresh();rng=new CountRandom();forge.util.MyRandom.setRandom(rng);check(AbundanceAi.considerSpell(ai,spell).willingToPlay()&&AbundanceAi.preflight(ai,spell),"private affordability permits normal cast");check(rng.calls==0&&ai.getLandsInPlay().stream().noneMatch(Card::isTapped)&&aura.isInZone(ZoneType.Hand),"cast forecast uses no RNG or live reservations/payment");
        ready();library("Centaur Courser","Forest");AiOverrideProfile.setActive("default");check(ComputerUtilCard.isCardRemAIDeck(aura),"default original AI block retained");check(!accept(),"default optional replacement confirmation remains native rejection");var nativeChoice=context();check(SpellApiToAi.Converter.get(nativeChoice).chooseSingleSpellAbility(ai,nativeChoice,new ArrayList<SpellAbility>(nativeChoice.getAdditionalAbilityList("Choices")),Map.of())==nativeChoice.getAdditionalAbilityList("Choices").get(0),"default generic mode chooser retains original first-branch fallback");ai.drawCard();check(hand().equals(List.of("Centaur Courser"))&&ai.getNumDrawnThisTurn()==1,"default forced-board fixture still performs ordinary draw");
        fresh();AiOverrideProfile.setActive("deadly-tempest");check(ComputerUtilCard.isCardRemAIDeck(aura)&&!AbundanceAi.applies(aura),"unrelated profile does not unlock Abundance");check(aura.getRules().getAiHints().getRemAIDecks(),"original script warning retained");
    }
    public static void main(String[]args){GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);casting();effects();choices();commanderRules();purity();System.out.println("DONE "+checks+" Abundance checks");}
}
