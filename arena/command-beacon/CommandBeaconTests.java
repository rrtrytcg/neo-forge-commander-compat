import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.*;

/** Original-script native controller, sacrifice, return, and commander-payment regression fixtures. */
public class CommandBeaconTests extends TradingPostTests {
    static Card beacon, commander;
    static SpellAbility ability;
    static void start(String name, int casts, int lands) {
        setup(40); game.getRules().addAppliedVariant(forge.game.GameType.Commander); AiOverrideProfile.setActive("command-beacon");
        ai.getZone(ZoneType.Battlefield).removeAllCards(true);
        for(int i=0;i<lands;i++) {add("Plains",ai,ZoneType.Battlefield);}
        beacon=add("Command Beacon",ai,ZoneType.Battlefield);
        commander=add(name,ai,ZoneType.Command); ai.addCommander(commander);
        for(int i=0;i<casts;i++) {ai.incCommanderCast(commander);}
        ability=beacon.getSpellAbilities().stream().filter(s->s.getApi()==forge.game.ability.ApiType.ChangeZone).findFirst().orElseThrow();
        ability.setActivatingPlayer(ai); refresh();
    }
    static boolean willing() {return SpellApiToAi.Converter.get(ability).canPlayWithSubs(ai,ability).willingToPlay();}
    static SpellAbility selected() {
        List<SpellAbility> list=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();
        return list==null?null:list.stream().filter(s->s.getHostCard()==beacon && !s.isManaAbility()).findFirst().orElse(null);
    }
    static void basic() {
        start("Isamaru, Hound of Konda",2,4);
        check(willing(),"two prior command casts and four remaining lands qualify");
        check(CommandBeaconAi.choose(ability,ai.getCardsIn(ZoneType.Command))==commander,"bind exact owned commander");
        check(willing() && willing(),"evaluation is repeatable");
        check(beacon.isInPlay() && !beacon.isTapped() && commander.isInZone(ZoneType.Command),"projection never moves or taps live cards");
        check(ai.getCommanderCast(commander)==2 && ai.getLandsInPlay().size()==5,"projection preserves live tax and land count");
        long rng=forge.util.MyRandom.callCount();
        check(CommandBeaconAi.consider(ai,ability).willingToPlay(),"direct policy forecast qualifies");
        check(forge.util.MyRandom.callCount()==rng,"forecast consumes no shared RNG");
        check(!CommandBeaconAi.paymentProjection(ai),"projection context never applies to live player");
        Thread.currentThread().interrupt();
        try {check(!CommandBeaconAi.consider(ai,ability).willingToPlay(),"interrupted forecast holds");}
        finally {Thread.interrupted();}
        check(beacon.isInPlay() && !beacon.isTapped() && !CommandBeaconAi.paymentProjection(ai),"interrupted forecast preserves live land and clears context");
        for(int casts:new int[]{0,1,2,3}) {start("Isamaru, Hound of Konda",casts,4);check(willing()==(casts>=2),"prior command casts threshold "+casts);}
        for(int lands:new int[]{0,2,3,4,5}) {start("Isamaru, Hound of Konda",2,lands);check(willing()==(lands>=4),"remaining land threshold "+lands);}
        for(PhaseType phase:List.of(PhaseType.MAIN1,PhaseType.MAIN2,PhaseType.UPKEEP,PhaseType.END_OF_TURN,PhaseType.COMBAT_DECLARE_BLOCKERS)) {
            start("Isamaru, Hound of Konda",2,4);game.getPhaseHandler().devModeSet(phase,ai,false);
            check(willing()==(phase==PhaseType.MAIN1 || phase==PhaseType.MAIN2),"own-main window "+phase);
        }
        start("Isamaru, Hound of Konda",2,4);game.getPhaseHandler().devModeSet(PhaseType.MAIN2,p(1),false);check(!willing(),"opponent main holds");
        start("Isamaru, Hound of Konda",2,4);beacon.setTapped(true);check(!willing(),"tapped Beacon holds");
        start("Isamaru, Hound of Konda",2,4);beacon.setPhasedOut(ai);check(!willing(),"phased Beacon holds");
        start("Isamaru, Hound of Konda",2,4);commander.getZone().remove(commander);ai.getZone(ZoneType.Hand).add(commander);refresh();check(!willing(),"commander already in hand holds");
        start("Isamaru, Hound of Konda",2,4);ai.removeCommander(commander);check(!willing(),"noncommander in command zone holds");
        start("Isamaru, Hound of Konda",2,4);Card other=add("Kytheon, Hero of Akros",p(1),ZoneType.Command);p(1).addCommander(other);
        for(int i=0;i<3;i++) {p(1).incCommanderCast(other);}ai.removeCommander(commander);refresh();check(!willing(),"opponent commander never qualifies");
        start("Isamaru, Hound of Konda",2,4);Card murder=add("Murder",p(1),ZoneType.Hand);SpellAbility removal=murder.getFirstSpellAbility();
        removal.setActivatingPlayer(p(1));removal.getTargets().add(permanent("Grizzly Bears",0));removal.setHostCard(game.getAction().moveToStack(murder,removal));game.getStack().add(removal);refresh();check(!willing(),"nonempty stack holds");
    }
    static void costs() {
        start("Isamaru, Hound of Konda",2,4);
        for(Card land:ai.getLandsInPlay()) {if(land!=beacon) {land.setTapped(true);}}check(!willing(),"no white mana after sacrifice holds");
        start("Isamaru, Hound of Konda",2,4);
        ai.getZone(ZoneType.Battlefield).removeAllCards(true);ai.getZone(ZoneType.Battlefield).add(beacon);
        for(int i=0;i<4;i++) {add("Wastes",ai,ZoneType.Battlefield);}refresh();check(!willing(),"colorless cannot pay white");
        start("Jareth, Leonine Titan",2,5);check(!willing(),"Beacon's own mana cannot pay six-mana commander after sacrifice");
        start("Jareth, Leonine Titan",2,6);check(willing(),"six remaining Plains pay six-mana commander");
        start("Jareth, Leonine Titan",2,6);permanent("God-Pharaoh's Statue",1);check(!willing(),"native public tax makes six remaining mana insufficient");
        start("Jareth, Leonine Titan",2,8);permanent("God-Pharaoh's Statue",1);check(willing(),"eight remaining mana pay native tax");
        start("Isamaru, Hound of Konda",2,4);permanent("Meddling Mage",1).setNamedCards(List.of("Isamaru, Hound of Konda"));refresh();check(!willing(),"native named cast prohibition holds");
        for(String card:List.of("Rest in Peace","Blood Artist","Titania, Protector of Argoth")) {
            start("Isamaru, Hound of Konda",2,4);permanent(card,1);check(!willing(),"defer omitted zone or sacrifice effects: "+card);
        }
        start("Isamaru, Hound of Konda",2,4);permanent("Pithing Needle",1).setNamedCards(List.of("Command Beacon"));refresh();check(!willing(),"native activation prohibition holds");
    }
    static void multiple() {
        start("Isamaru, Hound of Konda",2,4);Card other=add("Kytheon, Hero of Akros",ai,ZoneType.Command);ai.addCommander(other);
        for(int i=0;i<3;i++) {ai.incCommanderCast(other);}refresh();
        check(willing() && CommandBeaconAi.choose(ability,ai.getCardsIn(ZoneType.Command))==other,"choose highest tax savings among payable commanders");
        start("Isamaru, Hound of Konda",2,4);other=add("Jareth, Leonine Titan",ai,ZoneType.Command);ai.addCommander(other);
        for(int i=0;i<4;i++) {ai.incCommanderCast(other);}refresh();
        check(willing() && CommandBeaconAi.choose(ability,ai.getCardsIn(ZoneType.Command))==commander,"unpayable higher-tax commander does not displace payable one");
        start("Isamaru, Hound of Konda",2,4);other=add("Kytheon, Hero of Akros",ai,ZoneType.Command);ai.addCommander(other);
        for(int i=0;i<2;i++) {ai.incCommanderCast(other);}refresh();
        check(willing() && CommandBeaconAi.choose(ability,ai.getCardsIn(ZoneType.Command))==commander,"equal savings choose stable lower card id");
    }
    static void nativeUse() {
        start("Isamaru, Hound of Konda",3,4);SpellAbility chosen=selected();
        check(chosen!=null,"normal AI controller selects Beacon activation");
        check(ComputerUtil.handlePlayingSpellAbility(ai,chosen,null),"normal native tap and sacrifice payment");
        check(game.getCardState(beacon,null).isInZone(ZoneType.Graveyard),"Beacon sacrificed by native cost");
        check(ai.getLandsInPlay().size()==4 && !game.getStack().isEmpty(),"native activation on stack leaves four lands");
        game.getStack().resolveStack();game.getAction().checkStateEffects(true);refresh();
        Card returned=game.getCardState(commander,null);
        check(returned.isInZone(ZoneType.Hand),"native ability returns exact commander to hand");
        check(ai.getCommanderCast(returned)==3,"return does not reset commander cast history");
        SpellAbility cast=returned.getFirstSpellAbility();cast.setActivatingPlayer(ai);
        check(cast.canPlay() && ComputerUtilCost.canPayCost(cast,ai,false),"returned commander is natively legal and payable from hand");
        check(ComputerUtil.handlePlayingSpellAbility(ai,cast,null),"native hand cast pays normal costs");
        check(ai.getLandsInPlay().stream().filter(Card::isTapped).count()==1,"hand cast pays white only without six tax");
        game.getStack().resolveStack();game.getAction().checkStateEffects(true);refresh();
        check(game.getCardState(returned,null).isInPlay(),"native commander hand spell resolves");
        check(ai.getCommanderCast(game.getCardState(returned,null))==3,"hand cast does not increment command-zone cast count");
    }
    static void cancellation() {
        for(int kind=0;kind<5;kind++) {
            start("Isamaru, Hound of Konda",2,4);check(willing(),"fresh plan before stale change "+kind);
            if(kind==0) {for(Card c:ai.getLandsInPlay()) {if(c!=beacon) {c.setTapped(true);}}}
            if(kind==1) {game.getPhaseHandler().devModeSet(PhaseType.END_OF_TURN,ai,false);}
            if(kind==2) {commander.getZone().remove(commander);ai.getZone(ZoneType.Exile).add(commander);}
            if(kind==3) {permanent("Rest in Peace",1);}
            if(kind==4) {permanent("Pithing Needle",1).setNamedCards(List.of("Command Beacon"));refresh();}
            check(!ComputerUtil.handlePlayingSpellAbility(ai,ability,null),"stale plan cancels before native payment "+kind);
            check(beacon.isInPlay() && !beacon.isTapped(),"cancellation preserves Beacon "+kind);
        }
        start("Isamaru, Hound of Konda",2,4);
        check(beacon.getRules().getAiHints().getRemAIDecks(),"original script prohibition retained");
        for(String profile:List.of("","hunters-insight","greater-good")) {
            AiOverrideProfile.setActive(profile);check(!CommandBeaconAi.uses(ability) && selected()==null,"default or unrelated profile remains blocked: "+profile);
        }
        start("Isamaru, Hound of Konda",0,4);SpellAbility mana=beacon.getManaAbilities().getFirst();mana.setActivatingPlayer(ai);
        check(!CommandBeaconAi.uses(mana) && mana.canPlay(),"ordinary native mana ability unchanged");
    }
    static void informationBoundary() {
        start("Isamaru, Hound of Konda",2,4);
        Card secret=add("Murder",p(1),ZoneType.Hand);refresh();
        forge.ai.simulation.GameCopier plain=new forge.ai.simulation.GameCopier(game);plain.makeCopy(null,ai);
        check(plain.find(secret).getName().equals("Murder"),"default copier preserves prior behavior");
        forge.ai.simulation.GameCopier masked=new forge.ai.simulation.GameCopier(game,true);masked.makeCopy(null,ai);
        check(!masked.find(secret).getName().equals("Murder"),"opt-in copy masks opponent hidden identity");
        check(masked.find(commander).getName().equals(commander.getName()),"public command-zone identity remains visible");
        check(willing(),"hidden opponent removal is not a veto");
        secret.getZone().remove(secret);add("Counterspell",p(1),ZoneType.Hand);refresh();
        check(willing(),"different hidden identity leaves same public decision");
        game.EXPERIMENTAL_RESTORE_SNAPSHOT=true;
        check(!willing(),"unsupported snapshot restore copy is held");game.EXPERIMENTAL_RESTORE_SNAPSHOT=false;
        check(CommandBeaconAi.consider(ai,ability).willingToPlay(),"normal copy resumes after held snapshot mode");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);forge.util.MyRandom.setRandom(new Random(100001));passed=0;
        basic();costs();multiple();nativeUse();cancellation();informationBoundary();AiOverrideProfile.setActive("");
        System.out.println("DONE "+passed+" Command Beacon checks");
    }
}
