import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.game.card.*;
import forge.game.phase.PhaseType;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Native original-script decision/payment/destruction/life-loss fixtures. */
public class DeadlyTempestTests extends DarksteelMutationTests {
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    static void fresh(){setup();ai.getZone(ZoneType.Hand).remove(aura);aura=add("Deadly Tempest",ai,ZoneType.Hand);spell=aura.getFirstSpellAbility();spell.setActivatingPlayer(ai);add("Swamp",ai,ZoneType.Battlefield);add("Swamp",ai,ZoneType.Battlefield);AiOverrideProfile.setActive("deadly-tempest");refresh();}
    static boolean choose(){return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai,spell).willingToPlay();}
    static void enemies(){permanent("Centaur Courser",1);permanent("Centaur Courser",2);permanent("Centaur Courser",3);}
    static void cast(String label){
        check(choose(),label+": decision accepts");
        SpellAbility selected=selection();check(selected!=null,label+": native controller selects original sorcery");spell=selected;
        check(DeadlyTempestAi.preflight(ai,spell),label+": fresh preflight");
        check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null),label+": native payment enters stack");
        check(spell.getPayingMana().size()>=6 && spell.getPayingMana().stream().filter(m->m.toString().equals("B")).count()>=2,label+": native 4BB payment");
        game.getStack().resolveStack();refresh();check(game.getCardState(aura,null).isInZone(ZoneType.Graveyard)&&game.getStack().isEmpty(),label+": original sorcery resolves normally");
    }
    static void decisions(){
        fresh();check(!choose(),"empty battlefield holds");
        fresh();permanent("Centaur Courser",3);check(!choose(),"one opposing death holds");
        fresh();permanent("Centaur Courser",2);permanent("Centaur Courser",3);check(choose(),"two opposing deaths suffice even with first opponent empty");
        fresh();enemies();permanent("Centaur Courser",0);check(choose(),"three opposing vs one friendly death qualifies");
        permanent("Grizzly Bears",0);check(!choose(),"net one death advantage holds");
        fresh();permanent("Gigantosaurus",0);for(int i=0;i<3;i++)permanent("Ornithopter",1);check(!choose(),"count advantage cannot override worse native creature value");
        fresh();enemies();ai.setLife(5,null);check(choose(),"exact five life with no own destruction qualifies");
        permanent("Grizzly Bears",0);check(!choose(),"five life before own death violates reserve");ai.setLife(6,null);check(choose(),"six life with one own death meets reserve exactly");
        ai.setLife(1,null);check(!choose(),"self lethal never authorized");
        fresh();enemies();ai.setLife(4,null);check(!choose(),"already below reserve holds even without own losses");
        fresh();ai.setTeam(0);p(1).setTeam(0);permanent("Grizzly Bears",1);for(int i=0;i<3;i++)permanent("Centaur Courser",2);p(1).setLife(5,null);check(!choose(),"allied life reserve protected");p(1).setLife(6,null);check(choose(),"allied casualties count friendly at safe reserve");
        fresh();enemies();Card shield=permanent("Grizzly Bears",0);shield.setCounters(CounterEnumType.SHIELD,1);refresh();ai.setLife(5,null);check(choose(),"native shield survivor does not count as destroyed or life lost");
        fresh();enemies();Card ind=permanent("Darksteel Myr",0);ai.setLife(5,null);check(choose(),"indestructible survivor does not cost life");
        fresh();for(int i=0;i<3;i++)permanent("Darksteel Myr",1);check(!choose(),"indestructible opposing board supplies no destruction advantage");
        fresh();enemies();game.getPhaseHandler().devModeSet(PhaseType.MAIN1,ai);check(!choose(),"first main holds");game.getPhaseHandler().devModeSet(PhaseType.MAIN2,p(1));check(!choose(),"opposing second main holds");
        fresh();enemies();ai.getLandsInPlay().stream().filter(c->c.getName().equals("Swamp")).forEach(c->c.setTapped(true));check(!choose(),"missing black cannot be paid by spare generic mana");
        fresh();enemies();permanent("Sphere of Resistance",1);check(!choose(),"unfunded native tax holds");add("Swamp",ai,ZoneType.Battlefield);check(choose(),"funded native spell tax qualifies");
    }
    static void boundaries(){
        fresh();enemies();check(!DeadlyTempestAi.preflight(ai,spell),"unselected original spell cannot bypass a decision");
        fresh();enemies();var busy=add("Opt",p(1),ZoneType.Hand).getFirstSpellAbility();busy.setActivatingPlayer(p(1));game.getStack().add(busy);check(!choose(),"nonempty stack holds");
        fresh();enemies();Card c=p(1).getCreaturesInPlay().get(0);c.setCounters(CounterEnumType.SHIELD,1);refresh();check(choose(),"two real opposing deaths still qualify beside shield survivor");p(2).getCreaturesInPlay().get(0).setCounters(CounterEnumType.SHIELD,1);refresh();check(!choose(),"shielded opposing creatures cannot inflate death advantage");
        fresh();enemies();for(Card land:new ArrayList<>(ai.getCardsIn(ZoneType.Library)))ai.getZone(ZoneType.Library).remove(land);check(choose(),"no invented draw or library reserve for a spell that draws nothing");
        fresh();enemies();var raise=add("Raise the Alarm",p(1),ZoneType.Hand).getFirstSpellAbility();raise.setActivatingPlayer(p(1));forge.game.ability.AbilityUtils.resolve(raise);refresh();check(choose(),"native tokens are valid destruction casualties");cast("native tokens");check(p(1).getLife()==17&&p(2).getLife()==19,"destroyed native tokens contribute exactly one life each");
        fresh();enemies();Card stolen=permanent("Grizzly Bears",1);stolen.setController(ai,game.getNextTimestamp());refresh();cast("last-known controller");check(ai.getLife()==19&&p(1).getLife()==19,"destroyed borrowed creature charges controller, not owner");
        fresh();enemies();permanent("Lodestone Golem",1);add("Swamp",ai,ZoneType.Battlefield);check(choose(),"dying cost modifier is natively paid before destruction");cast("dying cost modifier");check(spell.getPayingMana().size()==7&&p(1).getLife()==18,"cost paid at cast and loss uses destroyed controller count");
    }
    static void uncertainty(){
        for(String name:List.of("Blood Artist","Zulaport Cutthroat","Sangromancer","Grave Pact","Dictate of Erebos","Sanguine Bond","Wound Reflection","Platinum Emperion","Rest in Peace","Humility","Platinum Angel","Drudge Skeletons","Welding Jar","Gaea's Anthem")){
            fresh();enemies();Card c=permanent(name,1);
            if(name.equals("Gaea's Anthem")){check(choose(),"fixed surviving noncreature anthem supported");}
            else check(!choose(),"uncertain engine held: "+name);
        }
        fresh();enemies();Card lord=permanent("Elvish Champion",1);check(!choose(),"dying continuous source deferred");
        fresh();enemies();Card body=permanent("Grizzly Bears",0);Card aura=permanent("Holy Strength",0);aura.attachToEntity(body,null);refresh();check(!choose(),"attached Aura collateral deferred");
        fresh();enemies();spell.getSubAbility().getAdditionalAbility("RepeatSubAbility").putParam("Defined","You");check(!choose(),"altered nested life-loss chain never falls through to generic AI");
    }
    interface Change{void apply();}
    static void stale(String label,Change change){fresh();enemies();check(choose(),label+": initial plan");change.apply();long tapped=ai.getLandsInPlay().stream().filter(Card::isTapped).count();int hand=ai.getCardsIn(ZoneType.Hand).size();check(!ComputerUtil.handlePlayingSpellAbility(ai,spell,null),label+": canceled before costs");check(ai.getLandsInPlay().stream().filter(Card::isTapped).count()==tapped&&ai.getCardsIn(ZoneType.Hand).size()==hand&&game.getStack().isEmpty(),label+": live resources untouched");}
    static void lifecycle(){
        stale("new own army",()->{for(int i=0;i<3;i++)permanent("Centaur Courser",0);});
        stale("life reserve lost",()->ai.setLife(4,null));
        stale("opponents leave",()->{for(Card c:new ArrayList<>(game.getCardsIn(ZoneType.Battlefield)))if(c.isCreature())game.getAction().moveToHand(c,null);});
        stale("new death engine",()->permanent("Blood Artist",2));
        stale("mana tapped",()->ai.getLandsInPlay().forEach(c->c.setTapped(true)));
        stale("source timestamp changed",()->aura.setGameTimestamp(game.getNextTimestamp()));
        stale("source leaves hand",()->game.getAction().moveToGraveyard(aura,null));
        stale("profile removed",()->AiOverrideProfile.setActive("default"));
        stale("nested loss manipulated",()->spell.getSubAbility().getAdditionalAbility("RepeatSubAbility").putParam("LifeAmount","20"));
        stale("timing changed",()->game.getPhaseHandler().devModeSet(PhaseType.MAIN1,ai));
        stale("fresh Silence",()->{var s=add("Silence",p(1),ZoneType.Hand).getFirstSpellAbility();s.setActivatingPlayer(p(1));forge.game.ability.AbilityUtils.resolve(s);refresh();});
        fresh();enemies();check(choose(),"interruption plan selected");Thread.currentThread().interrupt();check(!DeadlyTempestAi.preflight(ai,spell),"interruption cannot authorize cast");Thread.interrupted();check(DeadlyTempestAi.preflight(ai,spell),"cleared interruption recovers fresh plan");
        fresh();enemies();SpellAbility other=spell;check(choose(),"old game plan selected");fresh();check(!DeadlyTempestAi.preflight(ai,other),"previous game plan cannot authorize new game");
    }
    static void consequences(){
        fresh();enemies();permanent("Grizzly Bears",0);cast("four-player destruction");check(ai.getLife()==19&&p(1).getLife()==19&&p(2).getLife()==19&&p(3).getLife()==19,"original loss attributed to every controller");check(game.getCardsIn(ZoneType.Battlefield).stream().noneMatch(Card::isCreature),"actual four creature destruction");check(!aura.getRemembered().iterator().hasNext(),"original cleanup clears remembered LKI");
        fresh();enemies();Card shield=permanent("Grizzly Bears",0);shield.setCounters(CounterEnumType.SHIELD,1);refresh();cast("native shield counter");check(shield.isInPlay()&&shield.getCounters(CounterEnumType.SHIELD)==0&&ai.getLife()==20,"shield consumed with zero destruction life loss");
        fresh();enemies();Card ind=permanent("Darksteel Myr",0);cast("native indestructible");check(ind.isInPlay()&&ai.getLife()==20,"indestructible survivor does not contribute to original remembered loss");
        fresh();enemies();permanent("Sphere of Resistance",1);add("Swamp",ai,ZoneType.Battlefield);cast("native spell tax");check(spell.getPayingMana().size()==7,"exact native generic tax retained");
        fresh();enemies();check(choose(),"response fixture selected");check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"response fixture paid");Card target=p(1).getCreaturesInPlay().get(0);var regen=add("Regenerate",p(1),ZoneType.Hand).getFirstSpellAbility();regen.setActivatingPlayer(p(1));regen.getTargets().add(target);forge.game.ability.AbilityUtils.resolve(regen);refresh();game.getStack().resolveStack();refresh();check(target.isInPlay()&&target.isTapped()&&p(1).getLife()==20&&p(2).getLife()==19,"real post-cast regeneration allowed and not counted as destruction");
        fresh();enemies();check(choose(),"new indestructibility response selected");check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"new indestructibility response paid");target=p(1).getCreaturesInPlay().get(0);target.addIntrinsicKeyword("Indestructible");refresh();game.getStack().resolveStack();refresh();check(target.isInPlay()&&p(1).getLife()==20&&p(2).getLife()==19,"post-cast native response changes actual losses");
    }
    static class CountRandom extends Random{int calls;@Override protected int next(int bits){calls++;return super.next(bits);}}
    public static void main(String[] args){
        GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);
        fresh();check(DeadlyTempestAi.uses(spell),"recognizes original native chain");decisions();boundaries();uncertainty();lifecycle();consequences();
        fresh();enemies();add("Blood Artist",p(1),ZoneType.Hand);CountRandom rng=new CountRandom();MyRandom.setRandom(rng);long stamp=game.getTimestamp();check(choose()&&DeadlyTempestAi.preflight(ai,spell),"hidden opposing hand does not affect public forecast");check(rng.calls==0,"forecast consumes no evaluation or native reservation RNG");check(game.getTimestamp()==stamp&&ai.getManaPool().isEmpty()&&ai.getLandsInPlay().stream().noneMatch(Card::isTapped)&&aura.isInZone(ZoneType.Hand),"forecast preserves live resources, timestamp and zones");
        AiOverrideProfile.setActive("default");check(!DeadlyTempestAi.uses(spell)&&ComputerUtilCard.isCardRemAIDeck(aura),"default AI retains original block");AiOverrideProfile.setActive("hull-breach");check(!DeadlyTempestAi.uses(spell)&&ComputerUtilCard.isCardRemAIDeck(aura),"unrelated profile does not unlock Tempest");check(aura.getRules().getAiHints().getRemAIDecks(),"original script AI warning retained");
        System.out.println("DONE "+checks+" Deadly Tempest checks");
    }
}
