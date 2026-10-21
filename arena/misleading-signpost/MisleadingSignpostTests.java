import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.ability.ApiType;
import forge.game.card.*;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import java.util.*;

/** Native flash, optional trigger, actual combat mutation and original tap-for-blue payment. */
public class MisleadingSignpostTests extends KenrithsTransformationTests {
    static Combat combat;
    static void fresh(){
        List<RegisteredPlayer> regs=new ArrayList<>();for(int i=0;i<4;i++){LobbyPlayerAi l=new LobbyPlayerAi("Signpost-"+i,null);l.setAiProfile("Default");regs.add(new RegisteredPlayer(new Deck()).setPlayer(l));}
        GameRules rules=new GameRules(GameType.Constructed);game=new Game(regs,rules,new Match(rules,regs,"Signpost fixtures"));game.setAge(GameStage.Play);ai=p(0);for(Player p:game.getPlayers()){p.setLife(20,null);for(int i=0;i<12;i++){add("Island",p,ZoneType.Library);}}p(2).setLife(10,null);p(3).setLife(5,null);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);for(int i=0;i<3;i++){add("Island",ai,ZoneType.Battlefield);}aura=add("Misleading Signpost",ai,ZoneType.Hand);spell=aura.getFirstSpellAbility();spell.setActivatingPlayer(ai);AiOverrideProfile.setActive("misleading-signpost");combat=null;refresh();
    }
    static Card attack(String name,forge.game.GameEntity defender){
        if(combat==null){game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS,p(1));combat=new Combat(p(1));game.getPhaseHandler().setCombat(combat);}Card c=add(name,p(1),ZoneType.Battlefield);combat.addAttacker(c,defender);c.setTapped(true);refresh();return c;
    }
    static SpellAbility redirect(){for(var t:aura.getTriggers()){SpellAbility ab=t.ensureAbility();if(ab!=null && ab.getApi()==ApiType.ChangeCombatants){SpellAbility copy=ab.copy(ai);copy.setActivatingPlayer(ai);return copy;}}throw new AssertionError("original entry trigger missing");}
    static boolean willing(){return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai,spell).willingToPlay();}
    static SpellAbility currentRedirect(){for(var e:game.getStack()){if(e.getSpellAbility().getApi()==ApiType.ChangeCombatants){return e.getSpellAbility();}}return null;}
    static void enter(String label){
        spell=selection();check(spell!=null,label+": native controller selects original artifact spell");check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null),label+": native flash/casting payment succeeds");check(spell.getPayingMana().size()==3 && spell.getPayingMana().stream().anyMatch(m->m.toString().equals("U")),label+": native 2U mana paid");
        game.getStack().resolveStack();game.getTriggerHandler().runWaitingTriggers();game.getStack().addAllTriggeredAbilitiesToStack();refresh();check(game.getCardState(aura,null).isInZone(ZoneType.Battlefield),label+": original artifact enters");
    }
    static void rescue(Card expected,Player destination,String label){
        enter(label);SpellAbility ab=currentRedirect();check(ab!=null && ab.getTargetCard()==expected,label+": original optional entry trigger targets preferred attacker");game.getStack().resolveStack();refresh();check(combat.getDefenderByAttacker(expected)==destination && combat.isAttacking(expected),label+": original engine redirects actual combat");check(expected.isTapped() && combat.getBandOfAttacker(expected).getAttackers().size()==1,label+": attacker remains tapped in a single native band");check(game.getStack().isEmpty(),label+": original trigger finishes");
    }
    static void mana(String label){
        Card rock=game.getCardState(aura,null);rock.setTapped(false);for(Card land:ai.getLandsInPlay()){land.setTapped(true);}Card crow=add("Storm Crow",ai,ZoneType.Hand);SpellAbility crowSpell=crow.getFirstSpellAbility();crowSpell.setActivatingPlayer(ai);add("Wastes",ai,ZoneType.Battlefield);game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);refresh();
        check(rock.getManaAbilities().size()==1 && rock.getManaAbilities().get(0).getManaPart().getOrigProduced().equals("U"),label+": original blue mana ability retained");check(ComputerUtil.handlePlayingSpellAbility(ai,crowSpell,null),label+": native colored spell uses Signpost");check(crowSpell.getPayingMana().stream().anyMatch(m->m.getSourceCard().getId()==rock.getId() && m.toString().equals("U")) && rock.isTapped(),label+": actual native blue production and tap cost");game.getStack().resolveStack();refresh();check(game.getCardState(crow,null).isInPlay(),label+": color-sensitive spell resolves");rock.setTapped(false);for(Card land:ai.getLandsInPlay()){land.setTapped(true);}Card opt=add("Opt",ai,ZoneType.Hand);SpellAbility op=opt.getFirstSpellAbility();op.setActivatingPlayer(ai);check(ComputerUtil.handlePlayingSpellAbility(ai,op,null) && rock.isTapped(),label+": native source reused after untapping");game.getStack().resolveStack();refresh();
    }
    public static void main(String[] args){
        GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);forge.util.MyRandom.setRandom(new Random(109001));
        fresh();Card c=attack("Craw Wurm",ai);check(willing(),"ordinary incoming large attacker accepted");rescue(c,p(3),"defensive flash");mana("defensive Signpost");AiOverrideProfile.setActive("");mana("already-in-play Signpost with profile off");
        fresh();Card small=attack("Hill Giant",ai);Card big=attack("Craw Wurm",ai);SpellAbility ab=redirect();check(MisleadingSignpostAi.considerRedirect(ai,ab).willingToPlay() && ab.getTargetCard()==big,"largest attacker selected");rescue(big,p(3),"largest threat");check(combat.getDefenderByAttacker(small)==ai,"other attacker unchanged");
        fresh();Card first=attack("Hill Giant",ai),second=attack("Hill Giant",ai);ab=redirect();check(MisleadingSignpostAi.considerRedirect(ai,ab).willingToPlay() && ab.getTargetCard()==first && first.getId()<second.getId(),"stable attacker ID tie break");
        fresh();c=attack("Hill Giant",ai);p(2).setLife(5,null);ab=redirect();check(MisleadingSignpostAi.considerRedirect(ai,ab).willingToPlay(),"tied defenders still eligible");check(MisleadingSignpostAi.chooseDefender(ai,ab,new ArrayList<>(combat.getDefenders()),Map.of("Attacker",c))==p(2),"stable lowest-player-ID destination tie break");
        fresh();c=attack("Hill Giant",ai);p(1).setLife(1,null);rescue(c,p(3),"never attack creature controller");
        fresh();ai.setTeam(7);p(3).setTeam(7);c=attack("Hill Giant",ai);rescue(c,p(2),"never redirect to ally");
        fresh();ai.setTeam(7);p(2).setTeam(7);p(3).setTeam(7);attack("Hill Giant",ai);check(!willing(),"no other opponent destination holds");
        fresh();c=attack("Hill Giant",ai);add("Ghostly Prison",p(3),ZoneType.Battlefield);refresh();rescue(c,p(3),"native defender reselection is not a new taxed attack declaration");
        fresh();c=attack("Craw Wurm",ai);Card hero=add("Benalish Hero",p(1),ZoneType.Battlefield);combat.addAttacker(hero,ai,combat.getBandOfAttacker(c));refresh();check(!willing(),"unfamiliar attacking band deferred");
        fresh();c=attack("Hill Giant",ai);add("Witchbane Orb",p(3),ZoneType.Battlefield);refresh();rescue(c,p(3),"defender choice ignores player hexproof");
        fresh();Card pw=add("Jace Beleren",ai,ZoneType.Battlefield);pw.setCounters(CounterEnumType.LOYALTY,3);refresh();c=attack("Craw Wurm",pw);rescue(c,p(3),"owned planeswalker rescue");
        fresh();c=attack("Grizzly Bears",ai);check(!willing() && selection()==null,"power below three holds");
        fresh();c=attack("Craw Wurm",p(2));check(!willing(),"attack on another opponent is not redirected");
        fresh();c=attack("Craw Wurm",ai);ab=redirect();check(MisleadingSignpostAi.considerRedirect(ai,ab).willingToPlay(),"target selected before defender option checks");check(MisleadingSignpostAi.chooseDefender(ai,ab,List.of(ai,p(1)),Map.of("Attacker",c))==null,"native options cannot be expanded to illegal defenders");check(MisleadingSignpostAi.chooseDefender(ai,ab,List.of(p(2)),Map.of("Attacker",c))==p(2),"restricted native options honored");
        for(String name:new String[]{"Thrun, the Last Troll","Invisible Stalker"}){fresh();c=attack(name,ai);check(!willing(),"untargetable/small attacker held "+name);}
        fresh();c=attack("Craw Wurm",ai);c.setPhasedOut(ai);refresh();check(!willing(),"phased attacker held");
        for(PhaseType phase:new PhaseType[]{PhaseType.MAIN1,PhaseType.MAIN2,PhaseType.COMBAT_BEGIN,PhaseType.COMBAT_DECLARE_BLOCKERS}){fresh();c=attack("Craw Wurm",ai);game.getPhaseHandler().devModeSet(phase,p(1));check(!willing(),"wrong defensive phase holds "+phase);}
        fresh();game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS,ai);combat=new Combat(ai);game.getPhaseHandler().setCombat(combat);check(!willing(),"own declare attackers holds");
        int fallbackCount=0;for(int seat=0;seat<4;seat++){fresh();game.getPhaseHandler().devModeSet(PhaseType.END_OF_TURN,p(seat));boolean expected=seat!=0 && game.getPhaseHandler().getNextTurn()==ai;check(willing()==expected,"native end-step order seat "+seat);if(expected){enter("fallback end step");check(currentRedirect()==null && game.getStack().isEmpty(),"fallback has no out-of-phase redirect trigger");mana("fallback Signpost");fallbackCount++;}}check(fallbackCount>0,"native positive final-opponent end-step witness");
        for(String name:new String[]{"Impact Tremors","Panharmonicon","Hushbringer","Rest in Peace","Hellrider","Coastal Piracy","Dolmen Gate"}){fresh();attack("Craw Wurm",ai);add(name,p(1),ZoneType.Battlefield);refresh();check(!willing() && selection()==null,"uncertain entry/combat consequence holds "+name);}
        fresh();attack("Craw Wurm",ai);add("Impact Tremors",p(1),ZoneType.Graveyard);refresh();check(willing(),"inactive entry effect ignored");
        fresh();attack("Craw Wurm",ai);Card risk=add("Panharmonicon",p(1),ZoneType.Battlefield);risk.setPhasedOut(p(1));refresh();check(willing(),"phased trigger doubler ignored");
        fresh();attack("Craw Wurm",ai);add("Wall of Omens",p(1),ZoneType.Battlefield);refresh();check(willing(),"unrelated self-entry trigger ignored");
        fresh();attack("Craw Wurm",ai);add("Thalia, Guardian of Thraben",p(1),ZoneType.Battlefield);refresh();check(selection()==null,"native artifact spell tax respected");add("Island",ai,ZoneType.Battlefield);refresh();check(selection()!=null,"tax paid when native funding available");
        fresh();attack("Craw Wurm",ai);ai.getLandsInPlay().getFirst().setTapped(true);check(selection()==null,"insufficient native mana holds");
        fresh();attack("Craw Wurm",ai);for(Card land:ai.getLandsInPlay()){game.getAction().moveToGraveyard(land,null);}for(int i=0;i<3;i++){add("Wastes",ai,ZoneType.Battlefield);}refresh();check(selection()==null,"native blue cost mandatory");
        for(String change:new String[]{"gone","rank","incarnation","source incarnation","destination","phase","risk","profile","stack"}){
            fresh();c=attack("Hill Giant",ai);spell=selection();check(spell!=null,"selected before "+change);
            switch(change){case "gone":combat.removeFromCombat(c);break;case "rank":attack("Craw Wurm",ai);break;case "incarnation":c.setGameTimestamp(game.getNextTimestamp());break;case "source incarnation":aura.setGameTimestamp(game.getNextTimestamp());break;case "destination":p(2).setLife(1,null);break;case "phase":game.getPhaseHandler().devModeSet(PhaseType.MAIN2,p(1));break;case "risk":add("Impact Tremors",p(1),ZoneType.Battlefield);break;case "profile":AiOverrideProfile.setActive("");break;case "stack":Card opt=add("Opt",p(2),ZoneType.Hand);SpellAbility other=opt.getFirstSpellAbility();other.setActivatingPlayer(p(2));other.setHostCard(game.getAction().moveToStack(opt,other));game.getStack().add(other);break;}
            refresh();check(!ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"fresh pre-payment cancellation "+change);check(aura.isInZone(ZoneType.Hand) && ai.getLandsInPlay().stream().noneMatch(Card::isTapped),"cancellation spends no source/mana "+change);
        }
        fresh();c=attack("Craw Wurm",ai);enter("target gone after entry");ab=currentRedirect();check(ab!=null,"original trigger present before attacker removal");combat.removeFromCombat(c);game.getAction().moveToGraveyard(c,null);game.getStack().resolveStack();refresh();check(game.getStack().isEmpty(),"disappeared attacker resolves without forced retargeting");
        fresh();c=attack("Craw Wurm",ai);enter("source removed after entry");ab=currentRedirect();check(ab!=null,"original trigger present before source removal");game.getAction().moveToGraveyard(game.getCardState(aura,null),null);game.getStack().resolveStack();refresh();check(combat.getDefenderByAttacker(c)==p(3),"native entry trigger independent of source survival");
        fresh();c=attack("Craw Wurm",ai);spell=selection();check(spell!=null && ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"Signpost paid before native counter response");add("Island",p(2),ZoneType.Battlefield);add("Island",p(2),ZoneType.Battlefield);Card cc=add("Counterspell",p(2),ZoneType.Hand);SpellAbility ctr=cc.getFirstSpellAbility();ctr.setActivatingPlayer(p(2));ctr.getTargets().add(spell);check(ComputerUtil.handlePlayingSpellAbility(p(2),ctr,null),"opponent natively counters artifact spell");game.getStack().resolveStack();refresh();check(game.getStack().isEmpty() && currentRedirect()==null && game.getCardState(aura,null).isInZone(ZoneType.Graveyard),"countered artifact never enters or redirects");check(combat.getDefenderByAttacker(c)==ai && ai.getLandsInPlay().stream().allMatch(Card::isTapped),"counterplay leaves attacker unchanged and costs spent");
        fresh();c=attack("Craw Wurm",ai);long time=game.getTimestamp(),rng=forge.util.MyRandom.callCount();check(willing() && MisleadingSignpostAi.preflight(ai,spell),"repeat decision and preflight accept");check(game.getTimestamp()==time && forge.util.MyRandom.callCount()==rng && combat.getDefenderByAttacker(c)==ai && aura.isInZone(ZoneType.Hand) && ai.getLandsInPlay().stream().noneMatch(Card::isTapped),"evaluation preserves live combat, RNG, timestamps and resources");
        for(String profile:new String[]{"","swan-song","skeletal-scrying"}){fresh();attack("Craw Wurm",ai);AiOverrideProfile.setActive(profile);check(!MisleadingSignpostAi.usesSpell(spell) && MisleadingSignpostAi.preflight(ai,spell),profile+": fresh default not intercepted");check(selection()==null && !AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(aura),profile+": original admission boundary retained");}
        fresh();attack("Craw Wurm",ai);Thread.currentThread().interrupt();check(!willing(),"interrupted decision holds");Thread.interrupted();check(willing(),"interruption leaves no persistent veto");AiOverrideProfile.setActive("");System.out.println("DONE "+passed+" checks");
    }
}
