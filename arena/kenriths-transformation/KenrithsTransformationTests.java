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

/** Original-script fixtures exercise the normal AI entry point and native Aura payment/resolution. */
public class KenrithsTransformationTests {
    static Game game;
    static Player ai;
    static Card aura;
    static SpellAbility spell;
    static int passed;
    static void check(boolean ok,String label) {
        if(!ok) { throw new AssertionError(label); }
        passed++; System.out.println("PASS "+label);
    }
    static Player p(int i) { return game.getPlayers().get(i); }
    static Card add(String name,Player owner,ZoneType zone) {
        Card c=Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name),owner);
        c.setGameTimestamp(game.getNextTimestamp()); owner.getZone(zone).add(c); c.setSickness(false); return c;
    }
    static void refresh() { game.getAction().checkStaticAbilities(); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState(); }
    static Card permanent(String name,int seat) { Card c=add(name,p(seat),ZoneType.Battlefield); refresh(); return c; }
    static void setup() {
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            LobbyPlayerAi lobby=new LobbyPlayerAi("Mutation-"+i,null); lobby.setAiProfile("Default");
            players.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Constructed); game=new Game(players,rules,new Match(rules,players,"Kenrith's Transformation fixtures"));
        game.setAge(GameStage.Play); ai=p(0); game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);
        for(int i=0;i<4;i++) { add("Forest",ai,ZoneType.Battlefield); }
        for(int i=0;i<12;i++) { add("Forest",ai,ZoneType.Library); }
        aura=add("Kenrith's Transformation",ai,ZoneType.Hand); spell=aura.getFirstSpellAbility(); spell.setActivatingPlayer(ai);
        AiOverrideProfile.setActive("kenriths-transformation"); refresh();
    }
    static Card target() {
        boolean willing=SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai,spell).willingToPlay();
        return willing?spell.getTargetCard():null;
    }
    static SpellAbility selection() {
        List<SpellAbility> list=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();
        return list==null?null:list.stream().filter(sa->sa.getHostCard()==aura).findFirst().orElse(null);
    }
    static void cast(Card expected,int power,int toughness,int mana,String label) {
        SpellAbility selected=selection(); check(selected!=null,label+": normal controller selects Aura");
        spell=selected; check(spell.getTargetCard()==expected,label+": native selected target");
        check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null),label+": native payment enters stack");
        check(ai.getLandsInPlay().stream().filter(Card::isTapped).count()==mana,label+": exact native mana payment");
        int library=ai.getCardsIn(ZoneType.Library).size(), hand=ai.getCardsIn(ZoneType.Hand).size(), life=ai.getLife();
        game.getStack().resolveStack(); refresh();
        Card resolved=game.getCardState(aura,null); Card c=game.getCardState(expected,null);
        check(resolved.isInZone(ZoneType.Battlefield) && resolved.getEnchantingCard()==c,label+": original Aura attached");
        check(c.getNetPower()==power && c.getNetToughness()==toughness,label+": actual final stats");
        check(c.getCurrentPower()==3 && c.getCurrentToughness()==3,label+": actual base 3/3");
        check(!c.isArtifact() && c.getType().hasSubtype("Elk") && c.getColor().isMonoColor() && c.getColor().hasGreen(),label+": native transformation");        check(c.getSpellAbilities().stream().noneMatch(SpellAbility::isAbility),label+": native ability removal");
        check(ai.getCardsIn(ZoneType.Library).size()==library,label+": Aura resolution alone has not drawn");
        game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
        check(!game.getStack().isEmpty(),label+": original entry draw trigger stacked");
        int guard=0; while(!game.getStack().isEmpty() && guard++<4){game.getStack().resolveStack();}
        check(game.getStack().isEmpty() && ai.getCardsIn(ZoneType.Library).size()==library-1 && ai.getCardsIn(ZoneType.Hand).size()==hand+1,label+": exact native one-card draw");
        check(ai.getLife()==life && ai.getCardsIn(ZoneType.Library).size()>=4,label+": native life and library reserve preserved");
    }

    static void choices() {
        setup(); check(target()==null,"empty board holds");
        permanent("Colossus of Akros",0); check(target()==null,"own threat excluded");
        setup(); ai.setTeam(0); p(1).setTeam(0); permanent("Craw Wurm",1); check(target()==null,"allied threat excluded");
        for(String name:List.of("Eager Cadet","Grizzly Bears","Llanowar Elves","Royal Assassin","Wall of Wood","Ornithopter","Centaur Courser")) {
            setup(); permanent(name,1); check(target()==null,"do not upgrade or redundantly transform "+name);
        }
        setup(); Card best=permanent("Colossus of Akros",3); permanent("Craw Wurm",1);
        check(target()==best,"all opponents considered; largest native threat selected");
        cast(best,3,3,2,"indestructible threat");
        check(game.getCardState(best,null).getSpellAbilities().stream().noneMatch(SpellAbility::isAbility),"Colossus loses native abilities");
        check(!game.getCardState(best,null).hasKeyword(Keyword.INDESTRUCTIBLE),"indestructibility actually removed");
        setup(); Card utility=permanent("Llanowar Tribe",2); check(target()==utility,"same-sized mana creature loses useful ability");
        cast(utility,3,3,2,"mana ability removal"); check(game.getCardState(utility,null).getManaAbilities().isEmpty(),"native mana ability removed");
        setup(); permanent("Slippery Bogle",1); Card legal=permanent("Craw Wurm",2); check(target()==legal,"native hexproof legality preserved");
        setup(); Card protectedCard=permanent("Dunerider Outlaw",1); check(!spell.canTarget(protectedCard) && target()==null,"native protection from green preserved");
        setup(); permanent("Silhana Ledgewalker",1); check(selection()==null,"no legal target means controller holds");
        setup(); permanent("Elvish Archdruid",1); check(target()==null,"continuous source collateral deferred");
    }
    static void drawSafety() {
        for(int n=0;n<=5;n++) {
            setup(); Card c=permanent("Craw Wurm",1);
            while(ai.getCardsIn(ZoneType.Library).size()>n) {ai.getZone(ZoneType.Library).remove(ai.getCardsIn(ZoneType.Library).getFirst());}
            check((target()==c)==(n==5),"four-card post-draw reserve at library size "+n);
            if(n==5) {cast(c,3,3,2,"exact library boundary");}
        }
        for(String name:List.of("Underworld Dreams","Notion Thief","Alhammarret's Archive","Argothian Enchantress","Setessan Champion","Kor Spiritdancer","Eidolon of Blossoms")) {
            setup(); permanent("Craw Wurm",1); permanent(name,name.equals("Alhammarret's Archive")?0:2);
            check(target()==null,"uncertain draw or enchantment interaction holds: "+name);
        }
        setup(); permanent("Craw Wurm",1); permanent("Maralen of the Mornsong",2); check(target()==null,"native cannot-draw restriction holds");
        setup(); Card c=permanent("Craw Wurm",1); Card risk=permanent("Underworld Dreams",2); risk.setPhasedOut(p(2)); refresh();
        check(target()==c,"phased-out draw trigger ignored");
        setup(); c=permanent("Craw Wurm",1); add("Underworld Dreams",p(2),ZoneType.Graveyard); refresh();
        check(target()==c,"inactive graveyard trigger ignored");
        setup(); c=permanent("Craw Wurm",1); Card existing=permanent("Kenrith's Transformation",0); existing.attachToEntity(c,null); refresh();
        check(target()==null,"already transformed creature excluded");
        Card other=permanent("Craw Wurm",3); check(target()==other,"old Aura self-ETB trigger does not block later casts");
    }
    static void layersAndLifecycle() {
        setup(); Card c=permanent("Craw Wurm",1); c.setCounters(CounterEnumType.P1P1,2); refresh();
        check(target()==c,"counter-bearing creature useful"); cast(c,5,5,2,"counters retained");
        setup(); c=permanent("Craw Wurm",1); permanent("Glorious Anthem",1);
        check(target()==c,"external buff does not hide downgrade"); cast(c,4,4,2,"external buff retained");
        setup(); permanent("Llanowar Tribe",1); permanent("Humility",2); check(target()==null,"already suppressed ability gives no benefit");
        setup(); c=permanent("Craw Wurm",1); long timestamp=game.getTimestamp();
        int power=c.getNetPower(),toughness=c.getNetToughness();
        for(int i=0;i<4;i++) {check(target()==c,"repeat preview stable "+i);}
        check(game.getTimestamp()==timestamp,"preview does not consume timestamps");
        check(c.getNetPower()==power && c.getNetToughness()==toughness && !c.getType().hasSubtype("Elk"),"preview restores live stats and type");
        check(c.getAttachedCards().isEmpty() && !aura.isAttachedToEntity() && aura.isInZone(ZoneType.Hand),"preview never attaches or moves live cards");
        check(game.getStack().isEmpty(),"preview creates no stack actions");
        MyRandom.setRandom(new Random(7781)); target(); long actual=MyRandom.getRandom().nextLong();
        check(actual==new Random(7781).nextLong(),"policy preview consumes no evaluation RNG");
        setup(); c=permanent("Craw Wurm",1); spell=selection(); check(spell!=null,"fizzle fixture selects original Aura");
        check(ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"fizzle fixture casts natively"); game.getAction().moveToGraveyard(c,null);
        int library=ai.getCardsIn(ZoneType.Library).size(); game.getStack().resolveStack(); refresh();
        game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
        check(game.getCardState(aura,null).isInZone(ZoneType.Graveyard),"native fizzle when target leaves");
        check(game.getStack().isEmpty() && ai.getCardsIn(ZoneType.Library).size()==library,"fizzled Aura never enters or draws");
        setup(); permanent("Craw Wurm",1); permanent("Thalia, Guardian of Thraben",2); cast(target(),3,3,3,"native noncreature tax");
        setup(); permanent("Craw Wurm",1); for(Card land:ai.getLandsInPlay()) {land.setTapped(true);} check(selection()==null,"native unavailable mana holds");
        setup(); permanent("Craw Wurm",1); for(Card land:new ArrayList<>(ai.getLandsInPlay())) {ai.getZone(ZoneType.Battlefield).remove(land);}
        permanent("Mountain",0); permanent("Mountain",0); check(selection()==null,"native missing green mana holds");
    }
    static void freshChecks() {
        for(String change:List.of("library","draw trigger","target gone","target friendly","target phased","target upgraded","target protected")) {
            setup(); Card c=permanent("Craw Wurm",1); spell=selection(); check(spell!=null,"fresh check selected: "+change);
            switch(change) {
                case "library": while(ai.getCardsIn(ZoneType.Library).size()>4){ai.getZone(ZoneType.Library).remove(ai.getCardsIn(ZoneType.Library).getFirst());} break;
                case "draw trigger": permanent("Underworld Dreams",2); break;
                case "target gone": game.getAction().moveToGraveyard(c,null); break;
                case "target friendly": c.setController(ai,game.getNextTimestamp()); break;
                case "target phased": c.setPhasedOut(p(1)); break;
                case "target upgraded": c.setBasePower(1); c.setBaseToughness(1); break;
                case "target protected": c.addIntrinsicKeyword("Shroud"); break;
            }
            refresh(); check(!ComputerUtil.handlePlayingSpellAbility(ai,spell,null),"fresh safety cancels before native costs: "+change);
            check(ai.getLandsInPlay().stream().noneMatch(Card::isTapped) && aura.isInZone(ZoneType.Hand) && game.getStack().isEmpty(),"cancel has no mana/zone/stack mutation: "+change);
        }
    }
    static void defaults() {
        setup(); permanent("Craw Wurm",1); AiOverrideProfile.setActive(""); check(selection()==null,"default admission still blocked");
        check(aura.getRules().getAiHints().getRemAIDecks(),"original script warning retained");
        AiOverrideProfile.setActive("darksteel-mutation"); check(selection()==null,"unrelated profile does not unlock Transformation");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null); MyRandom.setRandom(new Random(102011));
        choices(); drawSafety(); layersAndLifecycle(); freshChecks(); defaults(); System.out.println("DONE "+passed+" Kenrith's Transformation checks");
    }
}




