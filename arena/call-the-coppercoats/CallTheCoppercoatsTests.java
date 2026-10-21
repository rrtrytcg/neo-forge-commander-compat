import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.*;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Original Coppercoats script through native selection, Strive payment and token resolution. */
public class CallTheCoppercoatsTests extends KenrithsTransformationTests {
    static void setup(int white,int colorless,int... counts){
        List<RegisteredPlayer> players=new ArrayList<>();for(int i=0;i<4;i++){LobbyPlayerAi lobby=new LobbyPlayerAi("Copper-"+i,null);lobby.setAiProfile("Default");players.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));}
        GameRules rules=new GameRules(GameType.Constructed);game=new Game(players,rules,new Match(rules,players,"Coppercoats fixtures"));game.setAge(GameStage.Play);ai=p(0);game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);
        for(int i=0;i<white;i++){add("Plains",ai,ZoneType.Battlefield);}for(int i=0;i<colorless;i++){add("Wastes",ai,ZoneType.Battlefield);}for(int i=0;i<20;i++){add("Plains",ai,ZoneType.Library);}
        for(int seat=1;seat<=3;seat++){for(int i=0;i<counts[seat-1];i++){add("Grizzly Bears",p(seat),ZoneType.Battlefield);}}
        aura=add("Call the Coppercoats",ai,ZoneType.Hand);spell=aura.getFirstSpellAbility();spell.setActivatingPlayer(ai);AiOverrideProfile.setActive("call-the-coppercoats");refresh();
    }
    static boolean willing(){return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai,spell).willingToPlay();}
    static List<Player> targets(){List<Player> out=new ArrayList<>();spell.getTargets().getTargetPlayers().forEach(out::add);return out;}
    static void expect(String label,int... seats){
        check(willing()==(seats.length>0),label+": policy willingness");List<Player> expected=Arrays.stream(seats).mapToObj(CallTheCoppercoatsTests::p).toList();check(targets().equals(expected),label+": exact native player targets");
    }
    static void cast(int tokens,int mana,String label){
        SpellAbility chosen=selection();check(chosen!=null,label+": normal controller selects original spell");spell=chosen;int old=ai.getCreaturesInPlay().size(),life=ai.getLife(),library=ai.getCardsIn(ZoneType.Library).size();
        check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null),label+": native payment enters stack");check(spell.getPayingMana().size()==mana,label+": native paid Strive-adjusted mana amount");
        game.getStack().resolveStack();game.getAction().checkStateEffects(true);refresh();
        List<Card> made=ai.getCreaturesInPlay().stream().filter(Card::isToken).toList();check(ai.getCreaturesInPlay().size()==old+tokens && made.size()==tokens,label+": exact original token count");
        check(made.stream().allMatch(c->c.getNetPower()==1 && c.getNetToughness()==1 && c.getColor().hasWhite() && c.getColor().isMonoColor() && c.getType().hasSubtype("Human") && c.getType().hasSubtype("Soldier")),label+": actual native 1/1 white Human Soldiers");
        check(game.getCardState(aura,null).isInZone(ZoneType.Graveyard) && game.getStack().isEmpty(),label+": original spell resolves normally");check(ai.getLife()==life && ai.getCardsIn(ZoneType.Library).size()==library,label+": token spell does not draw or lose life");
    }
    static void choices(){
        setup(7,0,0,0,0);expect("no creatures");setup(7,0,2,2,2);expect("no opponent reaches three");setup(7,0,3,1,0);expect("skip marginal extra opponents",1);cast(3,3,"single target boundary");
        setup(3,0,3,2,4);p(1).setLife(1,null);expect("largest army over weakest opponent",3);cast(4,3,"single target best army");
        setup(5,0,3,2,4);expect("best two-target combination",1,3);cast(7,5,"two native Strive targets");
        setup(7,0,3,2,4);expect("all three worthwhile and affordable",1,2,3);cast(9,7,"three native Strive targets");
        setup(3,0,4,4,0);expect("stable seat-ID tie",1);setup(1,6,3,2,4);expect("one white cannot pay a second Strive symbol",3);cast(4,3,"one-white single target");
        setup(2,5,3,2,4);expect("two white cannot pay third target",1,3);cast(7,5,"two-white pair");setup(0,9,3,2,4);expect("no white holds");setup(2,0,3,2,4);expect("less than base cost holds");
        setup(7,0,3,2,4);permanent("Leyline of Sanctity",1);expect("native player hexproof",2,3);cast(6,5,"targetable opponents only");
        setup(7,0,3,2,4);ai.setTeam(0);p(3).setTeam(0);expect("allies excluded",1,2);cast(5,5,"native opponent list");
        setup(7,0,3,2,4);permanent("Thalia, Guardian of Thraben",0);expect("native tax reduces affordable target count",1,3);cast(7,6,"native noncreature tax");
        setup(6,0,3,2,4);permanent("Goblin Electromancer",0);expect("native reduction permits three targets",1,2,3);cast(9,6,"native instant reduction");
    }
    static void timing(){
        for(PhaseType phase:List.of(PhaseType.MAIN1,PhaseType.COMBAT_BEGIN,PhaseType.COMBAT_DECLARE_ATTACKERS,PhaseType.COMBAT_DECLARE_BLOCKERS)){
            setup(7,0,3,2,4);game.getPhaseHandler().devModeSet(phase,ai);expect("not a combat policy "+phase);
        }
        setup(7,0,3,2,4);game.getPhaseHandler().devModeSet(PhaseType.MAIN2,p(1));expect("not another player's main phase");
        for(int seat=0;seat<4;seat++){
            setup(7,0,3,2,4);game.getPhaseHandler().devModeSet(PhaseType.END_OF_TURN,p(seat));boolean before=seat!=0 && game.getPhaseHandler().getNextTurn()==ai;check(willing()==before,"native next-turn end-step timing seat "+seat);
            if(before){cast(9,7,"last opponent end step");}
        }
        setup(7,0,3,2,4);Card bolt=add("Lightning Bolt",p(1),ZoneType.Hand);SpellAbility sa=bolt.getFirstSpellAbility();sa.setActivatingPlayer(p(1));sa.getTargets().add(ai);sa.setHostCard(game.getAction().moveToStack(bolt,sa));game.getStack().add(sa);refresh();expect("nonempty stack holds");
    }
    static void uncertain(){
        for(String risk:List.of("Doubling Season","Primal Vigor","Parallel Lives","Soul Warden","Purphoros, God of the Forge","Elesh Norn, Grand Cenobite","Illness in the Ranks","Containment Priest","Rest in Peace","Harsh Mentor")){
            setup(7,0,3,2,4);permanent(risk,0);expect("uncertain token/entry/cost interaction "+risk);
        }
        setup(7,0,3,2,4);Card risk=permanent("Soul Warden",0);risk.setPhasedOut(ai);refresh();expect("phased trigger ignored",1,2,3);
        setup(7,0,3,2,4);add("Soul Warden",ai,ZoneType.Graveyard);refresh();expect("inactive graveyard trigger ignored",1,2,3);
    }
    static void fresh(){
        for(String change:List.of("count","mana","targetability","phase","stack","risk","ranking","turn")){
            setup(5,0,3,2,4);spell=selection();check(spell!=null,"normal selection before "+change);List<Player> old=targets();
            switch(change){
                case "count":p(3).getZone(ZoneType.Battlefield).removeAllCards(true);break;
                case "mana":for(Card c:ai.getLandsInPlay()){c.setTapped(true);}break;
                case "targetability":permanent("Leyline of Sanctity",3);break;
                case "phase":game.getPhaseHandler().devModeSet(PhaseType.MAIN1,ai);break;
                case "stack":Card bolt=add("Lightning Bolt",p(1),ZoneType.Hand);SpellAbility sa=bolt.getFirstSpellAbility();sa.setActivatingPlayer(p(1));sa.getTargets().add(ai);sa.setHostCard(game.getAction().moveToStack(bolt,sa));game.getStack().add(sa);break;
                case "risk":permanent("Soul Warden",0);break;
                case "ranking":for(int i=0;i<5;i++){permanent("Grizzly Bears",2);}break;
                case "turn":game.getPhaseHandler().devModeSet(PhaseType.MAIN2,p(1));break;
            }
            refresh();check(!CallTheCoppercoatsAi.preflight(ai,spell),"fresh re-evaluation cancels "+change);int stackSize=game.getStack().size();check(!ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"native casting preflight cancels "+change);check(aura.isInZone(ZoneType.Hand) && game.getStack().size()==stackSize && targets().equals(old),"cancellation leaves source stack and selected targets intact "+change);
        }
    }
    static void resolutionChanges(){
        for(String change:List.of("counts","one protected","all protected")){
            setup(5,0,3,2,4);spell=selection();check(spell!=null,"normal selection before resolution change "+change);check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"native paid pair before resolution change "+change);
            if(change.equals("counts")){while(p(3).getCreaturesInPlay().size()>1){game.getAction().moveToGraveyard(p(3).getCreaturesInPlay().getFirst(),null);}}
            else {permanent("Leyline of Sanctity",1);if(change.equals("all protected")){permanent("Leyline of Sanctity",3);}}
            int expected=change.equals("all protected")?0:4;game.getStack().resolveStack();game.getAction().checkStateEffects(true);refresh();
            check(ai.getCreaturesInPlay().stream().filter(Card::isToken).count()==expected,"native resolution uses current legal targets and counts "+change);
            check(spell.getPayingMana().size()==5 && game.getCardState(aura,null).isInZone(ZoneType.Graveyard),"native Strive costs are not refunded "+change);
        }
        setup(5,0,3,2,4);spell=selection();check(spell!=null,"selected before still-useful count change");game.getAction().moveToGraveyard(p(1).getCreaturesInPlay().getFirst(),null);refresh();check(CallTheCoppercoatsAi.preflight(ai,spell),"still-best valid pair remains allowed after count change");
        setup(7,0,3,2,4);expect("public position baseline",1,2,3);for(int i=0;i<8;i++){add("Lightning Bolt",p(1),ZoneType.Hand);add("Counterspell",p(2),ZoneType.Library);}expect("opponent hidden hand and library do not change policy",1,2,3);
    }
    static void isolation(){
        setup(7,0,3,2,4);long time=game.getTimestamp(),rng=MyRandom.callCount();int hand=ai.getCardsIn(ZoneType.Hand).size(),library=ai.getCardsIn(ZoneType.Library).size();
        for(int i=0;i<4;i++){expect("stable repeated native projection "+i,1,2,3);}
        check(game.getTimestamp()==time && MyRandom.callCount()==rng,"projection consumes neither live timestamps nor evaluation RNG");check(ai.getLandsInPlay().stream().noneMatch(Card::isTapped) && ai.getManaPool().totalMana()==0 && ai.getCardsIn(ZoneType.Hand).size()==hand && ai.getCardsIn(ZoneType.Library).size()==library,"projection preserves live mana taps hand and library");
        check(CallTheCoppercoatsAi.preflight(ai,spell) && !CommandBeaconAi.paymentProjection(ai),"projection marker cannot authorize live payment behavior");
        Thread.currentThread().interrupt();check(!CallTheCoppercoatsAi.preflight(ai,spell),"interrupted evaluation holds");Thread.interrupted();
        setup(7,0,3,2,4);AiOverrideProfile.setActive("");check(selection()==null,"default admission remains blocked");check(aura.getRules().getAiHints().getRemAIDecks(),"original deck-building warning retained");AiOverrideProfile.setActive("timely-ward");check(selection()==null,"unrelated profile remains blocked");
    }
    public static void main(String[] args){GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(104011));choices();timing();uncertain();fresh();resolutionChanges();isolation();System.out.println("DONE "+passed+" Coppercoats checks");}
}
