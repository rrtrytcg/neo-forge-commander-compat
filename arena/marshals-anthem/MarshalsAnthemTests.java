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

/** Native four-player casting, multikicker payment and original reanimation fixtures. */
public class MarshalsAnthemTests {
    static Game game;
    static Player ai;
    static Card anthem;
    static SpellAbility spell;
    static int passed;
    static void check(boolean result,String label) {
        if(!result) { throw new AssertionError(label); }
        passed++; System.out.println("PASS "+label);
    }
    static Player player(int i) { return game.getPlayers().get(i); }
    static Card add(String name,Player owner,ZoneType zone) {
        Card card=Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name),owner);
        card.setGameTimestamp(game.getNextTimestamp()); owner.getZone(zone).add(card); card.setSickness(false); return card;
    }
    static void refresh() { game.getAction().checkStaticAbilities(); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState(); }
    static Card permanent(String name,Player owner) { Card card=add(name,owner,ZoneType.Battlefield); refresh(); return card; }
    static Card yard(String name) { Card card=add(name,ai,ZoneType.Graveyard); refresh(); return card; }
    static void setup(int plains,int mountains) {
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            LobbyPlayerAi lobby=new LobbyPlayerAi("Anthem-"+i,null); lobby.setAiProfile("Default");
            players.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Constructed); game=new Game(players,rules,new Match(rules,players,"Marshal's Anthem native fixtures"));
        game.setAge(GameStage.Play); ai=player(0); game.getPhaseHandler().devModeSet(PhaseType.MAIN2,ai);
        for(int i=0;i<plains;i++) { add("Plains",ai,ZoneType.Battlefield); }
        for(int i=0;i<mountains;i++) { add("Mountain",ai,ZoneType.Battlefield); }
        for(int i=0;i<12;i++) { add("Plains",ai,ZoneType.Library); }
        anthem=add("Marshal's Anthem",ai,ZoneType.Hand); spell=anthem.getFirstSpellAbility(); spell.setActivatingPlayer(ai);
        AiOverrideProfile.setActive("marshals-anthem"); refresh();
    }
    static boolean willing() { return SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai,spell).willingToPlay(); }
    static int kicks() { return spell.getOptionalKeywordAmount(Keyword.MULTIKICKER); }
    static SpellAbility selection() {
        List<SpellAbility> list=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();
        return list==null?null:list.stream().filter(sa->sa.getHostCard()==anthem).findFirst().orElse(null);
    }
    static int useful() { return MarshalsAnthemAi.useful(ai,spell,ai.getCardsIn(ZoneType.Graveyard)).size(); }
    static Card current(Card card) { return game.getCardState(card,null); }
    static void nativeCast(int expectedKicks,int expectedMana,int expectedReturns,String label) {
        SpellAbility selected=selection();
        check(selected!=null,label+": normal controller selects spell"); spell=selected;
        check(kicks()==expectedKicks,label+": selected useful kick count");
        int before=ai.getCreaturesInPlay().size();
        check(ComputerUtil.playStack(spell,ai,game),label+": native payment enters stack");
        check(ai.getLandsInPlay().stream().filter(Card::isTapped).count()==expectedMana,label+": native mana payment amount");
        game.getStack().resolveStack(); refresh();
        Card resolved=current(anthem);
        check(resolved!=null && resolved.isInZone(ZoneType.Battlefield) && resolved.getKickerMagnitude()==expectedKicks,label+": original enchantment enters with recorded kicks");
        game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
        int guard=0;
        while(!game.getStack().isEmpty() && guard++<12) {
            game.getStack().resolveStack(); game.getAction().checkStateEffects(true); refresh();
            game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
        }
        check(game.getStack().isEmpty(),label+": original ETB trigger completes");
        check(ai.getCreaturesInPlay().size()==before+expectedReturns,label+": expected actual creature returns");
    }
    static void countsAndCosts() {
        setup(12,0); check(!willing() && kicks()==0,"empty yard and empty board: hold without wasting kicks");
        permanent("Eager Cadet",ai); check(!willing() && kicks()==0,"one creature and no return: hold");
        permanent("Eager Cadet",ai); check(willing() && kicks()==0,"two creatures: useful unkicked anthem despite excess mana");
        nativeCast(0,4,0,"unkicked buff");
        check(ai.getCreaturesInPlay().stream().allMatch(c->c.getNetPower()==2 && c.getNetToughness()==2),"native +1/+1 applies exactly once to existing creatures");
        setup(12,0); yard("Serra Angel"); check(willing() && kicks()==1,"one useful return caps excess mana at one kick");
        nativeCast(1,6,1,"one return");
        check(ai.getCreaturesInPlay().get(0).getNetPower()==5 && ai.getCreaturesInPlay().get(0).getNetToughness()==5,"returned creature receives native anthem bonus");
        setup(8,0); yard("Serra Angel"); yard("Hill Giant"); yard("Eager Cadet");
        check(willing() && kicks()==2,"three returns with eight mana choose two kicks"); nativeCast(2,8,2,"two returns");
        check(ai.getCardsIn(ZoneType.Graveyard).stream().anyMatch(c->c.getName().equals("Eager Cadet")),"native most-expensive reanimation ranking retained");
        setup(10,0); yard("Serra Angel"); yard("Hill Giant"); yard("Eager Cadet");
        check(willing() && kicks()==3,"largest affordable useful count is three");
        setup(5,0); yard("Serra Angel"); check(!willing() && kicks()==0,"base affordable but return unaffordable: no useless body cast");
        setup(3,0); permanent("Eager Cadet",ai); permanent("Eager Cadet",ai);
        check(!willing() && selection()==null,"native base cost cannot be afforded with three mana");
        setup(2,4); yard("Serra Angel"); permanent("Eager Cadet",ai); permanent("Eager Cadet",ai);
        check(willing() && kicks()==0,"two white sources cannot also pay kicker white"); nativeCast(0,4,0,"color-sensitive zero kick");
        setup(3,3); yard("Serra Angel"); check(willing() && kicks()==1,"third white source enables one kick"); nativeCast(1,6,1,"color-sensitive kick");
        setup(6,0); yard("Serra Angel"); permanent("Thalia, Guardian of Thraben",player(1));
        check(!willing(),"native spell tax prevents six-mana kicked cast");
        add("Plains",ai,ZoneType.Battlefield); refresh(); check(willing() && kicks()==1,"seventh mana covers native tax"); nativeCast(1,7,1,"taxed return");
        setup(5,0); yard("Serra Angel"); permanent("Starfield Mystic",ai);
        check(willing() && kicks()==1,"native enchantment cost reduction is counted once"); nativeCast(1,5,1,"reduced return");
    }
    static void eligibility() {
        setup(10,0); add("Serra Angel",player(1),ZoneType.Graveyard); check(useful()==0 && !willing(),"opponent graveyard cannot fund kicks");
        for(String blocker:List.of("Ground Seal","Grafdigger's Cage","Containment Priest")) {
            setup(10,0); yard("Serra Angel"); permanent(blocker,player(1));
            check(useful()==0 && !willing(),"known return restriction: "+blocker);
            permanent("Eager Cadet",ai); permanent("Eager Cadet",ai);
            check(willing() && kicks()==0,"restriction still permits useful unkicked buff: "+blocker);
            nativeCast(0,4,0,"restricted buff "+blocker);
        }
        setup(10,0); yard("Isamaru, Hound of Konda"); yard("Isamaru, Hound of Konda");
        check(useful()==1 && willing() && kicks()==1,"duplicate graveyard legends fund only one useful kick"); nativeCast(1,6,1,"one legend");
        setup(10,0); permanent("Isamaru, Hound of Konda",ai); yard("Isamaru, Hound of Konda");
        check(useful()==0 && !willing(),"existing same-name legend is not a useful return");
        setup(10,0); permanent("Mirror Gallery",ai); yard("Isamaru, Hound of Konda"); yard("Isamaru, Hound of Konda");
        check(useful()==2 && willing() && kicks()==2,"native legend-rule exception permits both copies"); nativeCast(2,8,2,"legend exception");
        setup(6,0); yard("Eager Cadet"); permanent("Night of Souls' Betrayal",player(1));
        check(useful()==1 && willing() && kicks()==1,"future anthem saves creature from existing -1/-1"); nativeCast(1,6,1,"survival with new anthem");
        check(ai.getCreaturesInPlay().get(0).getNetToughness()==1,"new anthem bonus is neither omitted nor doubled");
        setup(10,0); yard("Eager Cadet"); permanent("Elesh Norn, Grand Cenobite",player(1));
        check(useful()==0 && !willing(),"creature still dies after future anthem: no kick");
        setup(8,0); yard("Serra Angel"); permanent("Eager Cadet",ai); permanent("Eager Cadet",ai);
        check(willing() && kicks()==1,"first evaluation selects one useful kick");
        for(Card c:new ArrayList<Card>(ai.getCardsIn(ZoneType.Graveyard))) { game.getAction().exile(c,null,null); }
        refresh(); check(willing() && kicks()==0,"reevaluation clears stale kicks after graveyard changes");
    }
    static void resolutionChangesAndIsolation() {
        setup(6,0); Card creature=yard("Serra Angel");
        check(willing() && kicks()==1 && ComputerUtil.playStack(spell,ai,game),"changed yard: native kicked cast");
        game.getAction().exile(creature,null,null); refresh();
        game.getStack().resolveStack(); refresh(); game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
        while(!game.getStack().isEmpty()) { game.getStack().resolveStack(); }
        check(ai.getCreaturesInPlay().isEmpty() && current(anthem).isInZone(ZoneType.Battlefield),"changed yard: native optional trigger returns none and keeps enchantment");
        for(String profile:List.of("","tragic-arrogance","dispatch","oreskos-explorer","mirror-entity","adaptive-enchantment","ahoy-mateys","decree-of-pain")) {
            setup(8,0); yard("Serra Angel"); AiOverrideProfile.setActive(profile);
            check(!MarshalsAnthemAi.uses(spell) && !AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(anthem) && selection()==null,"isolation: '"+profile+"'");
        }
        setup(10,0); AiOverrideProfile.setActive("");
        check(willing() && kicks()>0,"default generic multikicker still spends on empty yard");
        setup(4,0); Card chalice=add("Everflowing Chalice",ai,ZoneType.Hand); SpellAbility other=chalice.getFirstSpellAbility(); other.setActivatingPlayer(ai);
        check(!MarshalsAnthemAi.uses(other) && SpellApiToAi.Converter.get(other).canPlayWithSubs(ai,other).willingToPlay()
                && other.getOptionalKeywordAmount(Keyword.MULTIKICKER)==2,"unrelated multikicker behavior preserved under profile");
        check(ComputerUtilCard.isCardRemAIDeck(anthem),"original deck-building warning remains");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null); MyRandom.setRandom(new Random(7421));
        countsAndCosts(); eligibility(); resolutionChangesAndIsolation(); AiOverrideProfile.setActive("");
        System.out.println("DONE "+passed+" Marshal's Anthem checks");
    }
}
