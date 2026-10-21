import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.game.*;
import forge.game.card.*;
import forge.game.phase.PhaseType;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import java.util.*;

/** Original-script, native controller/payment/resolution and adversarial pre-cost fixtures. */
public class TreacherousTerrainTests extends DarksteelMutationTests {
 static int checks;
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
 static void lands(int seat,int count){var player=p(seat);for(var c:new ArrayList<>(player.getLandsInPlay()))player.getZone(ZoneType.Battlefield).remove(c);for(int i=0;i<count;i++)add(i%2==0?"Forest":"Mountain",player,ZoneType.Battlefield);refresh();}
 static void fresh(){setup();ai.getZone(ZoneType.Hand).remove(aura);aura=add("Treacherous Terrain",ai,ZoneType.Hand);spell=aura.getFirstSpellAbility();spell.setActivatingPlayer(ai);AiOverrideProfile.setActive("treacherous-terrain");lands(0,8);lands(1,3);lands(2,3);lands(3,2);}
 static SpellAbility cycle(){var s=aura.getSpellAbilities().stream().filter(x->!x.isSpell()&&x.getApi()==forge.game.ability.ApiType.ChangeZone).findFirst().orElseThrow();s.setActivatingPlayer(ai);return s;}
 static boolean willing(SpellAbility sa){return SpellApiToAi.Converter.get(sa).canPlayWithSubs(ai,sa).willingToPlay();}
 static void library(String...names){for(Card c:new ArrayList<>(ai.getCardsIn(ZoneType.Library)))ai.getZone(ZoneType.Library).remove(c);for(var n:names)add(n,ai,ZoneType.Library);refresh();}
 static void commander(){game.getRules().addAppliedVariant(GameType.Commander);for(var player:game.getPlayers())player.createCommanderEffect();refresh();}
 static void paid(SpellAbility expected,int mana,String label){check(willing(expected),label+" native AI decision");var selected=selection();check(selected!=null&&selected.isSpell()==expected.isSpell(),label+" normal controller chooses correct native mode");check(TreacherousTerrainAi.preflight(ai,selected),label+" fresh preflight");check(ComputerUtil.handlePlayingSpellAbility(ai,selected,null),label+" native payment enters stack");check(selected.getPayingMana().size()==mana,label+" exact native mana count");check(game.getCardState(aura,null).isInZone(ZoneType.Graveyard)==!expected.isSpell(),label+" native discard only for cycling");game.getStack().resolveStack();refresh();check(game.getStack().isEmpty(),label+" original chain resolves");}
 static void casting(){
  fresh();check(willing(spell),"eight aggregate damage qualifies");int[] before={ai.getLife(),p(1).getLife(),p(2).getLife(),p(3).getLife()};paid(spell,8,"eight-damage spell");check(ai.getLife()==before[0]&&p(1).getLife()==before[1]-3&&p(2).getLife()==before[2]-3&&p(3).getLife()==before[3]-2,"original RepeatEach deals each opponent their own land count");check(game.getCardState(aura,null).isInZone(ZoneType.Graveyard),"original sorcery goes to graveyard");
  fresh();lands(3,1);check(!willing(spell),"seven aggregate damage holds");lands(3,2);check(willing(spell),"fresh land change reaches exact eight");
  fresh();lands(1,0);lands(2,0);lands(3,0);check(!willing(spell),"zero damage holds");lands(2,3);p(2).setLife(3,null);check(willing(spell),"lethal exception below eight aggregate");p(2).setLife(4,null);check(!willing(spell),"three damage against four life is not lethal");
  fresh();lands(0,7);check(!willing(spell),"seven mana cannot pay original eight-mana spell");
  fresh();lands(0,8);for(var c:new ArrayList<>(ai.getLandsInPlay())){ai.getZone(ZoneType.Battlefield).remove(c);add("Forest",ai,ZoneType.Battlefield);}refresh();check(!willing(spell),"eight green sources cannot pay red shard");
  fresh();permanent("Thalia, Guardian of Thraben",1);check(!willing(spell),"native spell tax prevents eight-mana payment");lands(0,9);check(willing(spell),"native ninth source funds tax");paid(spell,9,"taxed spell");
  fresh();game.getPhaseHandler().devModeSet(PhaseType.MAIN1,ai);check(!willing(spell),"normal spell holds in first main");game.getPhaseHandler().devModeSet(PhaseType.MAIN2,p(1));check(!willing(spell),"normal spell holds on opponent turn");
  fresh();var busy=add("Opt",p(1),ZoneType.Hand).getFirstSpellAbility();busy.setActivatingPlayer(p(1));game.getStack().add(busy);check(!willing(spell),"normal spell holds with busy stack");
  fresh();permanent("Witchbane Orb",1);check(p(1).hasKeyword("Hexproof"),"native opposing player hexproof active");check(willing(spell),"nontargeting damage ignores hexproof target eligibility");paid(spell,8,"hexproof opponent");
  fresh();ai.setTeam(0);p(1).setTeam(0);lands(2,4);lands(3,4);int ally=p(1).getLife();check(willing(spell),"allied lands excluded from enemy threshold");paid(spell,8,"allied game");check(p(1).getLife()==ally,"original spell does not damage ally");
  fresh();ai.setTeam(0);p(1).setTeam(0);lands(2,3);lands(3,4);check(!willing(spell),"allied land count never inflates seven enemy damage");p(1).setLife(4,null);check(!willing(spell),"ally below five-life reserve holds");
  fresh();ai.setLife(4,null);check(!willing(spell),"self below five-life reserve holds");ai.setLife(5,null);check(willing(spell),"exact five-life self reserve allowed");
  fresh();commander();check(willing(spell),"native Commander rules permit spell");paid(spell,8,"Commander spell");
 }
 static void cycling(){
  fresh();lands(0,2);library("Mountain","Forest","Walking Corpse");var s=cycle();check(willing(s),"land scarcity admits basic landcycling");int lib=ai.getCardsIn(ZoneType.Library).size(),hand=ai.getCardsIn(ZoneType.Hand).size(),drawn=ai.getNumDrawnThisTurn();paid(s,2,"basic landcycle");check(ai.getCardsIn(ZoneType.Hand).size()==hand&&ai.getCardsIn(ZoneType.Hand).stream().anyMatch(Card::isLand),"discarded Terrain replaced by one native basic in hand");check(ai.getCardsIn(ZoneType.Library).size()==lib-1&&ai.getNumDrawnThisTurn()==drawn,"basic search reduces library by one and is not a draw");
  fresh();lands(0,5);check(willing(cycle()),"five lands below exact six-land cycling boundary");lands(0,6);check(!willing(cycle()),"six lands holds cycling");
  fresh();lands(0,2);add("Forest",ai,ZoneType.Hand);check(!willing(cycle()),"land already in hand holds cycling");
  fresh();lands(0,1);check(!willing(cycle()),"one mana cannot pay cycling");
  fresh();lands(0,2);game.getPhaseHandler().devModeSet(PhaseType.MAIN1,ai);check(willing(cycle()),"first-main cycle allowed for current land drop");paid(cycle(),2,"first-main cycle");
  fresh();lands(0,2);game.getPhaseHandler().devModeSet(PhaseType.END_OF_TURN,ai);check(!willing(cycle()),"cycling holds outside own main phases");game.getPhaseHandler().devModeSet(PhaseType.MAIN2,p(1));check(!willing(cycle()),"cycling holds on opponent turn");
  fresh();lands(0,2);library("Walking Corpse","Taiga");check(!willing(cycle()),"no basic remaining rejects native search despite nonbasic dual");library();check(!willing(cycle()),"empty library rejects fruitless basic search");library("Mountain");check(willing(cycle()),"one-card basic library needs no invented draw reserve");paid(cycle(),2,"one-basic library");check(ai.getCardsIn(ZoneType.Library).isEmpty()&&!ai.hasLost(),"native search into empty library does not deck us");
  fresh();lands(0,2);library("Forest","Mountain");for(var c:new ArrayList<>(ai.getLandsInPlay())){ai.getZone(ZoneType.Battlefield).remove(c);add("Forest",ai,ZoneType.Battlefield);}refresh();paid(cycle(),2,"native color fixing");check(ai.getCardsIn(ZoneType.Hand).stream().anyMatch(c->c.getName().equals("Mountain")),"reuse native basic selection to fill missing red");
  fresh();lands(0,2);permanent("Stranglehold",1);check(!willing(cycle()),"native library-search prohibition holds");
  fresh();lands(0,2);permanent("Leonin Arbiter",1);check(!willing(cycle()),"unpaid native search tax holds without invented tax payment");
  fresh();lands(0,2);commander();check(willing(cycle()),"exact native Commander return rule permits basic cycling");paid(cycle(),2,"Commander cycle");
  fresh();lands(0,5);for(int i=0;i<3;i++)permanent("Sol Ring",0);check(willing(spell),"five lands plus rocks fund useful full spell");check(!willing(cycle()),"eligible funded full spell preferred over basic cycling");
 }
 static void boundaries(){
  for(String name:List.of("Energy Field","Glacial Chasm","Palisade Giant")){fresh();permanent(name,1);check(!willing(spell),"explicit native prevention/redirection holds: "+name);}
  fresh();lands(1,3);lands(2,0);lands(3,0);p(1).setLife(3,null);permanent("Platinum Angel",1);check(!willing(spell),"cannot-lose opponent is not counted as lethal below threshold");
  fresh();lands(0,2);permanent("Suppression Field",1);check(!willing(cycle()),"native activation tax blocks two-mana cycling");lands(0,4);check(willing(cycle()),"four sources fund native taxed cycling");paid(cycle(),4,"activation-tax cycle");
  fresh();lands(0,2);var busy=add("Opt",p(1),ZoneType.Hand).getFirstSpellAbility();busy.setActivatingPlayer(p(1));game.getStack().add(busy);check(!willing(cycle()),"cycling holds with busy stack");
 }
 static void safety(){
  for(String name:List.of("Furnace of Rath","Sulfuric Vortex","Painful Quandary","Solphim, Mayhem Dominus","Platinum Emperion")){fresh();permanent(name,1);check(!willing(spell),"uncertain damage/life engine held: "+name);}
  for(String name:List.of("Ob Nixilis, Unshackled","Waste Not","Bone Miser","Spirit Cairn")){fresh();lands(0,2);permanent(name,1);check(!willing(cycle()),"uncertain search/discard trigger held: "+name);}
  fresh();var shield=permanent("Furnace of Rath",1);shield.setPhasedOut(p(1));check(willing(spell),"phased-out damage replacement ignored");
  fresh();add("Furnace of Rath",p(1),ZoneType.Hand);check(willing(spell),"hidden opposing damage engine ignored");
  fresh();spell.getAdditionalAbility("RepeatSubAbility").putParam("NumDmg","99");check(!willing(spell),"modified damage branch is not original template");
  fresh();spell.putParam("RepeatPlayers","Player");check(!willing(spell),"broadened repeated players is not original opponent spell");
  fresh();lands(0,2);var c=cycle();c.putParam("ChangeType","Card");check(!willing(c),"broadened search type held");
  fresh();lands(0,2);commander();var eff=ai.getCardsIn(ZoneType.Command).stream().filter(x->x.getName().equals("Commander Effect")).findFirst().orElseThrow();eff.getReplacementEffects().get(0).putParam("ValidCard","Card.YouOwn");check(!willing(cycle()),"altered Commander movement replacement not exempted");
 }
 static void stale(){
  for(String change:List.of("profile","source","timestamp","mana","lands","life","tax","timing","engine","silence","shape")){
   fresh();check(willing(spell),"selected spell before stale "+change);long stamp=aura.getGameTimestamp();
   switch(change){case "profile"->AiOverrideProfile.setActive("default");case "source"->ai.getZone(ZoneType.Hand).remove(aura);case "timestamp"->aura.setGameTimestamp(game.getNextTimestamp());case "mana"->ai.getLandsInPlay().forEach(x->x.setTapped(true));case "lands"->{lands(1,0);lands(2,0);lands(3,0);}case "life"->ai.setLife(4,null);case "tax"->permanent("Thalia, Guardian of Thraben",1);case "timing"->game.getPhaseHandler().devModeSet(PhaseType.MAIN1,ai);case "engine"->permanent("Furnace of Rath",1);case "silence"->{var silence=permanent("Silence",1);var eff=silence.getFirstSpellAbility();eff.setActivatingPlayer(p(1));forge.game.ability.AbilityUtils.resolve(eff);refresh();}case "shape"->spell.putParam("RepeatPlayers","Player");}
   check(!TreacherousTerrainAi.preflight(ai,spell),"stale spell rejected before costs: "+change);check(!ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"payment path cancels stale spell: "+change);check(game.getStack().isEmpty(),"stale spell never enters native stack: "+change);
  }
  for(String change:List.of("handland","sixlands","no-basic","search-ban","engine","mana","profile","timestamp")){
   fresh();lands(0,2);var c=cycle();check(willing(c),"selected cycle before stale "+change);
   switch(change){case "handland"->add("Forest",ai,ZoneType.Hand);case "sixlands"->lands(0,6);case "no-basic"->library("Walking Corpse");case "search-ban"->permanent("Stranglehold",1);case "engine"->permanent("Ob Nixilis, Unshackled",1);case "mana"->ai.getLandsInPlay().forEach(x->x.setTapped(true));case "profile"->AiOverrideProfile.setActive("default");case "timestamp"->aura.setGameTimestamp(game.getNextTimestamp());}
   check(!TreacherousTerrainAi.preflight(ai,c),"fresh cycle recheck rejects stale "+change);check(!ComputerUtil.handlePlayingSpellAbility(ai,c,null),"native payment cancels stale cycle "+change);check(aura.isInZone(ZoneType.Hand)&&game.getStack().isEmpty(),"stale cycle pays no discard: "+change);
  }
  fresh();check(!TreacherousTerrainAi.preflight(ai,spell),"unplanned direct cast cannot bypass decision");check(willing(spell),"game-local plan created");var old=spell;fresh();check(!TreacherousTerrainAi.preflight(ai,old),"previous game cannot authorize current source IDs");
  fresh();check(willing(spell),"plan before interruption");Thread.currentThread().interrupt();check(!TreacherousTerrainAi.preflight(ai,spell),"interruption cancels forecast");Thread.interrupted();check(TreacherousTerrainAi.preflight(ai,spell),"normal follow-up clears interruption");
 }
 static class CountRandom extends Random {int calls;@Override protected int next(int bits){calls++;return super.next(bits);}}
 static void purity(){
  fresh();var rng=new CountRandom();forge.util.MyRandom.setRandom(rng);int life=ai.getLife();long stamp=game.getTimestamp();check(willing(spell)&&TreacherousTerrainAi.preflight(ai,spell),"deterministic private damage/pay forecast accepts");check(rng.calls==0&&ai.getLife()==life&&game.getTimestamp()==stamp&&ai.getManaPool().isEmpty()&&ai.getLandsInPlay().stream().noneMatch(Card::isTapped),"forecast consumes no policy RNG or live life/mana/timestamp");
  fresh();lands(0,2);var c=cycle();var order=ai.getCardsIn(ZoneType.Library).stream().map(Card::getId).toList();rng=new CountRandom();forge.util.MyRandom.setRandom(rng);check(willing(c)&&TreacherousTerrainAi.preflight(ai,c),"deterministic basic-availability forecast accepts");check(rng.calls==0&&order.equals(ai.getCardsIn(ZoneType.Library).stream().map(Card::getId).toList())&&aura.isInZone(ZoneType.Hand),"cycling decision does not shuffle, discard or consume policy RNG");
  fresh();AiOverrideProfile.setActive("default");check(ComputerUtilCard.isCardRemAIDeck(aura)&&selection()==null,"default controller still blocks Terrain");check(willing(spell),"forced default generic repeat decision remains unchanged");check(!TreacherousTerrainAi.applies(spell)&&TreacherousTerrainAi.preflight(ai,spell),"default payment retains native behavior");check(aura.getRules().getAiHints().getRemAIDecks(),"original script warning retained");
  fresh();AiOverrideProfile.setActive("abundance");check(ComputerUtilCard.isCardRemAIDeck(aura)&&!TreacherousTerrainAi.applies(cycle()),"unrelated profile does not unlock Terrain");
 }
 public static void main(String[]args){GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);casting();cycling();boundaries();safety();stale();purity();System.out.println("DONE "+checks+" Treacherous Terrain checks");}
}
