import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.ability.ApiType;
import forge.game.card.*;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

class CurseOfTheSwineTests {
    static Game game;
    static Player ai;
    static SpellAbility swine;
    static int passed;
    static void check(boolean value,String label) {
        if(!value) { throw new AssertionError(label); }
        passed++; System.out.println("PASS "+label);
    }
    static Player opp(int i) { return game.getPlayers().get(i); }
    static Card add(String name,Player owner,ZoneType zone) {
        Card card=Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name),owner);
        card.setGameTimestamp(game.getNextTimestamp()); owner.getZone(zone).add(card); return card;
    }
    static void refresh() {
        game.getAction().checkStaticAbilities(); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
    }
    static void setup(int islands) {
        List<RegisteredPlayer> players=new ArrayList<>();
        for(int i=0;i<4;i++) {
            LobbyPlayerAi lobby=new LobbyPlayerAi("Swine-"+i,null); lobby.setAiProfile("Default");
            players.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules=new GameRules(GameType.Constructed);
        game=new Game(players,rules,new Match(rules,players,"Swine native fixtures")); game.setAge(GameStage.Play);
        ai=opp(0); game.getPhaseHandler().devModeSet(PhaseType.MAIN1,ai);
        for(int i=0;i<islands;i++) { add("Island",ai,ZoneType.Battlefield); }
        swine=add("Curse of the Swine",ai,ZoneType.Hand).getFirstSpellAbility(); swine.setActivatingPlayer(ai);
        AiOverrideProfile.setActive("curse-of-the-swine"); refresh();
    }
    static boolean willing() { return SpellApiToAi.Converter.get(swine).canPlayWithSubs(ai,swine).willingToPlay(); }
    static Card creature(String name,Player owner) { Card c=add(name,owner,ZoneType.Battlefield); refresh(); return c; }
    static void attach(String name,Card host) { add(name,host.getController(),ZoneType.Battlefield).attachToEntity(host,null); refresh(); }
    static void decisions() {
        setup(5); check(!willing(),"empty board holds instead of X=0");
        creature("Grizzly Bears",opp(1)); creature("Centaur Courser",opp(2));
        check(!willing(),"plain creatures below 4/4 do not qualify");
        creature("Darksteel Colossus",ai); add("Omniscience",opp(1),ZoneType.Battlefield); refresh();
        check(!willing(),"skip own creatures and high-value noncreatures");
        Card small=opp(1).getCreaturesInPlay().getFirst(); attach("Bonesplitter",small);
        check(willing() && swine.getXManaCostPaid()==1 && swine.getTargets().contains(small),
                "equipped small creature qualifies; X is target count rather than all spare mana");
        Card big=creature("Leatherback Baloth",opp(2));
        Card enchantedBig=creature("Colossal Dreadmaw",opp(3)); attach("Spider Umbra",enchantedBig);
        check(willing() && swine.getXManaCostPaid()==3 && swine.getTargets().size()==3,
                "up to affordable X qualifying creatures across opponents");
        check(swine.getTargets().getTargetCards().get(0)==enchantedBig
                && swine.getTargets().getTargetCards().get(1)==small && swine.getTargets().getTargetCards().get(2)==big,
                "reuse Beast Within creature priority order exactly");
        check(swine.isTargetNumberValid(),"native X target count is legal");
        // Re-evaluation must not accumulate old targets or a stale X.
        opp(3).getZone(ZoneType.Battlefield).remove(enchantedBig);
        game.getAction().moveToGraveyard(big,null); refresh();
        check(willing() && swine.getXManaCostPaid()==1 && swine.getTargets().size()==1,
                "re-evaluation clears prior targets and shrinks X");
        setup(3); Card low=creature("Leatherback Baloth",opp(1)); Card high=creature("Colossal Dreadmaw",opp(2));
        check(willing() && swine.getXManaCostPaid()==1 && swine.getTargetCard()==high,
                "one spare mana picks highest mana value within priority tier");
        setup(2); creature("Colossal Dreadmaw",opp(1));
        check(!willing() && swine.getTargets().isEmpty() && swine.getXManaCostPaid()==0,"UU alone cannot fund a useful X");
        setup(0); for(int i=0;i<5;i++) { add("Mountain",ai,ZoneType.Battlefield); } creature("Colossal Dreadmaw",opp(1));
        check(!willing(),"generic mana cannot replace the two blue pips");
        setup(3); creature("Colossal Dreadmaw",opp(1));
        for(Card land:ai.getLandsInPlay()) { land.setTapped(true); }
        check(!willing(),"native mana prediction rejects tapped lands");
        setup(4); creature("Leatherback Baloth",opp(1)); creature("Colossal Dreadmaw",opp(2));
        add("Sphere of Resistance",opp(3),ZoneType.Battlefield); refresh();
        check(willing() && swine.getXManaCostPaid()==1,"native spell tax reduces affordable X");
        setup(5); creature("Simic Sky Swallower",opp(1));
        Card hexproof=creature("Troll Ascetic",opp(2)); attach("Rancor",hexproof);
        Card protectedBlue=creature("Scryb Ranger",opp(3)); attach("Bonesplitter",protectedBlue);
        check(!willing(),"native shroud, hexproof and protection from blue reject targets");
        Card immortal=creature("Darksteel Colossus",opp(1));
        check(willing() && swine.getTargetCard()==immortal,"exile may target an indestructible creature");
        setup(3); Card token=creature("Colossal Dreadmaw",opp(1)); token.setGamePieceType(forge.card.GamePieceType.TOKEN);
        check(willing() && swine.getTargetCard()==token,"large opposing tokens qualify");
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS,opp(1));
        check(((PlayerControllerAi)ai.getController()).getAi().canPlaySa(swine)!=AiPlayDecision.WillPlay,
                "native sorcery timing remains enforced");
    }
    static long boars(Player p) { return p.getCreaturesInPlay().stream().filter(c->c.isToken() && c.getType().hasSubtype("Boar")).count(); }
    static void resolution() {
        setup(4); Card first=creature("Leatherback Baloth",opp(1)); Card second=creature("Colossal Dreadmaw",opp(2));
        check(willing() && swine.getXManaCostPaid()==2,"native resolution: two targets selected");
        check(ComputerUtil.playStack(swine,ai,game),"native resolution: pay XUU and put original spell on stack");
        check(ai.getLandsInPlay().stream().filter(Card::isTapped).count()==4,"native resolution: exactly four lands tapped for X=2");
        game.getStack().resolveStack();
        check(first.isInZone(ZoneType.Exile) && second.isInZone(ZoneType.Exile),"native resolution: selected creatures exiled");
        check(boars(opp(1))==1 && boars(opp(2))==1 && boars(ai)==0,"native resolution: one Boar per exiled creature to its controller");
        check(opp(1).getCreaturesInPlay().getFirst().getNetPower()==2 && opp(1).getCreaturesInPlay().getFirst().getNetToughness()==2,
                "native resolution: unchanged script produces 2/2 Boars");
        check(!swine.getHostCard().getRemembered().iterator().hasNext(),"native resolution: remembered targets cleaned up");
        setup(4); first=creature("Leatherback Baloth",opp(1)); second=creature("Colossal Dreadmaw",opp(2));
        check(willing() && ComputerUtil.playStack(swine,ai,game),"partial resolution: native spell cast");
        game.getAction().moveToGraveyard(second,null); game.copyLastState();
        game.getStack().resolveStack();
        check(first.isInZone(ZoneType.Exile) && second.isInZone(ZoneType.Graveyard),"partial resolution: only remaining legal target exiled");
        check(boars(opp(1))==1 && boars(opp(2))==0,"partial resolution: no Boar for invalid target");
    }
    static void isolation() {
        setup(3); Card target=creature("Colossal Dreadmaw",opp(1)); long rng=MyRandom.callCount();
        check(SpecialCardAi.BeastWithin.chooseTarget(ai,opp(1).getCreaturesInPlay())==target && MyRandom.callCount()==rng,
                "reused Beast Within ranking consumes no RNG");
        check(willing(),"native mana feasibility permits the ranked target");
        List<SpellAbility> selected=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();
        check(selected!=null && !selected.isEmpty() && selected.get(0).getHostCard().getName().equals("Curse of the Swine"),
                "normal AI selection admits and selects the spell");
        for(String profile:List.of("","abzan-armor","adaptive-enchantment","ahoy-mateys","decree-of-pain")) {
            AiOverrideProfile.setActive(profile);
            check(!CurseOfTheSwineAi.uses(swine) && !AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(swine.getHostCard()),
                    "no Curse override in profile '"+profile+"'");
        }
        AiOverrideProfile.setActive("");
        selected=((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();
        check(selected==null || selected.isEmpty(),"default retains original in-game blocker");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null); MyRandom.setRandom(new Random(7361));
        decisions(); resolution(); isolation(); AiOverrideProfile.setActive(""); System.out.println("DONE "+passed+" Swine checks");
    }
}
