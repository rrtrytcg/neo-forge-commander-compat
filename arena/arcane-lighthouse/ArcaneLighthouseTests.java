import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.ability.ApiType;
import forge.game.card.*;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import java.util.*;

/** Original land effect, native joint funding and unmodified follow-up selection. */
public class ArcaneLighthouseTests extends KenrithsTransformationTests {
    static Card victim,removal;
    static SpellAbility removalSpell;
    static void fresh(String target,String answer,String... lands){
        List<RegisteredPlayer> regs=new ArrayList<>();for(int i=0;i<4;i++){LobbyPlayerAi l=new LobbyPlayerAi("Lighthouse-"+i,null);l.setAiProfile("Default");regs.add(new RegisteredPlayer(new Deck()).setPlayer(l));}
        GameRules rules=new GameRules(GameType.Constructed);game=new Game(regs,rules,new Match(rules,regs,"Lighthouse fixtures"));game.setAge(GameStage.Play);ai=p(0);for(Player p:game.getPlayers()){p.setLife(40,null);for(int i=0;i<12;i++){add("Swamp",p,ZoneType.Library);}}
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1,ai);aura=add("Arcane Lighthouse",ai,ZoneType.Battlefield);for(String land:lands){add(land,ai,ZoneType.Battlefield);}spell=aura.getSpellAbilities().stream().filter(s->s.getApi()==ApiType.AnimateAll).findFirst().orElseThrow();spell.setActivatingPlayer(ai);victim=add(target,p(1),ZoneType.Battlefield);removal=add(answer,ai,ZoneType.Hand);removalSpell=removal.getFirstSpellAbility();removalSpell.setActivatingPlayer(ai);AiOverrideProfile.setActive("arcane-lighthouse");refresh();
    }
    static boolean willing(){return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai,spell).willingToPlay();}

    static void nativeLine(String label,ZoneType dest){
        SpellAbility picked=selection();check(picked!=null,label+": normal controller selects original land activation");spell=picked;
        check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null),label+": native activation payment succeeds");
        check(aura.isTapped() && spell.getPayingMana().size()==(game.getCardsIn(ZoneType.Battlefield).stream().anyMatch(c->c.getName().equals("Suppression Field"))?3:1) && spell.getPayingMana().stream().noneMatch(m->m.getSourceCard()==aura),label+": exact native activation mana plus separate Lighthouse tap paid");
        check(!removalSpell.canTarget(victim),label+": protection persists while ability is on stack");game.getStack().resolveStack();refresh();
        check(!victim.hasKeyword(Keyword.HEXPROOF) && !victim.hasKeyword(Keyword.SHROUD) && removalSpell.canTarget(victim),label+": original temporary effect unlocks target");
        SpellAbility chosen=null;for(int i=0;i<20 && chosen==null;i++){List<SpellAbility> list=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();if(list!=null){chosen=list.stream().filter(a->a.getHostCard()==removal).findFirst().orElse(null);}}
        check(chosen!=null && chosen.getTargetCard()==victim,label+": unmodified native AI chooses follow-up removal");
        check(ComputerUtil.handlePlayingSpellAbility(ai,chosen,null),label+": native colored follow-up paid");
        check(chosen.getPayingMana().stream().noneMatch(m->m.getSourceCard().getId()==aura.getId()),label+": tapped Lighthouse not counted again");game.getStack().resolveStack();refresh();
        check(game.getCardState(victim,null).isInZone(dest),label+": original removal actually moves target to "+dest);
    }
    static void mana(String label){
        for(Card c:ai.getLandsInPlay()){c.setTapped(true);}aura.setTapped(false);add("Swamp",ai,ZoneType.Battlefield);Card rat=add("Walking Corpse",ai,ZoneType.Hand);SpellAbility s=rat.getFirstSpellAbility();s.setActivatingPlayer(ai);refresh();
        check(ComputerUtil.handlePlayingSpellAbility(ai,s,null),label+": original colorless mana funds creature");check(aura.isTapped() && s.getPayingMana().stream().anyMatch(m->m.getSourceCard().getId()==aura.getId() && m.toString().equals("C")),label+": native Lighthouse tap and colorless production");game.getStack().resolveStack();refresh();aura.setTapped(false);// reuse with a one-generic spell after explicit fixture untap
        Card stone=add("Sol Ring",ai,ZoneType.Hand);SpellAbility a=stone.getFirstSpellAbility();a.setActivatingPlayer(ai);check(ComputerUtil.handlePlayingSpellAbility(ai,a,null) && aura.isTapped(),label+": original mana reusable after untapping");game.getStack().resolveStack();refresh();
    }
    public static void main(String[] args){
        GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);forge.util.MyRandom.setRandom(new Random(110001));
        fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp","Swamp");check(willing(),"hexproof threat plus funded Murder accepted");
        
        check(!removalSpell.canTarget(victim),"original hexproof prevents targeting before activation");
        long time=game.getTimestamp(),rng=forge.util.MyRandom.callCount();
        check(willing() && ArcaneLighthouseAi.preflight(ai,spell),"repeated private forecast accepts");
        check(game.getTimestamp()==time && forge.util.MyRandom.callCount()==rng && !aura.isTapped() && ai.getLandsInPlay().stream().noneMatch(Card::isTapped) && victim.hasKeyword(Keyword.HEXPROOF) && removal.isInZone(ZoneType.Hand) && spell.getTargets().isEmpty(),"preview preserves live keywords, timestamps, RNG, targets and resources");
        nativeLine("hexproof destruction",ZoneType.Graveyard);
        for(String target:new String[]{"Simic Sky Swallower","Plated Slagwurm"}){
            fresh(target,"Despark","Swamp","Plains","Wastes");game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);check(willing(),target+": funded exile accepted");nativeLine(target+" exile",ZoneType.Exile);
        }
        fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp");check(!willing(),"Lighthouse cannot fund itself and Murder");
        fresh("Plated Slagwurm","Murder","Wastes","Wastes","Wastes","Wastes");check(!willing(),"four generic mana cannot pay double black");
        fresh("Plated Slagwurm","Despark","Swamp","Swamp","Wastes");check(!willing(),"exile requires white as well as black");
        fresh("Plated Slagwurm","Despark","Swamp","Plains");check(!willing(),"joint exile and activation funding mandatory");
        fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp","Swamp");ai.getLandsInPlay().stream().filter(c->c!=aura).findFirst().orElseThrow().setTapped(true);check(!willing(),"already tapped funding unavailable");
        for(String answer:new String[]{"Lightning Bolt","Swords to Plowshares","Diabolic Edict","Go for the Throat","Despark"}){
            fresh("Slippery Bogle",answer,"Swamp","Swamp","Swamp","Plains","Mountain");check(!willing(),"small creature/unsupported answer holds "+answer);
        }
        fresh("Craw Wurm","Murder","Swamp","Swamp","Swamp","Swamp");check(!willing(),"already targetable threat does not justify Lighthouse");
        fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp","Swamp");game.getAction().moveToGraveyard(removal,null);refresh();check(!willing(),"no removal in hand holds");
        fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp","Swamp");victim.setController(ai,game.getNextTimestamp());refresh();check(!willing(),"own creature excluded");
        fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp","Swamp");ai.setTeam(7);p(1).setTeam(7);refresh();check(!willing(),"ally creature excluded");
        fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp","Swamp");victim.setPhasedOut(p(1));refresh();check(!willing(),"phased target excluded");
        for(String risk:new String[]{"Rest in Peace","Grave Pact","Blood Artist","Impact Tremors","Rhystic Study"}){
            fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp","Swamp");add(risk,p(1),ZoneType.Battlefield);refresh();
            // Unrelated entry-only damage has no consequence in this two-action line.
            check(willing()==risk.equals("Impact Tremors"),"public uncertainty gate "+risk);
        }
        fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp","Swamp");Card inactive=add("Blood Artist",p(2),ZoneType.Graveyard);refresh();check(willing(),"inactive death trigger ignored");
        fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp","Swamp");victim.addChangedCardKeywords(List.of("Indestructible"),List.of(),false,game.getNextTimestamp(),null);refresh();check(!willing(),"indestructible target cannot justify destruction");
        removal=add("Despark",ai,ZoneType.Hand);add("Plains",ai,ZoneType.Battlefield);add("Wastes",ai,ZoneType.Battlefield);refresh();check(willing(),"indestructible threat can justify effective exile");
        fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp","Swamp");victim.incShieldCount();refresh();check(!willing(),"regeneration shield blocks destruction justification");
        fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp","Swamp");victim.setCounters(CounterEnumType.SHIELD,1);refresh();check(!willing(),"shield counter blocks destruction justification");
        fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp","Swamp");victim.addChangedCardKeywords(List.of("Protection from black"),List.of(),false,game.getNextTimestamp(),null);refresh();check(!willing(),"other targeting protection remains authoritative");
        fresh("Plated Slagwurm","Despark","Swamp","Plains","Wastes");Card tax=add("Thalia, Guardian of Thraben",p(2),ZoneType.Battlefield);refresh();check(!willing(),"native follow-up spell tax included");add("Wastes",ai,ZoneType.Battlefield);refresh();check(willing(),"native taxed exile accepted with full funding");
        for(PhaseType phase:new PhaseType[]{PhaseType.MAIN1,PhaseType.MAIN2,PhaseType.END_OF_TURN,PhaseType.COMBAT_BEGIN}){
            fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp","Swamp");game.getPhaseHandler().devModeSet(phase,ai);check(willing()==(phase==PhaseType.MAIN1||phase==PhaseType.MAIN2),"own phase boundary "+phase);
            game.getPhaseHandler().devModeSet(phase,p(1));check(!willing(),"opponent phase holds "+phase);
        }
        for(String change:new String[]{"target gone","target incarnation","source incarnation","removal gone","removal incarnation","rank","phase","risk","profile","funding","stack"}){
            fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp","Swamp");check(willing(),"plan selected before "+change);
            switch(change){
                case "target gone":game.getAction().moveToGraveyard(victim,null);break;
                case "target incarnation":victim.setGameTimestamp(game.getNextTimestamp());break;
                case "source incarnation":aura.setGameTimestamp(game.getNextTimestamp());break;
                case "removal gone":game.getAction().moveToGraveyard(removal,null);break;
                case "removal incarnation":removal.setGameTimestamp(game.getNextTimestamp());break;
                case "rank":Card ranked=add("Plated Slagwurm",p(2),ZoneType.Battlefield);ranked.setCounters(CounterEnumType.P1P1,10);break;
                case "phase":game.getPhaseHandler().devModeSet(PhaseType.END_OF_TURN,p(1));break;
                case "risk":add("Blood Artist",p(1),ZoneType.Battlefield);break;
                case "profile":AiOverrideProfile.setActive("");break;
                case "funding":ai.getLandsInPlay().stream().filter(c->c!=aura).findFirst().orElseThrow().setTapped(true);break;
                case "stack":Card c=add("Opt",p(2),ZoneType.Hand);SpellAbility other=c.getFirstSpellAbility();other.setActivatingPlayer(p(2));other.setHostCard(game.getAction().moveToStack(c,other));game.getStack().add(other);break;
            }
            refresh();long taps=ai.getLandsInPlay().stream().filter(Card::isTapped).count();check(!ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"fresh cancellation before costs "+change);check(!aura.isTapped() && ai.getLandsInPlay().stream().filter(Card::isTapped).count()==taps,"cancellation preserves mana "+change);
        }

        for(String answer:new String[]{"Lightning Bolt","Swords to Plowshares","Bone Splinters","Black Sun's Zenith","Curse of the Swine"}){
            fresh("Plated Slagwurm",answer,"Swamp","Swamp","Swamp","Plains","Mountain","Island","Wastes");check(!willing(),"bounded removal shape excludes "+answer);
        }
        fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp","Swamp");check(willing(),"first threat selected before equal-value newcomer");add("Plated Slagwurm",p(2),ZoneType.Battlefield);refresh();check(ArcaneLighthouseAi.preflight(ai,spell),"stable threat ID tie preserves original preference");
        fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp","Swamp");check(willing() && ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"activation paid before target departure");game.getAction().moveToGraveyard(victim,null);game.getStack().resolveStack();refresh();check(removal.isInZone(ZoneType.Hand) && game.getStack().isEmpty(),"target departure does not force follow-up or replacement target");
        fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp","Swamp");Thread.currentThread().interrupt();check(!willing(),"interruption holds");Thread.interrupted();check(willing(),"interruption does not poison later decision");
        fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp","Swamp");check(willing(),"plan before native activation counterplay");check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"activation paid before source departure");game.getAction().moveToGraveyard(aura,null);game.getStack().resolveStack();refresh();check(!victim.hasKeyword(Keyword.HEXPROOF),"native stacked ability survives source departure");

        fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp","Swamp");Card own=add("Plated Slagwurm",ai,ZoneType.Battlefield);Card other=add("Simic Sky Swallower",p(2),ZoneType.Battlefield);refresh();
        check(willing() && ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"multiple protected opponents natively activated");game.getStack().resolveStack();refresh();
        check(own.hasKeyword(Keyword.HEXPROOF) && !victim.hasKeyword(Keyword.HEXPROOF) && !other.hasKeyword(Keyword.SHROUD),"original effect covers opponents and leaves own creature unchanged");
        victim.addChangedCardKeywords(List.of("Hexproof"),List.of(),false,game.getNextTimestamp(),null);refresh();check(!victim.hasKeyword(Keyword.HEXPROOF),"native effect prevents protection being granted again this turn");
        Card late=add("Plated Slagwurm",p(3),ZoneType.Battlefield);refresh();check(late.hasKeyword(Keyword.HEXPROOF),"later creature not incorrectly included in resolved affected set");
        check(!willing(),"tapped Lighthouse cannot activate repeatedly");game.getEndOfTurn().executeUntil();refresh();check(victim.hasKeyword(Keyword.HEXPROOF) && other.hasKeyword(Keyword.SHROUD),"native end-of-turn cleanup restores protection");
        for(String ban:new String[]{"Pithing Needle","Suppression Field","Meddling Mage"}){
            fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp","Swamp");Card b=add(ban,p(2),ZoneType.Battlefield);b.setNamedCards(List.of(ban.equals("Pithing Needle")?"Arcane Lighthouse":"Murder"));refresh();check(!willing(),"native prohibition/activation tax respected "+ban);
            if(ban.equals("Suppression Field")){add("Swamp",ai,ZoneType.Battlefield);add("Swamp",ai,ZoneType.Battlefield);refresh();check(willing(),"native activation tax payable with six other mana");nativeLine("taxed activation",ZoneType.Graveyard);}
        }
        fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp","Swamp");victim.addChangedCardKeywords(List.of("Ward:2"),List.of(),false,game.getNextTimestamp(),null);refresh();check(!willing(),"target-triggered Ward deferred rather than underfunded");
        fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp","Swamp");
        // A real public regeneration source is enough to defer the destruction forecast.
        add("Asceticism",p(1),ZoneType.Battlefield);refresh();check(!willing(),"public regeneration engine defers destruction");
        fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp","Swamp");Card hidden=add("Blood Artist",p(2),ZoneType.Hand);add("Rest in Peace",p(3),ZoneType.Library);rng=forge.util.MyRandom.callCount();check(willing() && forge.util.MyRandom.callCount()==rng,"unrevealed opponent hand/library contents do not affect activation or consume policy RNG");
        fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp","Swamp");aura.setTapped(true);check(!willing(),"tapped source held");aura.setTapped(false);aura.setPhasedOut(ai);refresh();check(!willing(),"phased source held");
        for(String profile:new String[]{"","misleading-signpost","swan-song"}){
            fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp","Swamp");AiOverrideProfile.setActive(profile);check(!ArcaneLighthouseAi.uses(spell) && ArcaneLighthouseAi.preflight(ai,spell),profile+": fresh unrelated ability not intercepted");check(!willing() && !AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(aura),profile+": original special-ability boundary preserved");mana(profile);
        }
        fresh("Plated Slagwurm","Murder","Swamp","Swamp","Swamp","Swamp");mana("opt-in");
        AiOverrideProfile.setActive("");System.out.println("DONE "+passed+" checks");
    }
}
