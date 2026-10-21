import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.ability.*;
import forge.game.card.*;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

class AhoyMateysTests {
    static Game game;
    static Player ai;
    static int passed;
    static Card add(String name, Player owner, ZoneType zone) {
        Card card = Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name), owner);
        card.setGameTimestamp(game.getNextTimestamp());
        owner.getZone(zone).add(card);
        return card;
    }
    static void check(boolean value, String label) {
        if (!value) { throw new AssertionError(label); }
        passed++;
        System.out.println("PASS " + label);
    }
    static void newGame() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            LobbyPlayerAi lobby = new LobbyPlayerAi("Ahoy-" + i, null);
            lobby.setAiProfile("Default");
            players.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Constructed);
        game = new Game(players, rules, new Match(rules, players, "Ahoy native fixtures"));
        game.setAge(GameStage.Play);
        ai = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        for (Player player : game.getPlayers()) { library(player, 12); }
        AiOverrideProfile.setActive("ahoy-mateys");
    }
    static void library(Player player, int count) {
        player.getZone(ZoneType.Library).removeAllCards(true);
        for (int i = 0; i < count; i++) { add("Island", player, ZoneType.Library); }
    }
    static Player opp(int i) { return game.getPlayers().get(i); }
    static void refresh() {
        game.getAction().checkStaticAbilities();
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
    }
    static SpellAbility spell(String name) {
        Card card = add(name, ai, ZoneType.Hand);
        SpellAbility sa = card.getFirstSpellAbility();
        sa.setActivatingPlayer(ai);
        refresh();
        return sa;
    }
    static boolean willing(SpellAbility sa) {
        return SpellApiToAi.Converter.get(sa).canPlayWithSubs(ai, sa).willingToPlay();
    }
    static void lands(int battlefield, int hand) {
        for (int i = 0; i < battlefield; i++) { add("Mountain", ai, ZoneType.Battlefield); }
        for (int i = 0; i < hand; i++) { add("Swamp", ai, ZoneType.Hand); }
    }
    static void looting() {
        newGame(); lands(4, 3);
        SpellAbility loot = spell("Faithless Looting");
        check(!willing(loot), "Looting: one excess land is insufficient");
        add("Swamp", ai, ZoneType.Hand);
        check(willing(loot), "Looting: two excess lands allow native Main1 casting");
        library(ai, 1);
        check(!willing(loot), "Looting: two-card draw cannot exceed library");
        library(ai, 2);
        check(willing(loot), "Looting: exactly two library cards are safe");
        newGame(); lands(2, 4); loot=spell("Faithless Looting");
        check(!willing(loot), "Looting: retain enough lands to reach five plus one spare");
        add("Swamp", ai, ZoneType.Hand); add("Swamp", ai, ZoneType.Hand);
        check(willing(loot), "Looting: early-game threshold permits only true excess");
        newGame(); lands(4, 3); loot=spell("Faithless Looting");
        Card pirate=add("Angrath's Marauders", ai, ZoneType.Hand);
        check(!willing(loot), "Looting: uncastable Pirate needs available Admiral");
        Card admiral=add("Admiral Brass, Unsinkable", ai, ZoneType.Command);
        check(willing(loot), "Looting: excess land plus uncastable Pirate with Admiral in command");
        CardCollection preferred=AhoyMateysAi.lootingDiscards(ai, ai.getCardsIn(ZoneType.Hand), loot, 2);
        check(preferred.size()==2 && preferred.get(0).isLand() && preferred.get(1)==pirate,
                "Looting: excess land precedes reanimation Pirate");
        ai.getZone(ZoneType.Command).remove(admiral);
        ai.getZone(ZoneType.Graveyard).add(admiral);
        check(!willing(loot), "Looting: Admiral in graveyard does not satisfy availability");
        newGame(); lands(4, 2); loot=spell("Faithless Looting");
        add("Admiral Brass, Unsinkable",ai,ZoneType.Battlefield);
        add("Angrath's Marauders",ai,ZoneType.Hand); add("Port Razer",ai,ZoneType.Hand);
        check(!willing(loot), "Looting: two Pirates supply at most one preferred discard");
        newGame(); lands(4, 3); loot=spell("Faithless Looting");
        add("Admiral Brass, Unsinkable",ai,ZoneType.Command);
        add("Captain Lannery Storm",ai,ZoneType.Hand);
        check(!willing(loot), "Looting: affordable Pirate is not a reanimation discard candidate");
        newGame(); lands(4, 3); loot=spell("Faithless Looting");
        add("Admiral Brass, Unsinkable",ai,ZoneType.Command);
        pirate=add("Angrath's Marauders",ai,ZoneType.Hand);
        add("Port Razer",ai,ZoneType.Hand);
        check(willing(loot), "Looting: prepared hand can cast");
        SpellAbility discard=loot.findSubAbilityByType(ApiType.Discard);
        CardCollection actual=((PlayerControllerAi)ai.getController()).getAi().getCardsToDiscard(2,2,
                new CardCollection(ai.getCardsIn(ZoneType.Hand)),discard);
        check(actual.size()==2 && actual.contains(pirate), "Looting: controller selects highest-value uncastable Pirate");
        int before=ai.getCardsIn(ZoneType.Graveyard).size();
        ai.getZone(ZoneType.Library).removeAllCards(true);
        add("Counterspell",ai,ZoneType.Library); add("Counterspell",ai,ZoneType.Library);
        game.getAction().moveToStack(loot.getHostCard(),loot);
        AbilityUtils.resolve(loot);
        check(ai.getCardsIn(ZoneType.Graveyard).size()==before+2 && pirate.isInZone(ZoneType.Graveyard),
                "Looting: native draw/discard resolution stocks Pirate graveyard");
        Card fieldAdmiral=add("Admiral Brass, Unsinkable",ai,ZoneType.Battlefield);
        SpellAbility returnPirate=fieldAdmiral.getTriggers().stream()
                .filter(tr->tr.getMode()==forge.game.trigger.TriggerType.Phase).findFirst().orElseThrow().ensureAbility();
        returnPirate.setActivatingPlayer(ai); returnPirate.getTargets().add(game.getCardState(pirate,null));
        refresh(); AbilityUtils.resolve(returnPirate);
        Card returned=game.getCardState(pirate,null);
        check(returned.isInZone(ZoneType.Battlefield) && returned.getCounters(CounterEnumType.FINALITY)==1,
                "Looting: discarded Pirate is a legal native Admiral reanimation with finality");
        newGame(); lands(4,3); loot=spell("Faithless Looting");
        add("Admiral Brass, Unsinkable",ai,ZoneType.Command);
        pirate=add("Angrath's Marauders",ai,ZoneType.Hand);
        check(willing(loot), "Looting: initial land-plus-Pirate candidate plan passes");
        game.getAction().moveToStack(loot.getHostCard(),loot); AbilityUtils.resolve(loot);
        check(pirate.isInZone(ZoneType.Hand)
                && ai.getCardsIn(ZoneType.Graveyard).stream().filter(Card::isLand).count()==2,
                "Looting: drawn lands are reassessed and discarded before the Pirate");
        // Re-evaluation after drawing can require fallback even if the initial plan was different.
        newGame(); loot=spell("Faithless Looting");
        game.getAction().moveToStack(loot.getHostCard(),loot);
        add("Sol Ring",ai,ZoneType.Hand); add("Chaos Warp",ai,ZoneType.Hand); add("Blasphemous Act",ai,ZoneType.Hand);
        actual=((PlayerControllerAi)ai.getController()).getAi().getCardsToDiscard(2,2,
                new CardCollection(ai.getCardsIn(ZoneType.Hand)),loot.findSubAbilityByType(ApiType.Discard));
        check(actual.size()==2, "Looting: native fallback completes mandatory discards");
        newGame(); lands(5,3);
        Card flashCard=add("Faithless Looting",ai,ZoneType.Graveyard);
        SpellAbility flash=flashCard.getAllPossibleAbilities(ai,false).stream().filter(SpellAbility::isFlashback).findFirst().orElseThrow();
        flash.setActivatingPlayer(ai);
        check(willing(flash), "Looting: native flashback uses the same candidate gate");
        add("Alhammarret's Archive",ai,ZoneType.Battlefield); refresh();
        check(!willing(flash), "Looting: unknown draw multiplier holds rather than guessing decking safety");
    }
    static void hand(Player player,int count) {
        for(int i=0;i<count;i++) { add("Mountain",player,ZoneType.Hand); }
    }
    static void windfall() {
        newGame(); hand(ai,2); hand(opp(1),4);
        SpellAbility wind=spell("Windfall");
        check(AhoyMateysAi.windfallDraw(ai,wind)==4 && willing(wind), "Windfall: two remaining cards and draw four pass");
        add("Island",ai,ZoneType.Hand);
        check(!willing(wind), "Windfall: three remaining cards hold");
        newGame(); hand(ai,2); hand(opp(1),3); wind=spell("Windfall");
        check(!willing(wind), "Windfall: draw three is below threshold");
        hand(opp(2),6);
        check(AhoyMateysAi.windfallDraw(ai,wind)==6 && willing(wind), "Windfall: largest opponent hand determines draw");
        library(ai,5);
        check(!willing(wind), "Windfall: avoid drawing beyond library");
        library(ai,6);
        check(willing(wind), "Windfall: exactly enough library cards pass");
        add("Tamiyo, Collector of Tales",opp(2),ZoneType.Battlefield); refresh();
        check(AhoyMateysAi.windfallDraw(ai,wind)==3 && !willing(wind), "Windfall: discard-protected largest hand contributes zero");
        newGame(); hand(ai,1); hand(opp(1),4); hand(opp(2),2); wind=spell("Windfall");
        check(willing(wind), "Windfall: root and draw drawback agree before discarding");
        game.getAction().moveToStack(wind.getHostCard(),wind);
        AbilityUtils.resolve(wind);
        check(game.getPlayers().stream().allMatch(p->p.getCardsIn(ZoneType.Hand).size()==4),
                "Windfall: native resolution draws the actual maximum for every player");
        check(wind.getHostCard().getRemembered().iterator().hasNext() == false, "Windfall: native cleanup clears remembered discards");
    }
    static SpellAbility charm() { return spell("Rakdos Charm"); }
    static ApiType mode(SpellAbility charm) {
        return charm.getChosenList()==null ? null : charm.getChosenList().get(0).getApi();
    }
    static void creatures(Player player,int count) {
        for(int i=0;i<count;i++) { add("Grizzly Bears",player,ZoneType.Battlefield); }
    }
    static void charmModes() {
        newGame(); SpellAbility sa=charm();
        check(!willing(sa) && sa.getChosenList()==null, "Charm: empty board and graveyards hold");
        Card ring=add("Sol Ring",opp(1),ZoneType.Battlefield); refresh();
        check(willing(sa) && mode(sa)==ApiType.Destroy && sa.getSubAbility().getTargetCard()==ring,
                "Charm: native mode chooses opposing artifact");
        Card horn=add("Vanquisher's Banner",opp(2),ZoneType.Battlefield); refresh();
        check(willing(sa) && sa.getSubAbility().getTargetCard()==horn, "Charm: native artifact evaluation selects highest value");
        AbilityUtils.resolve(sa);
        check(horn.isInZone(ZoneType.Graveyard) && ring.isInZone(ZoneType.Battlefield), "Charm: native resolution destroys selected artifact only");
        newGame(); add("Darksteel Ingot",opp(1),ZoneType.Battlefield);
        Card token=add("Sol Ring",opp(1),ZoneType.Battlefield); token.setGamePieceType(forge.card.GamePieceType.TOKEN);
        add("Sol Ring",ai,ZoneType.Battlefield); sa=charm();
        check(!willing(sa), "Charm: skip own, token, and indestructible artifacts");
        newGame();
        for(int i=0;i<3;i++) { add("Counterspell",opp(1),ZoneType.Graveyard); }
        sa=charm();
        check(!willing(sa), "Charm: three nonland graveyard cards are insufficient");
        add("Counterspell",opp(1),ZoneType.Graveyard);
        for(int i=0;i<10;i++) { add("Mountain",opp(2),ZoneType.Graveyard); }
        refresh();
        check(willing(sa) && mode(sa)==ApiType.ChangeZoneAll && sa.getSubAbility().getTargets().getFirstTargetedPlayer()==opp(1),
                "Charm: four nonlands qualify; land-only graveyard does not");
        for(int i=0;i<5;i++) { add("Grizzly Bears",opp(2),ZoneType.Graveyard); }
        refresh();
        check(willing(sa) && sa.getSubAbility().getTargets().getFirstTargetedPlayer()==opp(2), "Charm: proactive exile selects largest qualifying graveyard");
        AbilityUtils.resolve(sa);
        check(opp(2).getCardsIn(ZoneType.Graveyard).isEmpty() && opp(1).getCardsIn(ZoneType.Graveyard).size()==4,
                "Charm: native graveyard exile affects the selected opponent");
        newGame();
        for(int i=0;i<6;i++) { add("Angrath's Marauders",ai,ZoneType.Graveyard); }
        sa=charm();
        check(!willing(sa), "Charm: never exile own Pirate graveyard");
        newGame();
        Card target=add("Grizzly Bears",opp(1),ZoneType.Graveyard);
        Card reanimator=add("Reanimate",opp(1),ZoneType.Hand);
        SpellAbility reanimate=reanimator.getFirstSpellAbility(); reanimate.setActivatingPlayer(opp(1)); reanimate.getTargets().add(target);
        game.getStack().add(reanimate);
        ring=add("Sol Ring",opp(2),ZoneType.Battlefield);
        sa=charm();
        check(willing(sa) && mode(sa)==ApiType.ChangeZoneAll && sa.getSubAbility().getTargets().getFirstTargetedPlayer()==opp(1),
                "Charm: pending graveyard use outranks artifact destruction below proactive threshold");
        newGame(); add("Grizzly Bears",opp(1),ZoneType.Graveyard);
        Card living=add("Living Death",opp(2),ZoneType.Hand);
        SpellAbility mass=living.getFirstSpellAbility(); mass.setActivatingPlayer(opp(2));
        game.getStack().add(mass); sa=charm();
        check(willing(sa) && mode(sa)==ApiType.ChangeZoneAll && sa.getSubAbility().getTargets().getFirstTargetedPlayer()==opp(1),
                "Charm: public mass recursion through exile also counts as pending graveyard use");
        // A pending spell using our own graveyard must not authorize exiling it.
        newGame(); target=add("Grizzly Bears",ai,ZoneType.Graveyard);
        reanimator=add("Reanimate",opp(1),ZoneType.Hand);
        reanimate=reanimator.getFirstSpellAbility(); reanimate.setActivatingPlayer(opp(1)); reanimate.getTargets().add(target);
        game.getStack().add(reanimate); sa=charm();
        check(!willing(sa), "Charm: reactive exile preserves our graveyard too");
    }
    static void damage() {
        newGame(); creatures(ai,2); creatures(opp(1),3); opp(1).setLife(3,null);
        SpellAbility sa=charm(); long rng=MyRandom.callCount();
        check(willing(sa) && mode(sa)==ApiType.RepeatEach, "Charm damage: lethal opponent and surviving self choose damage");
        check(MyRandom.callCount()==rng, "Charm mode selection consumes no RNG");
        int ours=ai.getLife();
        AbilityUtils.resolve(sa);
        check(ai.getLife()==ours-2 && opp(1).getLife()==0, "Charm damage: native resolution matches separate creature-source damage");
        newGame(); creatures(ai,3); creatures(opp(1),3); ai.setLife(3,null); opp(1).setLife(3,null); sa=charm();
        check(!willing(sa), "Charm damage: reject simultaneous self-lethal even if opponent dies");
        newGame(); creatures(ai,2); add("Angrath's Marauders",ai,ZoneType.Battlefield);
        creatures(opp(1),2); ai.setLife(5,null); opp(1).setLife(2,null); sa=charm();
        check(AhoyMateysAi.charmDamage(ai,sa)==6 && !willing(sa), "Charm damage: Marauders doubles our creatures' damage to ourselves");
        ai.setLife(7,null); refresh();
        check(willing(sa) && mode(sa)==ApiType.RepeatEach, "Charm damage: doubled own damage is safe above lethal boundary");
        AbilityUtils.resolve(sa);
        check(ai.getLife()==1 && opp(1).getLife()==0, "Charm damage: native Marauders resolution matches projection");
        newGame(); creatures(ai,1); creatures(opp(1),1); add("Angrath's Marauders",opp(1),ZoneType.Battlefield);
        opp(1).setLife(4,null); sa=charm();
        check(AhoyMateysAi.charmDamage(ai,sa)==1 && AhoyMateysAi.charmDamage(opp(1),sa)==4 && willing(sa),
                "Charm damage: opponent-owned Marauders doubles only that opponent's sources");
        newGame(); creatures(ai,3); ai.setLife(1,null); creatures(opp(1),2); opp(1).setLife(2,null);
        add("Glacial Chasm",ai,ZoneType.Battlefield); sa=charm();
        check(AhoyMateysAi.charmDamage(ai,sa)==0 && willing(sa), "Charm damage: native full prevention protects us");
        AbilityUtils.resolve(sa);
        check(ai.getLife()==1 && opp(1).getLife()==0, "Charm damage: native prevention resolution matches projection");
        newGame(); creatures(ai,1); ai.setLife(1,null); creatures(opp(1),2); opp(1).setLife(2,null);
        add("Glacial Chasm",ai,ZoneType.Battlefield); add("Everlasting Torment",ai,ZoneType.Battlefield); sa=charm();
        check(AhoyMateysAi.charmDamage(ai,sa)==1 && !willing(sa),
                "Charm damage: prevention ban does not falsely protect us from lethal");
        AbilitySub forcedDamage=forge.game.ability.effects.CharmEffect.makePossibleOptions(sa).stream()
                .filter(m->m.getApi()==ApiType.RepeatEach).findFirst().orElseThrow();
        forcedDamage.setActivatingPlayer(ai); AbilityUtils.resolve(forcedDamage);
        check(ai.getLife()==0, "Charm damage: native prevention-ban resolution confirms self-lethal");
        newGame(); creatures(opp(1),2); opp(1).setLife(2,null); add("Glacial Chasm",opp(1),ZoneType.Battlefield); sa=charm();
        check(!willing(sa), "Charm damage: opponent full prevention prevents false lethal");
        newGame(); creatures(opp(1),2); add("Platinum Angel",opp(1),ZoneType.Battlefield); opp(1).setLife(1,null); sa=charm();
        check(mode(sa)!=ApiType.RepeatEach && (!willing(sa) || mode(sa)!=ApiType.RepeatEach), "Charm damage: cannot-lose protection prevents claimed kill");
        newGame(); creatures(opp(1),2); opp(1).setLife(2,null); add("Furnace of Rath",ai,ZoneType.Battlefield); sa=charm();
        check(AhoyMateysAi.charmDamage(opp(1),sa)==4 && willing(sa), "Charm damage: structural doubling also handles native global doubler");
        newGame(); creatures(opp(1),3); opp(1).setLife(3,null); add("Gisela, Blade of Goldnight",ai,ZoneType.Battlefield); sa=charm();
        check(AhoyMateysAi.charmDamage(ai,sa)==-1 && !willing(sa), "Charm damage: unmodelled partial prevention conservatively holds");
        newGame(); add("Vampire Nighthawk",opp(1),ZoneType.Battlefield); opp(1).setLife(1,null); sa=charm();
        check(!willing(sa), "Charm damage: lifelink cannot create a false lethal");
        Card ring=add("Sol Ring",opp(2),ZoneType.Battlefield); refresh();
        check(willing(sa) && mode(sa)==ApiType.Destroy, "Charm: unknown damage still permits safe artifact fallback");
    }
    static void isolation() {
        newGame();
        for(String name:List.of("Faithless Looting","Rakdos Charm","Windfall")) {
            SpellAbility sa=spell(name);
            check(AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(sa.getHostCard()),name+": Ahoy admission enabled");
            for(String profile:List.of("","adaptive-enchantment","abzan-armor")) {
                AiOverrideProfile.setActive(profile);
                check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(sa.getHostCard())
                        && AiOverrideProfile.get().getAiLogic(sa.getHostCard()).isEmpty(),name+": no override in profile '"+profile+"'");
            }
            AiOverrideProfile.setActive("ahoy-mateys");
        }
        newGame(); lands(4,4); SpellAbility loot=spell("Faithless Looting");
        check(willing(loot),"Ahoy permits eligible Main1 Looting");
        AiOverrideProfile.setActive("");
        check(!willing(loot),"no profile retains upstream Main2 timing preference");
        AiOverrideProfile.setActive("ahoy-mateys");
        check(!AiOverrideProfile.get().supportsLandManaAuras(),"Ahoy does not enable Adaptive Aura family");
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null); MyRandom.setRandom(new Random(7341));
        looting(); windfall(); charmModes(); damage(); isolation();
        AiOverrideProfile.setActive(""); System.out.println("DONE "+passed+" Ahoy checks");
    }
}
