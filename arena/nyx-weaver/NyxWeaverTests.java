import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.game.*;
import forge.game.ability.AbilityKey;
import forge.game.card.*;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.spellability.*;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import java.util.*;

/** Original-script controller selection, native costs/movement/mill and stale-plan regressions. */
public class NyxWeaverTests extends DarksteelMutationTests {
 static int checks;
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
 static void library(int n){for(Card c:new ArrayList<>(ai.getCardsIn(ZoneType.Library)))ai.getZone(ZoneType.Library).remove(c);for(int i=0;i<n;i++)add(i%2==0?"Forest":"Grizzly Bears",ai,ZoneType.Library);refresh();}
 static void lands(int n){for(Card c:new ArrayList<>(ai.getLandsInPlay()))ai.getZone(ZoneType.Battlefield).remove(c);for(int i=0;i<n;i++)add(i%2==0?"Forest":"Swamp",ai,ZoneType.Battlefield);refresh();}
 static void fresh(){setup();ai.getZone(ZoneType.Hand).remove(aura);aura=add("Nyx Weaver",ai,ZoneType.Hand);spell=aura.getFirstSpellAbility();spell.setActivatingPlayer(ai);AiOverrideProfile.setActive("nyx-weaver");lands(9);library(12);}
 static void ready(){fresh();ai.getZone(ZoneType.Hand).remove(aura);aura=permanent("Nyx Weaver",0);spell=aura.getSpellAbilities().stream().filter(s->!s.isSpell()).findFirst().orElseThrow();spell.setActivatingPlayer(ai);}
 static Card grave(String name){Card c=add(name,ai,ZoneType.Graveyard);refresh();return c;}
 static boolean willing(){return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai,spell).willingToPlay();}
 static void commander(){game.getRules().addAppliedVariant(GameType.Commander);for(var p:game.getPlayers())p.createCommanderEffect();refresh();}
 static void threat(String name){var c=add(name,p(1),ZoneType.Hand);var s=c.getFirstSpellAbility();s.setActivatingPlayer(p(1));s.getTargets().add(aura);game.getStack().add(s);}
 static void paid(Card target,String label){
  check(willing(),label+" native decision");var selected=selection();check(selected!=null&&selected.isSpell()==spell.isSpell(),label+" native controller selection");spell=selected;
  if(target!=null)check(spell.getTargetCard()==target,label+" exact native target");
  check(NyxWeaverAi.preflight(ai,spell),label+" fresh preflight");check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null),label+" native payment enters stack");
  check(spell.getPayingMana().size()>=3&&spell.getPayingMana().stream().anyMatch(m->m.toString().equals("B"))&&spell.getPayingMana().stream().anyMatch(m->m.toString().equals("G")),label+" original colored mana paid");
  if(target!=null)check(game.getCardState(aura,null).isInZone(ZoneType.Exile),label+" exact source exiled as native cost before resolution");
  game.getStack().resolveStack();refresh();
  check(target==null?game.getCardState(aura,null).isInPlay():game.getCardState(target,null).isInZone(ZoneType.Hand),label+" original effect resolves");
 }
 static void casts(){
  fresh();check(willing(),"normal creature cast accepted");paid(null,"native creature cast");check(game.getCardState(aura,null).getNetPower()==2&&game.getCardState(aura,null).getNetToughness()==3&&game.getCardState(aura,null).hasKeyword("Reach"),"native 2/3 reach retained");
  for(int n:new int[]{0,2,4,7,8,9}){fresh();library(n);check(willing()==(n>=8),"cast library boundary "+n);}
  fresh();permanent("Nyx Weaver",0);check(!willing(),"no duplicate own Weaver");
  fresh();game.getPhaseHandler().devModeSet(PhaseType.MAIN1,ai);check(!willing(),"cast waits until own second main");game.getPhaseHandler().devModeSet(PhaseType.MAIN2,p(1));check(!willing(),"cast holds on opponent turn");
  fresh();lands(2);check(!willing(),"two sources cannot cast 1BG");fresh();lands(3);check(willing(),"three native colored sources cast 1BG");
  fresh();lands(0);for(int i=0;i<9;i++)add("Forest",ai,ZoneType.Battlefield);refresh();check(!willing(),"green-only mana cannot cast black shard");
  fresh();permanent("Howling Mine",1);check(!willing(),"extra public draw engine defers casting");
  fresh();commander();check(willing(),"native Commander rules permit cast");paid(null,"Commander cast");
 }
 static void recovery(){
  ready();check(!willing(),"empty graveyard holds and no exile paid");grave("Grizzly Bears");check(!willing(),"healthy body retained instead of cheap creature");grave("Forest");check(!willing(),"healthy body retained instead of land");
  ready();lands(7);var target=grave("Rumbling Baloth");check(willing()&&spell.getTargetCard()==target,"exact joint three-plus-four mana qualifies");paid(target,"native value recovery");check(ai.getLandsInPlay().stream().filter(Card::isTapped).count()==3,"activation spends three live mana only");var follow=game.getCardState(target,null).getFirstSpellAbility();follow.setActivatingPlayer(ai);check(ComputerUtil.handlePlayingSpellAbility(ai,follow,null),"recovered ordinary creature remains natively payable after activation");game.getStack().resolveStack();check(game.getCardState(target,null).isInPlay(),"native later creature cast resolves without forced policy choice");
  ready();lands(6);grave("Rumbling Baloth");check(!willing(),"six total mana cannot fund activation plus four-mana target");
  ready();lands(7);var low=grave("Rumbling Baloth");var high=grave("Craw Wurm");check(willing()&&spell.getTargetCard()==low,"unaffordable higher creature filtered before valuation");lands(9);check(willing()&&spell.getTargetCard()==high,"native creature value prefers jointly funded larger creature");
  ready();var first=grave("Rumbling Baloth");grave("Rumbling Baloth");check(willing()&&spell.getTargetCard()==first,"equal-valued targets use stable card ID");
  ready();grave("Harmonize");check(willing(),"ordinary four-mana noncreature can qualify");
  ready();grave("Abundance");check(!willing(),"active profile does not authorize blocked recovered spell");
  ready();lands(9);grave("Serra Angel");check(!willing(),"joint affordability preserves recovered target white requirement");add("Plains",ai,ZoneType.Battlefield);add("Plains",ai,ZoneType.Battlefield);refresh();check(willing(),"joint native colored payment qualifies once two white sources exist");
  ready();grave("Craw Wurm");game.getPhaseHandler().devModeSet(PhaseType.MAIN1,ai);check(!willing(),"ordinary recovery holds in first main");game.getPhaseHandler().devModeSet(PhaseType.MAIN2,p(1));check(!willing(),"ordinary recovery holds on opponent main");
  ready();grave("Rumbling Baloth");permanent("Suppression Field",1);lands(8);check(!willing(),"activation tax included before follow-up funding");lands(9);check(willing(),"activation tax jointly funded at nine");
  ready();grave("Rumbling Baloth");commander();paid(spell.getTargets().isEmpty()?ai.getCardsIn(ZoneType.Graveyard).get(0):spell.getTargetCard(),"Commander recovery");
 }
 static void lowLibrary(){
  for(int n:new int[]{0,1,2,4,5}){ready();library(n);var target=grave("Forest");check(willing()==(n<=4),"low-library cash-out boundary "+n);if(n<=4){int drawn=ai.getNumDrawnThisTurn();paid(target,"low library "+n);check(ai.getCardsIn(ZoneType.Library).size()==n&&ai.getNumDrawnThisTurn()==drawn,"recovery never draws or adds an invented library reserve at "+n);}}
  ready();library(2);grave("Forest");game.getPhaseHandler().devModeSet(PhaseType.END_OF_TURN,p(3));check(willing(),"last-opponent end step allows cash-out before next upkeep");
  ready();library(2);grave("Forest");game.getPhaseHandler().devModeSet(PhaseType.END_OF_TURN,p(1));check(!willing(),"earlier opponent end step holds");
  ready();library(2);grave("Forest");lands(2);check(!willing(),"no invented cash-out without payment");
  ready();library(2);check(!willing(),"no invented cash-out without legal target");
 }
 static void rescue(){
  ready();var target=grave("Grizzly Bears");threat("Murder");check(willing()&&spell.getTargetCard()==target,"confirmed removal allows cheap usable rescue target");paid(target,"native doomed-source recovery");game.getStack().resolveStack();check(game.getCardState(aura,null).isInZone(ZoneType.Exile),"pending original removal cannot restore or retarget exiled source");
  ready();grave("Forest");threat("Murder");check(willing(),"doomed-source rescue allows legal land");
  ready();grave("Forest");threat("Lightning Bolt");check(willing(),"three damage dooms native three-toughness Weaver");
  ready();grave("Forest");threat("Shock");check(!willing(),"two damage does not doom healthy Weaver");
  ready();grave("Forest");threat("Unsummon");check(!willing(),"nonlethal bounce does not count as doomed-source rescue");
  for(boolean trade:new boolean[]{false,true}){ready();grave("Forest");game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS,p(1));var enemy=permanent(trade?"Typhoid Rats":"Ancient Brontodon",1);var combat=new Combat(p(1));game.getPhaseHandler().setCombat(combat);combat.addAttacker(enemy,ai);combat.addBlocker(enemy,aura);combat.fireTriggersForUnblockedAttackers(game);refresh();check(willing()==!trade,"declared losing blocker rescue preserves favorable trade="+trade);}
  ready();grave("Forest");ai.setLife(5,null);var enemy=permanent("Terra Stomper",1);game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS,p(1));var combat=new Combat(p(1));game.getPhaseHandler().setCombat(combat);combat.addAttacker(enemy,ai);combat.addBlocker(enemy,aura);combat.fireTriggersForUnblockedAttackers(game);refresh();check(!willing(),"do not remove blocker into lethal trample");
 }
 static void upkeep(){
  ready();library(5);int grave=ai.getCardsIn(ZoneType.Graveyard).size(),drawn=ai.getNumDrawnThisTurn();game.getPhaseHandler().devModeSet(PhaseType.UPKEEP,ai);refresh();game.getTriggerHandler().runTrigger(TriggerType.Phase,AbilityKey.mapFromPlayer(ai),false);game.getTriggerHandler().runWaitingTriggers();game.getStack().addAllTriggeredAbilitiesToStack();check(!game.getStack().isEmpty(),"original upkeep trigger enters native stack");game.getStack().resolveStack();refresh();check(ai.getCardsIn(ZoneType.Library).size()==3&&ai.getCardsIn(ZoneType.Graveyard).size()==grave+2,"original mandatory trigger mills exactly two");check(ai.getNumDrawnThisTurn()==drawn,"native mill is not a draw");
  ready();library(2);var returned=grave("Forest");game.getPhaseHandler().devModeSet(PhaseType.UPKEEP,ai);refresh();game.getTriggerHandler().runTrigger(TriggerType.Phase,AbilityKey.mapFromPlayer(ai),false);game.getTriggerHandler().runWaitingTriggers();game.getStack().addAllTriggeredAbilitiesToStack();check(!willing(),"low-library branch never pretends to cancel pending mill");AiOverrideProfile.setActive("default");spell.resetTargets();spell.getTargets().add(returned);check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"forced native legality fixture can activate over pending mill");game.getStack().resolveStack();check(game.getCardState(aura,null).isInZone(ZoneType.Exile),"source leaves before pending original mill resolves");game.getStack().resolveStack();refresh();check(ai.getCardsIn(ZoneType.Library).isEmpty()&&ai.getNumDrawnThisTurn()==0,"already-triggered mill still resolves after source exile");check(!ai.hasLost(),"emptying library by mill alone is not a decking draw");
 }
 static void counterplay(){
  ready();var target=grave("Rumbling Baloth");check(willing(),"value recovery selected before native response");var selected=selection();check(selected!=null&&ComputerUtil.handlePlayingSpellAbility(ai,selected,null),"native recovery pays before target response");check(game.getCardState(aura,null).isInZone(ZoneType.Exile),"source cost already exiled before opponent response");var reply=add("Scarab Feast",p(1),ZoneType.Hand).getFirstSpellAbility();reply.setActivatingPlayer(p(1));reply.getTargets().add(target);game.getStack().add(reply);game.getStack().resolveStack();check(game.getCardState(target,null).isInZone(ZoneType.Exile),"native opponent response exiles recovery target");game.getStack().resolveStack();check(game.getCardState(aura,null).isInZone(ZoneType.Exile)&&game.getCardState(target,null).isInZone(ZoneType.Exile)&&ai.getCardsIn(ZoneType.Hand).isEmpty(),"native recovery fizzles without refunding exile cost");
  ready();var first=grave("Rumbling Baloth");var second=grave("Rumbling Baloth");check(willing()&&spell.getTargetCard()==first,"target bound before attempted substitution");spell.resetTargets();spell.getTargets().add(second);check(!NyxWeaverAi.preflight(ai,spell),"preflight cannot silently substitute another target");
  ready();add("Craw Wurm",p(1),ZoneType.Graveyard);check(!willing(),"never recover opposing graveyard card");
 }
 static void uncertainty(){
  for(String name:List.of("Rest in Peace","Leyline of the Void","Howling Mine","Blood Artist","Grim Haruspex","Harsh Mentor")){ready();grave("Craw Wurm");permanent(name,1);check(!willing(),"uncertain movement/library/consequence engine holds: "+name);}
  ready();grave("Craw Wurm");var engine=permanent("Rest in Peace",1);engine.setPhasedOut(p(1));refresh();check(willing(),"phased-out replacement ignored");
  ready();grave("Craw Wurm");add("Rest in Peace",p(1),ZoneType.Hand);check(willing(),"hidden opposing replacement ignored");
  ready();grave("Craw Wurm");spell.putParam("Destination","Battlefield");check(!willing(),"modified recovery template held");
  ready();grave("Craw Wurm");aura.getTriggers().get(0).ensureAbility().putParam("NumCards","20");check(!willing(),"modified original upkeep template held");
  ready();grave("Craw Wurm");ai.setLife(4,null);check(!willing(),"low friendly life reserve holds");
 }
 static void stale(){
  for(String change:List.of("profile","source","stamp","target","target-stamp","better","mana","timing","engine","shape")){
   ready();var target=grave("Rumbling Baloth");check(willing(),"selected recovery before "+change);
   switch(change){case "profile"->AiOverrideProfile.setActive("default");case "source"->ai.getZone(ZoneType.Battlefield).remove(aura);case "stamp"->aura.setGameTimestamp(game.getNextTimestamp());case "target"->ai.getZone(ZoneType.Graveyard).remove(target);case "target-stamp"->target.setGameTimestamp(game.getNextTimestamp());case "better"->grave("Craw Wurm");case "mana"->lands(2);case "timing"->game.getPhaseHandler().devModeSet(PhaseType.MAIN1,ai);case "engine"->permanent("Rest in Peace",1);case "shape"->spell.putParam("ValidTgts","Card");}
   check(!NyxWeaverAi.preflight(ai,spell),"fresh recovery rejects stale "+change);check(!ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"native payment cancels stale recovery "+change);check(game.getStack().isEmpty()&&ai.getCardsIn(ZoneType.Exile).isEmpty(),"stale recovery pays no exile "+change);
  }
  for(String change:List.of("profile","library","duplicate","mana","timing","stamp")){fresh();check(willing(),"selected cast before "+change);switch(change){case "profile"->AiOverrideProfile.setActive("default");case "library"->library(7);case "duplicate"->permanent("Nyx Weaver",0);case "mana"->lands(2);case "timing"->game.getPhaseHandler().devModeSet(PhaseType.MAIN1,ai);case "stamp"->aura.setGameTimestamp(game.getNextTimestamp());}check(!NyxWeaverAi.preflight(ai,spell),"fresh cast rejects stale "+change);check(!ComputerUtil.handlePlayingSpellAbility(ai,spell,null)&&game.getStack().isEmpty(),"stale cast pays no native costs "+change);}
  ready();grave("Craw Wurm");check(!NyxWeaverAi.preflight(ai,spell),"unplanned direct recovery cannot bypass decision");check(willing(),"old-game plan made");var old=spell;ready();grave("Craw Wurm");check(!NyxWeaverAi.preflight(ai,old),"old game cannot authorize reused card IDs");
  ready();grave("Craw Wurm");check(willing(),"plan before interruption");Thread.currentThread().interrupt();check(!NyxWeaverAi.preflight(ai,spell),"interruption cancels forecast");Thread.interrupted();check(NyxWeaverAi.preflight(ai,spell),"fresh retry works after cleared interruption");
 }
 static class CountRandom extends Random{int calls;@Override protected int next(int bits){calls++;return super.next(bits);}}
 static void purity(){
  ready();grave("Craw Wurm");var rng=new CountRandom();forge.util.MyRandom.setRandom(rng);var order=ai.getCardsIn(ZoneType.Library).stream().map(Card::getId).toList();long stamp=game.getTimestamp();check(willing()&&NyxWeaverAi.preflight(ai,spell),"private native recovery/funding forecast accepts");check(rng.calls==0&&ai.getManaPool().isEmpty()&&ai.getLandsInPlay().stream().noneMatch(Card::isTapped)&&aura.isInPlay()&&order.equals(ai.getCardsIn(ZoneType.Library).stream().map(Card::getId).toList())&&game.getTimestamp()==stamp,"forecast has no policy RNG, live costs, library order or timestamp effects");
  fresh();rng=new CountRandom();forge.util.MyRandom.setRandom(rng);check(NyxWeaverAi.consider(ai,spell).willingToPlay()&&NyxWeaverAi.preflight(ai,spell)&&rng.calls==0,"cast forecast has no policy RNG");
  ready();var cheap=grave("Grizzly Bears");AiOverrideProfile.setActive("default");check(willing()&&spell.getTargetCard()==cheap,"forced default native cheap recovery preserved");check(ComputerUtilCard.isCardRemAIDeck(aura)&&selection()==null,"default controller still blocks Nyx ability");check(!NyxWeaverAi.applies(spell)&&NyxWeaverAi.preflight(ai,spell),"default preflight remains native");
  fresh();AiOverrideProfile.setActive("treacherous-terrain");check(ComputerUtilCard.isCardRemAIDeck(aura)&&!NyxWeaverAi.applies(spell),"unrelated profile does not unlock Weaver");check(aura.getRules().getAiHints().getRemAIDecks(),"original script warning retained");
 }
 public static void main(String[]args){GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);casts();recovery();lowLibrary();rescue();upkeep();counterplay();uncertainty();stale();purity();System.out.println("DONE "+checks+" Nyx Weaver checks");}
}
