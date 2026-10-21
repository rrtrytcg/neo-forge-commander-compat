import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.ability.ApiType;
import forge.game.card.*;
import forge.game.combat.*;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import java.util.*;

public class MirageMirrorTests extends KenrithsTransformationTests {
    static Card model;
    static Combat combat;
    static void fresh(String target,boolean hand){fresh(target,hand,false);}
    static void fresh(String target,boolean hand,boolean commander){
        List<RegisteredPlayer> regs=new ArrayList<>();for(int i=0;i<4;i++){LobbyPlayerAi l=new LobbyPlayerAi("Mirror-"+i,null);l.setAiProfile("Default");regs.add(new RegisteredPlayer(new Deck()).setPlayer(l));}
        GameRules rules=new GameRules(commander?GameType.Commander:GameType.Constructed);if(commander){rules.addAppliedVariant(GameType.Commander);}game=new Game(regs,rules,new Match(rules,regs,"Mirage Mirror fixtures"));game.setAge(GameStage.Play);ai=p(0);for(Player p:game.getPlayers()){p.setLife(40,null);for(int i=0;i<12;i++){add("Forest",p,ZoneType.Library);}}
        for(int i=0;i<6;i++){add("Wastes",ai,ZoneType.Battlefield);}aura=add("Mirage Mirror",ai,hand?ZoneType.Hand:ZoneType.Battlefield);model=add(target,p(1),ZoneType.Battlefield);spell=hand?aura.getFirstSpellAbility():aura.getSpellAbilities().stream().filter(s->s.getApi()==ApiType.Clone).findFirst().orElseThrow();spell.setActivatingPlayer(ai);game.getPhaseHandler().devModeSet(hand?PhaseType.MAIN2:PhaseType.COMBAT_BEGIN,ai);AiOverrideProfile.setActive("mirage-mirror");combat=null;if(commander){for(Player p:game.getPlayers()){p.createCommanderEffect();}}refresh();
    }
    static boolean willing(){return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai,spell).willingToPlay();}

    static Card incoming(String name,GameEntity defender){
        if(combat==null){game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS,p(1));combat=new Combat(p(1));game.getPhaseHandler().setCombat(combat);}
        Card c=add(name,p(1),ZoneType.Battlefield);combat.addAttacker(c,defender);c.setTapped(true);refresh();return c;
    }
    static Card activate(String label){
        spell=selection();check(spell!=null,label+": ordinary controller selects original activation");Card chosen=spell.getTargetCard();check(chosen!=null,label+": native legal target bound");
        check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null),label+": original native payment succeeds");check(spell.getPayingMana().size()==(game.getCardsIn(ZoneType.Battlefield).stream().anyMatch(c->c.getName().equals("Suppression Field"))?4:2) && !aura.isTapped(),label+": exact native mana cost without tap cost");
        game.getStack().resolveStack();refresh();check(aura.isCreature() && aura.isCloned() && aura.getName().equals(chosen.getName()),label+": original copy effect changes actual characteristics");
        check(aura.getSpellAbilities().stream().noneMatch(a->a.getApi()==ApiType.Clone) && aura.getId()!=chosen.getId(),label+": own identity retained and Mirror ability lost while copied");return chosen;
    }
    static void cleanup(String label){game.getEndOfTurn().executeUntil();refresh();check(aura.getName().equals("Mirage Mirror") && aura.isArtifact() && !aura.isCreature() && !aura.isCloned(),label+": native end-of-turn restores original artifact");check(aura.getSpellAbilities().stream().anyMatch(a->a.getApi()==ApiType.Clone),label+": original activation restored");}
    public static void main(String[] args){
        GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);forge.util.MyRandom.setRandom(new Random(111001));
        fresh("Craw Wurm",false);check(willing(),"ready Mirror copies a large creature for offense");

        long rng=forge.util.MyRandom.callCount(),time=game.getTimestamp();check(willing() && MirageMirrorAi.preflight(ai,spell),"repeat preview accepts selected choice");
        check(forge.util.MyRandom.callCount()==rng && game.getTimestamp()==time && !aura.isCreature() && !aura.isCloned() && ai.getLandsInPlay().stream().noneMatch(Card::isTapped),"preview preserves live RNG, timestamps, characteristics and resources");
        model.setTapped(true);activate("offensive Wurm");check(aura.getNetPower()==6 && aura.getNetToughness()==4,"native Wurm copied stats");
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS,ai);combat=new Combat(ai);game.getPhaseHandler().setCombat(combat);ai.getController().declareAttackers(ai,combat);check(combat.isAttacking(aura),"normal native attack AI uses copied creature");combat.removeFromCombat(aura);cleanup("offense");
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_BEGIN,ai);aura.setTapped(false);spell=aura.getSpellAbilities().stream().filter(a->a.getApi()==ApiType.Clone).findFirst().orElseThrow();spell.setActivatingPlayer(ai);check(!willing(),"one successful activation per native turn even after cleanup");
        Card previousSource=aura;Game previousGame=game;fresh("Craw Wurm",false);check(game!=previousGame && aura.getId()==previousSource.getId(),"fresh game reuses public card ID in lifecycle regression");check(willing(),"fresh game with colliding IDs does not inherit prior activation history");
        fresh("Colossal Dreadmaw",false);Card attacker=incoming("Hill Giant",ai);check(willing(),"defensive profitable blocker accepted");long defensiveRng=forge.util.MyRandom.callCount(),defensiveTime=game.getTimestamp();check(willing() && MirageMirrorAi.preflight(ai,spell),"repeated native defensive preview accepts");check(forge.util.MyRandom.callCount()==defensiveRng && game.getTimestamp()==defensiveTime && !aura.isCreature() && attacker.isTapped() && combat.getBlockers(attacker).isEmpty(),"defensive trade evaluation preserves live RNG, tapping and combat");activate("defensive Dreadmaw");game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS,p(1),false,game.getPhaseHandler().getTurn());ai.getController().declareBlockers(ai,combat);check(combat.isBlocking(aura,attacker),"normal native block AI uses copied creature");cleanup("defense");
        fresh("Craw Wurm",false);attacker=incoming("Craw Wurm",ai);check(willing(),"equal-value creature trade qualifies");
        fresh("Hill Giant",false);attacker=incoming("Colossal Dreadmaw",ai);attacker.addChangedCardKeywords(List.of("Shroud"),List.of(),false,game.getNextTimestamp(),null);refresh();check(!willing(),"losing block without trade holds when large attacker is untargetable");
        fresh("Hill Giant",false);attacker=incoming("Colossal Dreadmaw",p(2));check(!willing(),"attacks aimed at another opponent do not justify activation");
        fresh("Colossal Dreadmaw",false);Card pw=add("Jace Beleren",ai,ZoneType.Battlefield);pw.setCounters(CounterEnumType.LOYALTY,3);attacker=incoming("Hill Giant",pw);check(willing(),"owned planeswalker defensive line accepted");
        fresh("Air Elemental",false);attacker=incoming("Air Elemental",ai);check(willing(),"native flying copy can block incoming flyer");
        fresh("Craw Wurm",false);attacker=incoming("Air Elemental",ai);attacker.addChangedCardKeywords(List.of("Shroud"),List.of(),false,game.getNextTimestamp(),null);refresh();check(!willing(),"ground form cannot justify blocking untargetable flyer");
        fresh("Craw Wurm",false);aura.setSickness(true);check(!willing(),"summoning-sick Mirror cannot attack just by copying an old creature");
        fresh("Charging Monstrosaur",false);aura.setSickness(true);check(willing(),"native copied haste can permit same-turn attack");
        fresh("Colossal Dreadmaw",false);aura.setSickness(true);incoming("Hill Giant",ai);check(willing(),"summoning sickness does not prohibit blocking");
        for(String name:new String[]{"Grizzly Bears","Wall of Stone","Sol Ring","Forest","Glorious Anthem","Isamaru, Hound of Konda","Solemn Simulacrum","Shivan Dragon","Spined Megalodon","Clone","Tarmogoyf"}){
            fresh(name,false);check(!willing(),"bounded target filter holds "+name);
        }
        for(String name:new String[]{"Craw Wurm","Air Elemental","Colossal Dreadmaw","Serra Angel"}){fresh(name,false);check(willing(),"simple native combat form supported "+name);activate(name);cleanup(name);}
        fresh("Plated Slagwurm",false);check(!willing(),"opposing hexproof remains illegal");model.setController(ai,game.getNextTimestamp());refresh();check(willing(),"native targeting permits own hexproof creature");
        fresh("Simic Sky Swallower",false);check(!willing(),"native shroud remains illegal even on otherwise useful form");
        fresh("Craw Wurm",false);model.setTapped(true);model.setCounters(CounterEnumType.P1P1,5);model.setDamage(2);refresh();activate("copy rules");check(aura.getNetPower()==6 && aura.getNetToughness()==4 && !aura.isTapped() && aura.getDamage()==0 && aura.getCounters(CounterEnumType.P1P1)==0,"target tapped state, damage and counters are not copied");cleanup("copy rules");
        fresh("Craw Wurm",false);aura.setCounters(CounterEnumType.P1P1,2);aura.setDamage(1);refresh();activate("retained state");check(aura.getNetPower()==8 && aura.getNetToughness()==6 && aura.getDamage()==1 && aura.getCounters(CounterEnumType.P1P1)==2,"source counters and marked damage are retained");cleanup("retained state");check(aura.getCounters(CounterEnumType.P1P1)==2,"cleanup retains source counters");
        fresh("Craw Wurm",false);aura.setDamage(4);check(!willing(),"would die to retained marked damage holds");
        fresh("Craw Wurm",false);Card first=model,second=add("Craw Wurm",p(2),ZoneType.Battlefield);refresh();check(willing() && spell.getTargetCard()==first && first.getId()<second.getId(),"equal-value target tie breaks by card ID");
        fresh("Hill Giant",false);Card largest=add("Colossal Dreadmaw",p(3),ZoneType.Battlefield);refresh();check(willing() && spell.getTargetCard()==largest,"highest native-valued eligible form across all players selected");
        for(PhaseType phase:new PhaseType[]{PhaseType.MAIN1,PhaseType.MAIN2,PhaseType.END_OF_TURN,PhaseType.COMBAT_DECLARE_BLOCKERS,PhaseType.COMBAT_DAMAGE}){fresh("Craw Wurm",false);game.getPhaseHandler().devModeSet(phase,ai);check(!willing(),"activation outside own beginning-combat holds "+phase);}
        fresh("Craw Wurm",false);game.getPhaseHandler().devModeSet(PhaseType.COMBAT_BEGIN,p(1));check(!willing(),"opponent beginning combat is too early for defensive activation");
        fresh("Craw Wurm",false);for(Card land:ai.getLandsInPlay()){land.setTapped(true);}ai.getLandsInPlay().getFirst().setTapped(false);check(!willing(),"native activation requires two available mana");
        fresh("Craw Wurm",false);add("Suppression Field",p(2),ZoneType.Battlefield);for(int i=0;i<3;i++){ai.getLandsInPlay().get(i).setTapped(true);}refresh();check(!willing(),"native activation tax included in preview");ai.getLandsInPlay().getFirst().setTapped(false);check(willing(),"taxed activation fully funded");activate("taxed Mirror");
        fresh("Craw Wurm",false);Card needle=add("Pithing Needle",p(2),ZoneType.Battlefield);needle.setNamedCards(List.of("Mirage Mirror"));refresh();check(!willing(),"native activation prohibition preserved");
        for(String risk:new String[]{"Coat of Arms","Glorious Anthem","Impact Tremors","Panharmonicon","Hellrider","Dolmen Gate","Rhystic Study","Rest in Peace"}){fresh("Craw Wurm",false);add(risk,p(2),ZoneType.Battlefield);refresh();check(!willing(),"uncertain public engine holds "+risk);}
        fresh("Craw Wurm",false);add("Glorious Anthem",p(2),ZoneType.Graveyard);refresh();check(willing(),"inactive global effect ignored");
        fresh("Craw Wurm",false);add("Hellrider",p(2),ZoneType.Hand);add("Glorious Anthem",p(3),ZoneType.Library);rng=forge.util.MyRandom.callCount();check(willing() && forge.util.MyRandom.callCount()==rng,"hidden opponent identities do not affect policy or consume RNG");
        for(String change:new String[]{"target gone","target stamp","source stamp","better target","phase","mana","profile","tapped source","risk","target legality","stack"}){
            fresh("Craw Wurm",false);check(willing(),"selected before "+change);long taps=ai.getLandsInPlay().stream().filter(Card::isTapped).count();
            switch(change){case "target gone":game.getAction().moveToGraveyard(model,null);break;case "target stamp":model.setGameTimestamp(game.getNextTimestamp());break;case "source stamp":aura.setGameTimestamp(game.getNextTimestamp());break;case "better target":add("Colossal Dreadmaw",p(2),ZoneType.Battlefield);break;case "phase":game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);break;case "mana":for(Card land:ai.getLandsInPlay()){land.setTapped(true);}taps=ai.getLandsInPlay().size();break;case "profile":AiOverrideProfile.setActive("");break;case "tapped source":aura.setTapped(true);break;case "risk":add("Glorious Anthem",p(2),ZoneType.Battlefield);break;case "target legality":model.addChangedCardKeywords(List.of("Shroud"),List.of(),false,game.getNextTimestamp(),null);break;case "stack":Card opt=add("Opt",p(2),ZoneType.Hand);SpellAbility other=opt.getFirstSpellAbility();other.setActivatingPlayer(p(2));other.setHostCard(game.getAction().moveToStack(opt,other));game.getStack().add(other);break;}
            refresh();check(!ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"fresh cancellation before native payment "+change);check(ai.getLandsInPlay().stream().filter(Card::isTapped).count()==taps && !aura.isCreature(),"cancelled plan spends no mana or copy effect "+change);
        }
        fresh("Colossal Dreadmaw",false);attacker=incoming("Hill Giant",ai);check(willing(),"defensive plan bound");combat.removeFromCombat(attacker);check(!MirageMirrorAi.preflight(ai,spell),"departed incoming attacker cancels defensive plan");
        fresh("Craw Wurm",false);check(willing() && ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"original activation paid before target departure");game.getAction().moveToGraveyard(model,null);game.getStack().resolveStack();refresh();check(aura.getName().equals("Mirage Mirror") && !aura.isCreature(),"native target departure fizzles without forced retarget");
        fresh("Craw Wurm",false);check(willing() && ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"original activation paid before source departure");game.getAction().moveToGraveyard(aura,null);game.getStack().resolveStack();refresh();check(game.getCardState(aura,null).isInZone(ZoneType.Graveyard) && !game.getCardState(aura,null).isCloned(),"native departed source does not return or become a copied graveyard card");
        fresh("Craw Wurm",true);check(willing(),"useful copy target permits second-main casting");spell=selection();check(spell!=null && ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"normal AI casts original artifact with native payment");check(spell.getPayingMana().size()==3,"native three-mana artifact cost paid");game.getStack().resolveStack();refresh();aura=game.getCardState(aura,null);check(aura.isArtifact() && !aura.isCreature() && aura.getName().equals("Mirage Mirror"),"cast enters as original artifact without copying or entry benefits");
        for(String name:new String[]{"Grizzly Bears","Sol Ring","Forest","Solemn Simulacrum"}){fresh(name,true);check(!willing(),"casting needs supported useful creature "+name);}
        fresh("Craw Wurm",true);game.getPhaseHandler().devModeSet(PhaseType.MAIN1,ai);check(!willing(),"casting waits until second main");

        fresh("Craw Wurm",true);for(Card land:ai.getLandsInPlay()){land.setTapped(true);}ai.getLandsInPlay().getFirst().setTapped(false);check(!willing(),"native artifact casting affordability required");
        fresh("Craw Wurm",true);check(willing(),"cast plan chosen before source change");aura.setGameTimestamp(game.getNextTimestamp());check(!ComputerUtil.handlePlayingSpellAbility(ai,spell,null) && aura.isInZone(ZoneType.Hand) && ai.getLandsInPlay().stream().noneMatch(Card::isTapped),"stale artifact casting plan cancels before movement and costs");
        fresh("Craw Wurm",false);check(willing() && ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"first activation pending on native stack");check(!willing(),"duplicate pending activation declined");game.getStack().resolveStack();refresh();cleanup("pending guard");
        fresh("Colossal Dreadmaw",false);attacker=incoming("Hill Giant",ai);Card partner=add("Benalish Hero",p(1),ZoneType.Battlefield);combat.addAttacker(partner,ai,combat.getBandOfAttacker(attacker));refresh();check(!willing(),"unfamiliar attacking band is deferred");
        fresh("Colossal Dreadmaw",false);attacker=incoming("Hill Giant",ai);Card existing=add("Wall of Wood",ai,ZoneType.Battlefield);combat.addBlocker(attacker,existing);refresh();check(!willing(),"already blocked attacker does not justify speculative extra block");

        fresh("Craw Wurm",true);check(willing(),"cast plan selected before newly introduced casting ban");Card mage=add("Meddling Mage",p(2),ZoneType.Battlefield);mage.setNamedCards(List.of("Mirage Mirror"));refresh();check(!spell.checkRestrictions(ai) && !MirageMirrorAi.preflight(ai,spell),"fresh native casting prohibition cancels selected spell");check(!ComputerUtil.handlePlayingSpellAbility(ai,spell,null) && aura.isInZone(ZoneType.Hand) && ai.getLandsInPlay().stream().noneMatch(Card::isTapped),"casting ban cancellation preserves hand and mana");check(!willing() && selection()==null,"native casting ban also holds fresh evaluation and normal selection");

        fresh("Craw Wurm",true,true);check(willing(),"native Commander command-zone permission does not block artifact casting");
        fresh("Craw Wurm",false,true);check(willing(),"native Commander permission does not block offensive activation");activate("Commander offensive copy");cleanup("Commander offensive copy");
        fresh("Colossal Dreadmaw",false,true);attacker=incoming("Hill Giant",ai);check(willing(),"native Commander permission does not block defensive activation");activate("Commander defensive copy");cleanup("Commander defensive copy");
        fresh("Craw Wurm",false,true);Card changedPermission=ai.getCardsIn(ZoneType.Command).stream().filter(c->c.getName().equals("Commander Effect")).findFirst().orElseThrow();changedPermission.addStaticAbility("Mode$ Continuous | EffectZone$ Command | Affected$ Creature.YouCtrl | AddPower$ 1 | AddToughness$ 1");refresh();check(!willing(),"extra continuous effects are not exempted by native Commander source name");
        for(String profile:new String[]{"","arcane-lighthouse","misleading-signpost"}){fresh("Craw Wurm",false);AiOverrideProfile.setActive(profile);check(!MirageMirrorAi.usesActivation(spell) && MirageMirrorAi.preflight(ai,spell),profile+": fresh unrelated ability not intercepted");check(!willing() && !AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(aura),profile+": original clone admission retained");}
        fresh("Craw Wurm",false);Thread.currentThread().interrupt();check(!willing(),"interrupted evaluation holds");Thread.interrupted();check(willing(),"interruption does not persist");
        AiOverrideProfile.setActive("");System.out.println("DONE "+passed+" checks");
    }
}
