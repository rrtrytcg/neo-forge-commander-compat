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

/** Real original Charm chain, costs and destruction; forecasts never replace native resolution. */
public class HullBreachTests extends DarksteelMutationTests {
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    static void fresh(){
        setup();ai.getZone(ZoneType.Hand).remove(aura);
        aura=add("Hull Breach",ai,ZoneType.Hand);spell=aura.getFirstSpellAbility();spell.setActivatingPlayer(ai);
        add("Mountain",ai,ZoneType.Battlefield);add("Forest",ai,ZoneType.Battlefield);
        AiOverrideProfile.setActive("hull-breach");refresh();
    }
    static boolean choose(){return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai,spell).willingToPlay();}
    static int mode(){return spell.getAdditionalAbilityList("Choices").indexOf(spell.getChosenList().get(0));}
    static List<Card> targets(){List<Card> r=new ArrayList<>();for(SpellAbility s=spell.getSubAbility();s!=null;s=s.getSubAbility()){r.addAll(s.getTargets().getTargetCards());}return r;}
    static void selected(int mode,Card... cards){check(choose(),"normal Charm decision accepts supported position");check(mode()==mode,"correct native Charm mode "+mode);check(targets().equals(List.of(cards)),"exact ordered distinct opposing targets");check(HullBreachAi.preflight(ai,spell),"fresh plan passes before payment");}
    static void nativeCast(String label,int mode,Card... cards){
        selected(mode,cards);SpellAbility selected=selection();check(selected!=null,label+": native controller selects Hull Breach");spell=selected;
        check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null),label+": native payment places original spell on stack");
        check(spell.getPayingMana().size()==2 && spell.getPayingMana().stream().anyMatch(m->m.toString().equals("R")) && spell.getPayingMana().stream().anyMatch(m->m.toString().equals("G")),label+": exact red and green native payment");
        check(targets().equals(List.of(cards)),label+": native mode announcement preserves chosen targets");
        game.getStack().resolveStack();refresh();
        for(Card c:cards)check(game.getCardState(c,null).isInZone(ZoneType.Graveyard),label+": actual target destroyed by original script");
        check(game.getCardState(aura,null).isInZone(ZoneType.Graveyard) && game.getStack().isEmpty(),label+": original sorcery finishes normally");
    }
    static void choices(){
        fresh();check(!choose(),"empty board holds");
        permanent("Manalith",0);permanent("Fervor",0);check(!choose(),"never destroys own permanents");
        fresh();ai.setTeam(0);p(1).setTeam(0);permanent("Manalith",1);permanent("Fervor",1);check(!choose(),"never fills slots with allied permanents");
        fresh();Card a=permanent("Manalith",1);selected(0,a);
        fresh();Card e=permanent("Fervor",2);selected(1,e);
        fresh();a=permanent("Manalith",1);e=permanent("Fervor",2);selected(2,a,e);
        fresh();a=permanent("Manalith",1);permanent("Fervor",0);selected(0,a);
        fresh();permanent("Manalith",0);e=permanent("Fervor",2);selected(1,e);
        fresh();Card dual=permanent("Bident of Thassa",1);selected(0,dual);check(targets().size()==1,"artifact enchantment is counted once");
        fresh();dual=permanent("Bident of Thassa",1);a=permanent("Manalith",2);selected(2,a,dual);
        fresh();a=permanent("Manalith",1);Card high=permanent("Gilded Lotus",2);e=permanent("Fervor",1);selected(2,high,e);check(!targets().contains(a),"native removal priority prefers valuable artifact");
        fresh();a=permanent("Manalith",1);Card tie=permanent("Manalith",1);selected(0,a);check(a.getId()<tie.getId(),"equal native values break on stable card ID");
        fresh();a=permanent("Universal Automaton",1);permanent("Standard Bearer",1);permanent("Gilded Lotus",1);refresh();check(!choose(),"native mandatory target rule vetoes otherwise preferred illegal choice");
        fresh();a=permanent("Universal Automaton",1);permanent("Standard Bearer",1);refresh();selected(0,a);
        fresh();a=permanent("Manalith",1);a.addIntrinsicKeyword("Indestructible");e=permanent("Fervor",2);selected(1,e);
        fresh();a=permanent("Manalith",1);a.addIntrinsicKeyword("Hexproof");refresh();check(!choose(),"native hexproof targeting holds");
        fresh();a=permanent("Manalith",1);a.addIntrinsicKeyword("Shroud");refresh();check(!choose(),"native shroud targeting holds");
        fresh();a=permanent("Manalith",1);a.addIntrinsicKeyword("Ward:2");refresh();check(!choose(),"defer ward rather than predict its triggered payment");
        fresh();a=permanent("Manalith",1);a.setCounters(CounterEnumType.SHIELD,1);refresh();check(!choose(),"shield counter prevents wasted removal");
        fresh();a=permanent("Manalith",1);a.incShieldCount();check(!choose(),"native regeneration shield holds");
        fresh();permanent("Welding Jar",1);check(!choose(),"regeneration engines defer");
        fresh();permanent("Manalith",1);permanent("Grave Pact",1);check(!choose(),"uncertain battlefield-leave triggers defer");
        fresh();permanent("Manalith",1);permanent("Rest in Peace",1);check(!choose(),"active movement replacement defers");
        fresh();permanent("Manalith",1);permanent("Karmic Justice",1);check(!choose(),"punishing destruction trigger defers");
        fresh();permanent("Manalith",1);permanent("Maro",0);check(!choose(),"defer variable stats that can change when casting leaves hand or spends mana");
        fresh();permanent("Manalith",1);permanent("Manabarbs",1);check(!choose(),"defer mana-tap damage triggers omitted from affordability preview");
        fresh();permanent("Mirror Gallery",1);check(!choose(),"defer legend-rule engines rather than omit native legend choices");
        fresh();permanent("Humility",1);check(!choose(),"defer ability suppression that can reveal variable characteristics");
        fresh();permanent("Manalith",1);permanent("Volrath's Shapeshifter",0);check(!choose(),"defer unmodeled continuous text and characteristic changes");
        fresh();permanent("Platinum Angel",1);check(!choose(),"defer player-loss prevention state-based consequences");
        fresh();Card friendly=permanent("Walking Corpse",0);permanent("Bad Moon",1);friendly.setDamage(2);refresh();check(!choose(),"native preview refuses friendly collateral death after shared anthem removal");
        fresh();a=permanent("Myr Enforcer",1);Card pacifism=permanent("Pacifism",0);pacifism.attachToEntity(a,null);refresh();check(!choose(),"defer destroying an opponent's creature with a friendly Aura attached");
        fresh();permanent("Manalith",1);game.getPhaseHandler().devModeSet(PhaseType.UPKEEP,ai);check(!choose(),"normal sorcery timing preserved");
        fresh();permanent("Manalith",1);game.getPhaseHandler().devModeSet(PhaseType.MAIN2,p(1));check(!choose(),"opponent turn holds");
        fresh();a=permanent("Manalith",1);ai.getLandsInPlay().stream().filter(c->c.getName().equals("Forest")).forEach(c->c.setTapped(true));check(!choose(),"generic mana cannot substitute missing green");
        fresh();a=permanent("Manalith",1);permanent("Sphere of Resistance",2);selected(0,a);check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"native tax payment succeeds with spare basic land");check(spell.getPayingMana().size()==3,"native additional spell tax paid");
    }
    interface Change{void apply(Card a,Card e);}
    static void stale(String label,Change change){fresh();Card a=permanent("Manalith",1),e=permanent("Fervor",2);selected(2,a,e);change.apply(a,e);long tapped=ai.getLandsInPlay().stream().filter(Card::isTapped).count();int hand=ai.getCardsIn(ZoneType.Hand).size();check(!ComputerUtil.handlePlayingSpellAbility(ai,spell,null),label+": cancel before native payment");check(ai.getLandsInPlay().stream().filter(Card::isTapped).count()==tapped && ai.getCardsIn(ZoneType.Hand).size()==hand && game.getStack().isEmpty(),label+": no source, mana or stack mutation");}
    static void lifecycle(){
        stale("target departed",(a,e)->game.getAction().moveToGraveyard(a,null));
        stale("artifact reincarnation",(a,e)->a.setGameTimestamp(game.getNextTimestamp()));
        stale("enchantment reincarnation",(a,e)->e.setGameTimestamp(game.getNextTimestamp()));
        stale("source reincarnation",(a,e)->aura.setGameTimestamp(game.getNextTimestamp()));
        stale("new best target",(a,e)->permanent("Gilded Lotus",1));
        stale("lost colored mana",(a,e)->ai.getLandsInPlay().stream().filter(c->c.getName().equals("Forest")).forEach(c->c.setTapped(true)));
        stale("fresh protection",(a,e)->{a.addIntrinsicKeyword("Indestructible");refresh();});
        stale("profile removed",(a,e)->AiOverrideProfile.setActive("default"));
        stale("target manipulation",(a,e)->{spell.getSubAbility().resetTargets();spell.getSubAbility().getTargets().add(permanent("Manalith",0));});
        stale("raw chosen-mode manipulation",(a,e)->{var m=spell.getChosenList().get(0);m.resetTargets();m.getTargets().add(permanent("Manalith",0));});
        stale("chain manipulation",(a,e)->spell.getSubAbility().getSubAbility().getSubAbility().putParam("Defined","YouCtrl"));
        stale("new spell prohibition",(a,e)->{var silence=add("Silence",p(1),ZoneType.Hand).getFirstSpellAbility();silence.setActivatingPlayer(p(1));forge.game.ability.AbilityUtils.resolve(silence);refresh();});
        stale("source left hand",(a,e)->game.getAction().moveToGraveyard(aura,null));
        stale("newly protected enchantment",(a,e)->{e.addIntrinsicKeyword("Shroud");refresh();});
        stale("turn changed",(a,e)->game.getPhaseHandler().devModeSet(PhaseType.MAIN2,p(1)));
        for(int leave=0;leave<2;leave++){
            fresh();Card a=permanent("Manalith",1),e=permanent("Fervor",2);selected(2,a,e);check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"paid combined mode before response");
            Card removed=leave==0?a:e,remaining=leave==0?e:a;game.getAction().moveToHand(removed,null);game.getStack().resolveStack();refresh();
            check(game.getCardState(removed,null).isInZone(ZoneType.Hand),"departed target is never destroyed in its new zone");check(game.getCardState(remaining,null).isInZone(ZoneType.Graveyard),"native partial legality destroys remaining target");
        }
        fresh();Card a=permanent("Manalith",1),e=permanent("Fervor",2);selected(2,a,e);check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"original spell pays before shield response");a.addIntrinsicKeyword("Indestructible");refresh();game.getStack().resolveStack();refresh();check(a.isInPlay() && game.getCardState(e,null).isInZone(ZoneType.Graveyard),"post-cast indestructibility and other destruction remain native");
        fresh();a=permanent("Manalith",1);e=permanent("Fervor",2);selected(2,a,e);check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"both-target response fixture pays normally");game.getAction().moveToHand(a,null);game.getAction().moveToHand(e,null);game.getStack().resolveStack();refresh();check(game.getCardState(a,null).isInZone(ZoneType.Hand) && game.getCardState(e,null).isInZone(ZoneType.Hand) && game.getCardState(aura,null).isInZone(ZoneType.Graveyard),"all targets invalid: native fizzle leaves both new zones intact");
        fresh();a=permanent("Manalith",1);selected(0,a);Thread.currentThread().interrupt();check(!HullBreachAi.preflight(ai,spell),"interruption never authorizes a pending cast");Thread.interrupted();check(HullBreachAi.preflight(ai,spell),"cleared interruption recovers a still-fresh plan");
    }
    static class CountRandom extends Random {int calls;@Override protected int next(int bits){calls++;return super.next(bits);}}
    public static void main(String[] args){
        GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);
        fresh();check(HullBreachAi.uses(spell),"opt-in recognizes original native Charm structure");choices();lifecycle();
        fresh();Card a=permanent("Manalith",1);nativeCast("artifact only",0,a);
        fresh();Card e=permanent("Fervor",2);nativeCast("enchantment only",1,e);
        fresh();a=permanent("Manalith",1);e=permanent("Fervor",2);nativeCast("combined",2,a,e);
        fresh();a=permanent("Manalith",1);CountRandom rng=new CountRandom();MyRandom.setRandom(rng);check(choose() && HullBreachAi.preflight(ai,spell),"repeated pure decision succeeds");check(rng.calls==0,"evaluation consumes no policy/native reservation RNG");check(!a.isTapped() && a.isInPlay() && aura.isInZone(ZoneType.Hand) && ai.getManaPool().isEmpty(),"private forecast leaves live resources and zones untouched");
        AiOverrideProfile.setActive("default");check(ComputerUtilCard.isCardRemAIDeck(aura),"default AI still refuses original flagged card");check(!HullBreachAi.uses(spell),"default profile never enters new helper");AiOverrideProfile.setActive("mirage-mirror");check(!HullBreachAi.uses(spell) && ComputerUtilCard.isCardRemAIDeck(aura),"unrelated profile does not unlock Hull Breach");
        check(aura.getRules().getAiHints().getRemAIDecks(),"original AI warning retained");
        System.out.println("DONE "+checks+" Hull Breach checks");
    }
}
