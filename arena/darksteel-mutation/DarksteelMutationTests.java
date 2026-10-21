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
public class DarksteelMutationTests {
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
        GameRules rules=new GameRules(GameType.Constructed); game=new Game(players,rules,new Match(rules,players,"Darksteel Mutation fixtures"));
        game.setAge(GameStage.Play); ai=p(0); game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);
        for(int i=0;i<4;i++) { add("Plains",ai,ZoneType.Battlefield); }
        for(int i=0;i<12;i++) { add("Plains",ai,ZoneType.Library); }
        aura=add("Darksteel Mutation",ai,ZoneType.Hand); spell=aura.getFirstSpellAbility(); spell.setActivatingPlayer(ai);
        AiOverrideProfile.setActive("darksteel-mutation"); refresh();
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
        check(ComputerUtil.playStack(spell,ai,game),label+": native payment enters stack");
        check(ai.getLandsInPlay().stream().filter(Card::isTapped).count()==mana,label+": exact native mana payment");
        game.getStack().resolveStack(); refresh();
        Card resolved=game.getCardState(aura,null); Card c=game.getCardState(expected,null);
        check(resolved.isInZone(ZoneType.Battlefield) && resolved.getEnchantingCard()==c,label+": original Aura attached");
        check(c.getNetPower()==power && c.getNetToughness()==toughness,label+": actual final stats");
        check(c.getCurrentPower()==0 && c.getCurrentToughness()==1,label+": actual base 0/1");
        check(c.isArtifact() && c.getType().hasSubtype("Insect") && c.hasKeyword(Keyword.INDESTRUCTIBLE),label+": native transformation");
    }
    static void choices() {
        setup(); check(target()==null,"empty board holds");
        permanent("Colossus of Akros",0); check(target()==null,"never voluntarily enchants own threat");
        setup(); ai.setTeam(0); p(1).setTeam(0); permanent("Colossus of Akros",1);
        check(target()==null,"never voluntarily enchants allied threat");
        setup(); permanent("Eager Cadet",1); check(target()==null,"hold against plain 1/1");
        setup(); permanent("Ornithopter",1); check(target()==null,"hold against harmless zero-power flyer");
        setup(); permanent("Wall of Wood",1); check(target()==null,"do not upgrade defender to indestructible blocker");
        setup(); Card best=permanent("Colossus of Akros",3); permanent("Craw Wurm",1);
        check(target()==best,"all opponents considered; native threat ranking selects indestructible Colossus");
        cast(best,0,1,2,"already-indestructible target");
        check(game.getCardState(best,null).getSpellAbilities().stream().noneMatch(sa->sa.isAbility()),"Colossus loses activated ability");
        setup(); Card utility=permanent("Royal Assassin",2); check(target()==utility,"small activated utility creature qualifies");
        cast(utility,0,1,2,"utility removal");
        check(game.getCardState(utility,null).getSpellAbilities().stream().noneMatch(sa->sa.isAbility()),"Assassin activated ability actually removed");
        setup(); Card dork=permanent("Llanowar Elves",2); check(target()==dork,"small mana creature qualifies");
        cast(dork,0,1,2,"mana ability removal"); check(game.getCardState(dork,null).getManaAbilities().isEmpty(),"native mana ability removed");
        setup(); Card strong=permanent("Craw Wurm",2); Card commander=permanent("Eager Cadet",1); commander.setCommander(true);
        check(target()==strong,"small commander does not override native threat ranking");
        setup(); Card protectedCard=permanent("Slippery Bogle",1); Card legal=permanent("Craw Wurm",3);
        check(target()==legal && target()!=protectedCard,"native hexproof legality preserved across opponents");
        setup(); permanent("Black Knight",1); check(target()==null,"native protection from white excludes target");
        setup(); permanent("Silhana Ledgewalker",1); check(target()==null,"no legal target means hold");
    }
    static void layersAndLifecycle() {
        setup(); Card c=permanent("Craw Wurm",1); c.setCounters(CounterEnumType.P1P1,2); refresh();
        check(target()==c,"counter-bearing creature useful"); cast(c,2,3,2,"counters retained");
        setup(); c=permanent("Craw Wurm",1); permanent("Glorious Anthem",1);
        check(target()==c,"external buff does not hide base-stat reduction"); cast(c,1,2,2,"external buff retained");
        setup(); c=permanent("Llanowar Elves",1); permanent("Humility",2); check(target()==null,"already suppressed ability gives no utility benefit");
        setup(); c=permanent("Craw Wurm",1); Card existing=permanent("Darksteel Mutation",0); existing.attachToEntity(c,null); refresh();
        check(target()==null,"do not enchant already mutated target");
        setup(); c=permanent("Craw Wurm",1); long timestamp=game.getTimestamp();
        int power=c.getNetPower(), toughness=c.getNetToughness();
        for(int i=0;i<8;i++) { check(target()==c,"repeat preview stable "+i); }
        check(game.getTimestamp()==timestamp,"preview does not consume game timestamps");
        check(c.getNetPower()==power && c.getNetToughness()==toughness && !c.isArtifact(),"preview restores original live target stats/type");
        check(c.getAttachedCards().isEmpty() && !aura.isAttachedToEntity() && aura.isInZone(ZoneType.Hand),"preview has no live attachment/zone mutation");
        check(game.getStack().isEmpty(),"preview creates no stack actions");
        spell=selection(); check(spell!=null && ComputerUtil.playStack(spell,ai,game),"target-change fixture casts natively");
        game.getAction().moveToGraveyard(c,null); game.getStack().resolveStack(); refresh();
        check(game.getCardState(aura,null).isInZone(ZoneType.Graveyard),"native fizzle when target leaves");
        setup(); permanent("Craw Wurm",1); permanent("Thalia, Guardian of Thraben",2); cast(target(),0,1,3,"native noncreature tax");
        setup(); c=permanent("Craw Wurm",1); for(Card land:ai.getLandsInPlay()) { land.setTapped(true); }
        check(selection()==null,"native controller rejects unavailable mana");
        setup(); permanent("Craw Wurm",1);
        for(Card land:new ArrayList<>(ai.getLandsInPlay())) { ai.getZone(ZoneType.Battlefield).remove(land); }
        permanent("Mountain",0); permanent("Mountain",0); refresh();
        check(selection()==null,"native controller rejects missing white mana");
    }
    static void defaults() {
        setup(); permanent("Craw Wurm",1); AiOverrideProfile.setActive(""); check(selection()==null,"default admission still blocked");
        check(aura.getRules().getAiHints().getRemAIDecks(),"original deck-building warning retained");
        AiOverrideProfile.setActive("marshals-anthem"); check(selection()==null,"unrelated profile does not unlock Mutation");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null); MyRandom.setRandom(new Random(96341));
        choices(); layersAndLifecycle(); defaults(); System.out.println("DONE "+passed+" Darksteel Mutation checks");
    }
}

