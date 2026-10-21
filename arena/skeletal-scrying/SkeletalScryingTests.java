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
import java.util.*;

/** Original X B / exile-X payment, draw-X and lose-X through native controller and engine. */
public class SkeletalScryingTests extends KenrithsTransformationTests {
    static void fresh(int mana,int grave,int library,int life,int extras){
        List<RegisteredPlayer> regs=new ArrayList<>();for(int i=0;i<4;i++){LobbyPlayerAi lobby=new LobbyPlayerAi("Scrying-"+i,null);lobby.setAiProfile("Default");regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));}
        GameRules rules=new GameRules(GameType.Constructed);game=new Game(regs,rules,new Match(rules,regs,"Skeletal Scrying fixtures"));game.setAge(GameStage.Play);ai=p(0);ai.setLife(life,null);game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);
        for(int i=0;i<mana;i++){add("Swamp",ai,ZoneType.Battlefield);}for(int i=0;i<grave;i++){add("Swamp",ai,ZoneType.Graveyard);}for(int i=0;i<library;i++){add("Swamp",ai,ZoneType.Library);}for(int i=0;i<extras;i++){add("Storm Crow",ai,ZoneType.Hand);}
        aura=add("Skeletal Scrying",ai,ZoneType.Hand);spell=aura.getFirstSpellAbility();spell.setActivatingPlayer(ai);AiOverrideProfile.setActive("skeletal-scrying");refresh();
    }
    static boolean willing(){return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai,spell).willingToPlay();}
    static CardCollection chosen(){return SkeletalScryingAi.payment(ai,spell,ai.getCardsIn(ZoneType.Graveyard),spell.getXManaCostPaid());}
    static void cast(int x,int mana,String label){
        spell=selection();check(spell!=null,label+": normal controller selects spell");check(spell.getXManaCostPaid()==x,label+": bounded native X");
        CardCollection cards=chosen();check(cards!=null && cards.size()==x,label+": exact bound graveyard payment");
        int life=ai.getLife(),library=ai.getCardsIn(ZoneType.Library).size(),hand=ai.getCardsIn(ZoneType.Hand).size(),grave=ai.getCardsIn(ZoneType.Graveyard).size();
        check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null),label+": native cast/payment enters stack");
        check(spell.getPayingMana().size()==mana && spell.getPayingMana().stream().anyMatch(m->m.toString().equals("B")),label+": native X B adjusted mana paid");
        check(cards.stream().allMatch(c->game.getCardState(c,null).isInZone(ZoneType.Exile)) && ai.getCardsIn(ZoneType.Graveyard).size()==grave-x,label+": exactly X chosen cards actually exiled as cost");
        check(ai.getLife()==life && ai.getCardsIn(ZoneType.Library).size()==library,label+": costs do not replace draw or life loss");
        game.getStack().resolveStack();refresh();
        check(ai.getCardsIn(ZoneType.Library).size()==library-x && ai.getCardsIn(ZoneType.Hand).size()==hand-1+x,label+": original X cards drawn");
        check(ai.getLife()==life-x && ai.getLife()>=5 && ai.getCardsIn(ZoneType.Library).size()>=4,label+": original X life loss and reserves");
        check(game.getCardState(aura,null).isInZone(ZoneType.Graveyard) && game.getStack().isEmpty(),label+": original spell completes normally");
    }
    public static void main(String[] args){
        GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);forge.util.MyRandom.setRandom(new Random(107001));
        fresh(4,5,20,20,0);check(willing(),"ordinary draw accepted");cast(3,4,"X3");
        fresh(3,5,20,20,0);cast(2,3,"mana-limited X2");
        fresh(4,2,20,20,0);cast(2,3,"graveyard-limited X2");
        fresh(4,5,6,20,0);cast(2,3,"library boundary X2");
        fresh(4,5,20,7,0);cast(2,3,"five-life reserve X2");
        fresh(4,5,7,8,2);cast(3,4,"three-card hand and both reserves");
        for(int mana=0;mana<=2;mana++){fresh(mana,5,20,20,0);check(!willing() && selection()==null,"insufficient native mana "+mana);}
        for(int grave=0;grave<=1;grave++){fresh(4,grave,20,20,0);check(!willing(),"fewer than two expendable cards "+grave);}
        for(int library=0;library<=5;library++){fresh(4,5,library,20,0);check(!willing(),"four-card library reserve hold "+library);}
        for(int life=1;life<=6;life++){fresh(4,5,20,life,0);check(!willing(),"five-life reserve hold "+life);}
        fresh(10,10,30,40,0);check(willing() && spell.getXManaCostPaid()==3,"never spend more than three X even with abundant resources");
        fresh(4,5,20,20,3);check(!willing(),"four-card hand holds");
        fresh(4,0,20,20,0);Card first=add("Divination",ai,ZoneType.Graveyard);Card basic=add("Forest",ai,ZoneType.Graveyard);Card last=add("Doom Blade",ai,ZoneType.Graveyard);refresh();
        check(willing(),"ordinary used spells may fund draw");check(chosen().get(0)==basic && chosen().get(1)==first && chosen().get(2)==last,"basic land first then stable public card IDs");cast(3,4,"mixed expendable cards");
        fresh(4,3,20,20,0);List<Card> protectedCards=new ArrayList<>();for(String name:new String[]{"Grizzly Bears","Prophetic Prism","Badlands","Faithless Looting","Think Twice","Raven's Crime","Deep Analysis","Punishing Fire"}){protectedCards.add(add(name,ai,ZoneType.Graveyard));}refresh();
        check(willing(),"reanimation and reusable graveyard cards can coexist with safe payment");check(chosen().stream().allMatch(Card::isBasicLand),"protected cards excluded from payment");cast(3,4,"protect graveyard resources");check(protectedCards.stream().allMatch(c->c.isInZone(ZoneType.Graveyard)),"protected cards actually remain for recursion");
        for(String name:new String[]{"Grizzly Bears","Prophetic Prism","Badlands","Faithless Looting","Think Twice","Raven's Crime","Deep Analysis","Punishing Fire"}){fresh(4,0,20,20,0);Card c=add(name,ai,ZoneType.Graveyard);refresh();check(!SkeletalScryingAi.expendable(ai,spell,c),"protect "+name);}
        fresh(4,5,20,20,0);game.getPhaseHandler().devModeSet(PhaseType.MAIN1,ai);check(!willing(),"own first main holds");
        fresh(4,5,20,20,0);game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS,ai);check(!willing(),"combat holds");
        fresh(4,5,20,20,0);game.getPhaseHandler().devModeSet(PhaseType.MAIN2,p(1));check(!willing(),"opponent second main holds");
        int endWitness=0;for(int seat=0;seat<4;seat++){fresh(4,5,20,20,0);game.getPhaseHandler().devModeSet(PhaseType.END_OF_TURN,p(seat));boolean before=seat!=0 && game.getPhaseHandler().getNextTurn()==ai;check(willing()==before,"end-step native next-turn order seat "+seat);if(before){cast(3,4,"last opponent end step");endWitness++;}}check(endWitness>0,"positive native last-opponent end-step fixture");
        fresh(4,5,20,20,0);Card bolt=add("Lightning Bolt",p(1),ZoneType.Hand);SpellAbility threat=bolt.getFirstSpellAbility();threat.setActivatingPlayer(p(1));threat.getTargets().add(ai);threat.setHostCard(game.getAction().moveToStack(bolt,threat));game.getStack().add(threat);refresh();check(!willing(),"nonempty stack holds");
        for(String name:new String[]{"Underworld Dreams","Fate Unraveler","Notion Thief","Alms Collector","Bloodchief Ascension","Psychosis Crawler","Rest in Peace","Mana Confluence","City of Brass"}){
            fresh(4,5,20,20,0);add(name,name.equals("Mana Confluence") || name.equals("City of Brass")?ai:p(1),ZoneType.Battlefield);refresh();check(!willing() && selection()==null,"uncertain consequence holds "+name);
        }
        fresh(4,5,20,20,0);Card risk=add("Underworld Dreams",p(1),ZoneType.Graveyard);refresh();check(willing(),"inactive graveyard effect ignored");
        fresh(4,5,20,20,0);risk=add("Underworld Dreams",p(1),ZoneType.Battlefield);risk.setPhasedOut(ai);refresh();check(willing(),"phased draw damage ignored");
        fresh(4,5,20,20,0);add("Maralen of the Mornsong",p(1),ZoneType.Battlefield);refresh();check(!willing(),"cannot draw holds");
        fresh(4,5,20,20,0);add("Thalia, Guardian of Thraben",p(1),ZoneType.Battlefield);refresh();cast(2,4,"native spell tax reduces X");
        fresh(3,5,20,20,0);add("Goblin Electromancer",ai,ZoneType.Battlefield);refresh();cast(3,3,"native reduction increases affordable X");
        for(String change:new String[]{"library","life","hand","mana","grave card","grave return","incarnation","source incarnation","phase","X","profile"}){
            fresh(4,5,7,8,0);spell=selection();check(spell!=null,"selected before "+change);CardCollection old=chosen();
            switch(change){
                case "library":ai.getZone(ZoneType.Library).remove(ai.getCardsIn(ZoneType.Library).getFirst());break;
                case "life":ai.setLife(7,null);break;
                case "hand":for(int i=0;i<3;i++){add("Storm Crow",ai,ZoneType.Hand);}break;
                case "mana":ai.getLandsInPlay().getFirst().setTapped(true);break;
                case "grave card":game.getAction().exile(old.getFirst(),null,forge.game.ability.AbilityKey.newMap());break;
                case "grave return":Card gone=game.getAction().exile(old.getFirst(),null,forge.game.ability.AbilityKey.newMap());game.getAction().moveToGraveyard(gone,null);break;
                case "incarnation":old.getFirst().setGameTimestamp(game.getNextTimestamp());break;
                case "source incarnation":aura.setGameTimestamp(game.getNextTimestamp());break;
                case "phase":game.getPhaseHandler().devModeSet(PhaseType.MAIN1,ai);break;
                case "X":spell.setXManaCostPaid(2);break;
                case "profile":AiOverrideProfile.setActive("");break;
            }
            refresh();check(!ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"fresh pre-payment cancellation "+change);check(aura.isInZone(ZoneType.Hand) && game.getStack().isEmpty(),"cancellation leaves source and stack intact "+change);
        }
        for(String profile:new String[]{"","prophetic-prism","prismatic-lens"}){fresh(4,5,20,20,0);AiOverrideProfile.setActive(profile);check(!SkeletalScryingAi.uses(spell) && SkeletalScryingAi.preflight(ai,spell),profile+": fresh default ability not intercepted");check(ComputerUtilCard.isCardRemAIDeck(aura) && !AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(aura),profile+": original admission retained");}
        fresh(4,0,20,20,0);for(int i=0;i<5;i++){add("Swamp",p(1),ZoneType.Graveyard);}refresh();check(!willing(),"opponents' graveyards never fund the cost");
        fresh(4,5,20,20,0);add("Crucible of Worlds",ai,ZoneType.Battlefield);refresh();check(ai.getCardsIn(ZoneType.Graveyard).stream().allMatch(c->!c.mayPlay(ai).isEmpty()),"native graveyard land permissions present");check(!willing(),"lands reusable through native permission are protected");
        fresh(4,5,20,20,0);spell=selection();CardCollection paid=chosen();check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"paid before native counter response");
        add("Island",p(1),ZoneType.Battlefield);add("Island",p(1),ZoneType.Battlefield);Card counter=add("Counterspell",p(1),ZoneType.Hand);SpellAbility ctr=counter.getFirstSpellAbility();ctr.setActivatingPlayer(p(1));ctr.getTargets().add(spell);refresh();
        check(ComputerUtil.handlePlayingSpellAbility(p(1),ctr,null),"native legal counter payment and targeting");game.getStack().resolveStack();refresh();check(game.getStack().isEmpty() && ai.getLife()==20 && ai.getCardsIn(ZoneType.Library).size()==20,"countered spell does not draw or lose life");check(paid.stream().allMatch(c->game.getCardState(c,null).isInZone(ZoneType.Exile)) && ai.getLandsInPlay().stream().allMatch(Card::isTapped),"native counterplay does not refund paid exiles or mana");
        fresh(4,5,20,20,0);Random expected=new Random(107009);forge.util.MyRandom.setRandom(new Random(107009));check(SkeletalScryingAi.consider(ai,spell).willingToPlay(),"private native forecast accepts");check(forge.util.MyRandom.getRandom().nextLong()==expected.nextLong(),"new decision and private forecasts consume no live RNG");check(ai.getLandsInPlay().stream().noneMatch(Card::isTapped) && ai.getCardsIn(ZoneType.Graveyard).size()==5 && ai.getCardsIn(ZoneType.Library).size()==20,"forecast leaves live resources intact");
        fresh(4,5,20,20,0);add("Underworld Dreams",p(1),ZoneType.Hand);add("Notion Thief",p(1),ZoneType.Library);refresh();check(willing() && spell.getXManaCostPaid()==3,"opponent hidden identities do not affect X or admission");
        fresh(4,5,20,20,0);Card sentinel=ai.getLandsInPlay().getFirst();AiCardMemory.rememberCard(ai,sentinel,AiCardMemory.MemorySet.HELD_MANA_SOURCES_FOR_NEXT_SPELL);check(SkeletalScryingAi.consider(ai,spell).willingToPlay() && AiCardMemory.isRememberedCard(ai,sentinel,AiCardMemory.MemorySet.HELD_MANA_SOURCES_FOR_NEXT_SPELL),"private forecast preserves live reservations");
        for(String name:new String[]{"Millikin","Deranged Assistant","Chromatic Sphere"}){fresh(4,5,20,20,0);Card mm=add(name,ai,ZoneType.Battlefield);refresh();check(mm.getManaAbilities().isEmpty() && willing(),"native non-mana classification preserved "+name);}fresh(4,5,20,20,0);add("Chromatic Star",ai,ZoneType.Battlefield);refresh();check(!willing(),"resource-changing real mana ability deferred Chromatic Star");
        fresh(4,5,20,20,0);for(Card c:ai.getLandsInPlay()){game.getAction().moveToGraveyard(c,null);}for(int i=0;i<4;i++){add("Wastes",ai,ZoneType.Battlefield);}refresh();check(!willing(),"native black requirement remains mandatory");
        fresh(4,0,20,20,0);Card supplier=add("Stitcher's Supplier",ai,ZoneType.Hand);refresh();List<SpellAbility> nativeList=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();SpellAbility supplierSpell=nativeList==null?null:nativeList.stream().filter(a->a.getHostCard()==supplier).findFirst().orElse(null);
        check(supplierSpell!=null && ComputerUtil.handlePlayingSpellAbility(ai,supplierSpell,null),"normal AI casts original Supplier to fill graveyard");game.getStack().resolveStack();game.getTriggerHandler().runWaitingTriggers();game.getStack().addAllTriggeredAbilitiesToStack();while(!game.getStack().isEmpty()){game.getStack().resolveStack();}refresh();
        check(ai.getCardsIn(ZoneType.Library).size()==17 && ai.getCardsIn(ZoneType.Graveyard).size()==3,"original Supplier actually mills three cards");cast(2,3,"native self-mill supplies safe payment");
        fresh(4,5,20,20,0);Thread.currentThread().interrupt();check(!SkeletalScryingAi.consider(ai,spell).willingToPlay(),"interrupted evaluation holds");Thread.interrupted();check(willing(),"no stale global veto in next evaluation");
        AiOverrideProfile.setActive("");System.out.println("DONE "+passed+" checks");
    }
}
