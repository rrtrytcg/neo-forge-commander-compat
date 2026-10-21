import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.*;
import forge.game.combat.Combat;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import java.util.*;

/** Original permission response, paid counter and opposing Bird through the native engine. */
public class SwanSongTests extends KenrithsTransformationTests {
    static SpellAbility threat;
    static void fresh(){
        List<RegisteredPlayer> regs=new ArrayList<>();for(int i=0;i<4;i++){LobbyPlayerAi l=new LobbyPlayerAi("Song-"+i,null);l.setAiProfile("Default");regs.add(new RegisteredPlayer(new Deck()).setPlayer(l));}
        GameRules rules=new GameRules(GameType.Constructed);game=new Game(regs,rules,new Match(rules,regs,"Swan Song fixtures"));game.setAge(GameStage.Play);ai=p(0);for(Player p:game.getPlayers()){p.setLife(20,null);for(int i=0;i<12;i++){add("Island",p,ZoneType.Library);}}
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2,p(1));add("Island",ai,ZoneType.Battlefield);aura=add("Swan Song",ai,ZoneType.Hand);spell=aura.getFirstSpellAbility();spell.setActivatingPlayer(ai);AiOverrideProfile.setActive("swan-song");threat=null;refresh();
    }
    static SpellAbility push(String name,int seat,forge.game.GameObject target,int x){Card c=add(name,p(seat),ZoneType.Hand);SpellAbility s=c.getFirstSpellAbility();s.setActivatingPlayer(p(seat));if(target!=null){s.getTargets().add(target);}if(s.getPayCosts().getTotalMana().countX()>0){s.setXManaCostPaid(x);}s.setHostCard(game.getAction().moveToStack(c,s));s.getHostCard().setCastSA(s);game.getStack().add(s);refresh();threat=s;return s;}
    static boolean willing(){return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai,spell).willingToPlay();}
    static long birds(Player p){return p.getCreaturesInPlay().stream().filter(c->c.isToken() && c.getType().hasSubtype("Bird")).count();}
    static void cast(String label){
        SpellAbility expected=threat;Player recipient=expected.getActivatingPlayer();int hand=ai.getCardsIn(ZoneType.Hand).size();int library=recipient.getCardsIn(ZoneType.Library).size();long before=birds(recipient);
        spell=selection();check(spell!=null,label+": native counter response selects Song");check(spell.getTargets().getFirstTargetedSpell()==expected,label+": exact top spell selected");check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null),label+": original native payment enters stack");check(spell.getPayingMana().size()==1 && spell.getPayingMana().get(0).toString().equals("U"),label+": original blue cost paid");check(ai.getCardsIn(ZoneType.Hand).size()==hand-1,label+": real card spent");
        game.getStack().resolveStack();refresh();check(game.getCardState(expected.getHostCard(),null).isInZone(ZoneType.Graveyard),label+": original counter removes target to graveyard");check(birds(recipient)==before+1 && birds(ai)==0,label+": target controller receives exactly one original Bird");Card bird=recipient.getCreaturesInPlay().stream().filter(c->c.isToken() && c.getType().hasSubtype("Bird")).findFirst().orElseThrow();check(bird.getNetPower()==2 && bird.getNetToughness()==2 && bird.hasKeyword(Keyword.FLYING) && !bird.hasKeyword(Keyword.HASTE),label+": actual original ordinary 2/2 flyer");check(game.getCardState(aura,null).isInZone(ZoneType.Graveyard) && game.getStack().isEmpty(),label+": original Song finishes normally");check(recipient.getCardsIn(ZoneType.Library).size()==library,label+": countered draw never resolves");
    }
    public static void main(String[] args){
        GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);forge.util.MyRandom.setRandom(new Random(108001));
        fresh();push("Tidings",1,null,0);check(willing(),"high mana-value draw accepted");cast("Tidings");
        for(String name:new String[]{"Divination","Opt","Cancel"}){fresh();push(name,1,null,0);check(!willing(),"cheap nonemergency holds "+name);}
        fresh();check(!willing(),"empty stack holds");fresh();push("Air Elemental",1,null,0);check(!willing(),"creature spell cannot be targeted");
        fresh();push("Tidings",0,null,0);check(!willing(),"own spell held");
        fresh();push("Inescapable Blaze",1,ai,0);check(!willing(),"native uncounterable spell held");
        fresh();push("Levitation",1,null,0);check(willing(),"opposing enchantment spell accepted before its enhancement becomes active");cast("Levitation");
        fresh();push("Tidings",1,null,0);push("Opt",2,null,0);check(!willing(),"never reach through a cheap top spell to valuable lower spell");
        fresh();ai.setLife(3,null);push("Opt",2,null,0);push("Lightning Bolt",1,ai,0);check(!willing(),"cheap emergency defers ambiguous multi-spell stack");
        for(int seat=1;seat<4;seat++){fresh();push("Concentrate",seat,null,0);check(willing(),"mana-value-four boundary opponent "+seat);cast("opponent "+seat);}
        for(int x=0;x<=4;x++){fresh();push("Fireball",1,p(2),x);check(threat.getHostCard().getCMC()==x+1,"native stack mana value includes exact X "+x);check(willing()==(x>=3),"X threshold "+x);}
        fresh();ai.setLife(3,null);push("Lightning Bolt",1,ai,0);check(willing(),"cheap known lethal damage accepted");cast("lethal Bolt");check(ai.getLife()==3,"lethal counter actually preserves life");
        fresh();ai.setLife(4,null);push("Lightning Bolt",1,ai,0);check(!willing(),"nonlethal cheap damage held");
        fresh();p(2).setLife(3,null);push("Lightning Bolt",1,p(2),0);check(!willing(),"other opponent lethal damage not rescued");
        fresh();ai.setLife(2,null);push("Shock",1,ai,0);check(willing(),"exact lethal two damage accepted");
        fresh();ai.setLife(3,null);add("Platinum Angel",ai,ZoneType.Battlefield);refresh();push("Lightning Bolt",1,ai,0);check(!willing(),"native cannot-lose boundary is not falsely lethal");
        fresh();Card c=add("Air Elemental",ai,ZoneType.Battlefield);push("Doom Blade",1,c,0);check(willing(),"cheap removal of useful expensive creature accepted");cast("rescue Elemental");check(c.isInPlay(),"rescued creature survives native counter");
        fresh();c=add("Isamaru, Hound of Konda",ai,ZoneType.Battlefield);c.setCommander(true);push("Doom Blade",1,c,0);check(willing(),"cheap commander protected regardless of mana value");
        fresh();c=add("Storm Crow",ai,ZoneType.Battlefield);push("Doom Blade",1,c,0);check(!willing(),"ordinary cheap creature not worth Bird trade");
        fresh();c=add("Darksteel Myr",ai,ZoneType.Battlefield);push("Doom Blade",1,c,0);check(!willing(),"ineffective destruction of indestructible not rescued");
        fresh();c=add("Air Elemental",p(2),ZoneType.Battlefield);push("Doom Blade",1,c,0);check(!willing(),"another opponent's creature not rescued");
        fresh();c=add("Air Elemental",ai,ZoneType.Battlefield);push("Despark",1,c,0);check(willing(),"cheap exile of useful expensive creature accepted");cast("rescue from Despark");check(c.isInPlay(),"exile target survives original counter");
        fresh();c=add("Air Elemental",ai,ZoneType.Battlefield);c.setGamePieceType(forge.card.GamePieceType.TOKEN);push("Doom Blade",1,c,0);check(!willing(),"ordinary token not protected by expensive-creature exception");
        for(String name:new String[]{"Doubling Season","Parallel Lives","Fervor","Impact Tremors","Coat of Arms","Purphoros, God of the Forge","Dovescape","Guile"}){fresh();add(name,p(1),ZoneType.Battlefield);refresh();push("Tidings",1,null,0);check(!willing(),"uncertain Bird/cast/counter engine holds "+name);}
        fresh();add("Impact Tremors",p(1),ZoneType.Graveyard);push("Tidings",1,null,0);check(willing(),"inactive entry engine ignored");
        fresh();c=add("Fervor",p(1),ZoneType.Battlefield);c.setPhasedOut(p(1));push("Tidings",1,null,0);check(willing(),"phased haste engine ignored");
        fresh();add("Wall of Omens",p(1),ZoneType.Battlefield);push("Tidings",1,null,0);check(willing(),"unrelated self-entry trigger ignored");
        fresh();add("Thalia, Guardian of Thraben",p(1),ZoneType.Battlefield);push("Tidings",1,null,0);check(selection()==null,"native tax cannot be ignored");add("Island",ai,ZoneType.Battlefield);refresh();check(selection()!=null,"native tax funded normally");
        fresh();push("Tidings",1,null,0);add("Lier, Disciple of the Drowned",p(1),ZoneType.Battlefield);refresh();check(!willing(),"native public uncounterability respected");
        fresh();ai.getLandsInPlay().getFirst().setTapped(true);push("Tidings",1,null,0);check(selection()==null,"native exhausted mana held");
        fresh();game.getAction().moveToGraveyard(ai.getLandsInPlay().getFirst(),null);add("Wastes",ai,ZoneType.Battlefield);push("Tidings",1,null,0);check(selection()==null,"native blue requirement preserved");
        for(PhaseType phase:new PhaseType[]{PhaseType.COMBAT_BEGIN,PhaseType.COMBAT_DECLARE_ATTACKERS}){fresh();game.getPhaseHandler().devModeSet(phase,ai);game.getPhaseHandler().setCombat(new Combat(ai));c=add("Air Elemental",ai,ZoneType.Battlefield);game.getCombat().addAttacker(c,p(1));push("Tidings",1,null,0);check(!willing(),"pre-blocker Bird combat hold "+phase);}
        fresh();game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS,ai);game.getPhaseHandler().setCombat(new Combat(ai));push("Tidings",1,null,0);check(willing(),"after blockers new Bird cannot join blocks");
        for(String change:new String[]{"top","gone","life","protected target","Bird engine","profile","source incarnation","target incarnation","target list"}){
            fresh();ai.setLife(3,null);push("Lightning Bolt",1,ai,0);spell=selection();check(spell!=null,"selected before "+change);
            switch(change){case "top":push("Opt",2,null,0);break;case "gone":game.getStack().resolveStack();break;case "life":ai.setLife(4,null);break;case "protected target":threat.resetTargets();threat.getTargets().add(p(2));break;case "Bird engine":add("Parallel Lives",p(1),ZoneType.Battlefield);break;case "profile":AiOverrideProfile.setActive("");break;case "source incarnation":aura.setGameTimestamp(game.getNextTimestamp());break;case "target incarnation":threat.getHostCard().setGameTimestamp(game.getNextTimestamp());break;case "target list":spell.resetTargets();break;}
            refresh();long mana=ai.getLandsInPlay().stream().filter(Card::isTapped).count();check(!ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"stale pre-payment cancellation "+change);check(aura.isInZone(ZoneType.Hand) && ai.getLandsInPlay().stream().filter(Card::isTapped).count()==mana,"cancel leaves source and mana intact "+change);
        }
        fresh();push("Tidings",1,null,0);Random expected=new Random(108009);forge.util.MyRandom.setRandom(new Random(108009));check(willing(),"deterministic decision accepts");check(forge.util.MyRandom.getRandom().nextLong()==expected.nextLong(),"policy consumes no live RNG");check(ai.getLandsInPlay().stream().noneMatch(Card::isTapped) && birds(p(1))==0,"evaluation spends no mana or creates live tokens");
        fresh();c=add("Air Elemental",ai,ZoneType.Battlefield);push("Doom Blade",1,c,0);expected=new Random(108010);forge.util.MyRandom.setRandom(new Random(108010));check(willing(),"native rescue prediction accepts");check(forge.util.MyRandom.getRandom().nextLong()==expected.nextLong(),"rescue exception consumes no live RNG");
        fresh();push("Tidings",1,null,0);add("Parallel Lives",p(2),ZoneType.Hand);add("Fervor",p(2),ZoneType.Library);refresh();check(willing(),"opponent hidden enhancement identities have no influence");
        fresh();push("Divination",1,null,0);check(selection()==null,"opt-in holds cheap draw through normal native counter selection");AiOverrideProfile.setActive("");check(selection()!=null,"original native counter selection still accepts cheap draw");
        for(String profile:new String[]{"","skeletal-scrying","prophetic-prism"}){fresh();AiOverrideProfile.setActive(profile);check(!SwanSongAi.uses(spell) && SwanSongAi.preflight(ai,spell),profile+": fresh default not intercepted");check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(aura),profile+": original admission preserved");push("Tidings",1,null,0);check(selection()!=null,profile+": native counter response bypass of removal tag preserved");}
        fresh();push("Tidings",1,null,0);spell=selection();check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"Song paid before native opponent response");add("Island",p(2),ZoneType.Battlefield);add("Island",p(2),ZoneType.Battlefield);Card cc=add("Counterspell",p(2),ZoneType.Hand);SpellAbility ctr=cc.getFirstSpellAbility();ctr.setActivatingPlayer(p(2));ctr.getTargets().add(spell);check(ComputerUtil.handlePlayingSpellAbility(p(2),ctr,null),"opponent natively counters Song");game.getStack().resolveStack();refresh();check(birds(p(1))==0 && game.getStack().size()==1 && ai.getLandsInPlay().stream().allMatch(Card::isTapped),"countered Song gives no Bird and does not refund mana");int lib=p(1).getCardsIn(ZoneType.Library).size();game.getStack().resolveStack();refresh();check(p(1).getCardsIn(ZoneType.Library).size()==lib-4,"original threat resolves after Song is countered");
        fresh();push("Tidings",1,null,0);Thread.currentThread().interrupt();check(!willing(),"interrupted decision holds");Thread.interrupted();check(willing(),"interruption does not leave global veto");AiOverrideProfile.setActive("");System.out.println("DONE "+passed+" checks");
    }
}
