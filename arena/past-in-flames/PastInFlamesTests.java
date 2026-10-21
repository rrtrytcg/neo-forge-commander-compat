import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.*;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;

/** Opt-in Past in Flames graveyard-value, native casting and flashback fixtures. */
public class PastInFlamesTests {
    static Game game; static Player ai; static int passed;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);passed++;System.out.println("PASS "+label);}
    static Player p(int i){return game.getPlayers().get(i);}
    static Card add(String name,Player owner,ZoneType zone){Card c=Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name),owner);c.setGameTimestamp(game.getNextTimestamp());owner.getZone(zone).add(c);c.setSickness(false);return c;}
    static void refresh(){game.getAction().checkStaticAbilities();game.getTriggerHandler().resetActiveTriggers();game.copyLastState();}
    static void setup(String profile){
        AiOverrideProfile.setActive(profile);List<RegisteredPlayer> regs=new ArrayList<>();for(int i=0;i<2;i++){LobbyPlayerAi lobby=new LobbyPlayerAi("Past in Flames fixture "+i,null);lobby.setAiProfile("Default");regs.add(new RegisteredPlayer(new Deck()).setPlayer(lobby));}
        GameRules rules=new GameRules(GameType.Constructed);game=new Game(regs,rules,new Match(rules,regs,"Past in Flames native fixtures"));game.setAge(GameStage.Play);ai=p(0);game.getPhaseHandler().devModeSet(PhaseType.MAIN1,ai);for(Player player:game.getPlayers())for(int i=0;i<12;i++)add("Island",player,ZoneType.Library);refresh();
    }
    static Card grave(String name){return add(name,ai,ZoneType.Graveyard);}
    static void mana(boolean blue){for(int i=0;i<4;i++)add("Mountain",ai,ZoneType.Battlefield);if(blue)add("Island",ai,ZoneType.Battlefield);refresh();}
    static boolean willing(SpellAbility sa){return SpellApiToAi.Converter.get(sa).canPlayWithSubs(ai,sa).willingToPlay();}
    static List<SpellAbility> choices(){return ((PlayerControllerAi)ai.getController()).getAi().chooseSpellAbilityToPlay();}
    static boolean contains(List<SpellAbility> abilities,Card card){return abilities!=null&&abilities.stream().anyMatch(sa->sa.getHostCard()==card);}
    static SpellAbility flashback(Card card){return ComputerUtilAbility.getSpellAbilities(new CardCollection(List.of(card)),ai).stream().filter(SpellAbility::isFlashback).findFirst().orElse(null);}
    static void profileAndValueGate(){
        setup("past-in-flames");Card pif=add("Past in Flames",ai,ZoneType.Hand);mana(true);grave("Opt");grave("Lightning Bolt");SpellAbility cast=pif.getFirstSpellAbility();cast.setActivatingPlayer(ai);
        check(AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(pif),"opt-in profile admits Past in Flames");check("PastInFlames".equals(AiOverrideProfile.get().getAiLogic(pif)),"opt-in maps the card to its narrow graveyard-value rule");
        check(willing(cast),"native PumpAll hook accepts two graveyard spells with exact combined mana affordability");
        List<SpellAbility> selected=choices();check(contains(selected,pif),"normal AI selection can choose Past in Flames with eligible graveyard value");
    }
    static void holdConditionsAndIsolation(){
        setup("past-in-flames");Card pif=add("Past in Flames",ai,ZoneType.Hand);mana(true);grave("Opt");SpellAbility cast=pif.getFirstSpellAbility();cast.setActivatingPlayer(ai);check(!willing(cast),"holds with only one graveyard instant or sorcery");
        setup("past-in-flames");pif=add("Past in Flames",ai,ZoneType.Hand);mana(false);grave("Opt");grave("Lightning Bolt");cast=pif.getFirstSpellAbility();cast.setActivatingPlayer(ai);check(!willing(cast),"holds when available mana cannot pay Past in Flames plus a colored flashback cost");
        setup("past-in-flames");pif=add("Past in Flames",ai,ZoneType.Hand);for(int i=0;i<5;i++)add("Mountain",ai,ZoneType.Battlefield);grave("Lightning Bolt");grave("Opt");refresh();cast=pif.getFirstSpellAbility();cast.setActivatingPlayer(ai);check(willing(cast),"finds an affordable spell even when another graveyard card needs a missing color");
        setup("past-in-flames");pif=add("Past in Flames",ai,ZoneType.Hand);mana(true);grave("Opt");grave("Lightning Bolt");cast=pif.getFirstSpellAbility();cast.setActivatingPlayer(ai);game.getPhaseHandler().devModeSet(PhaseType.COMBAT_BEGIN,ai);check(!willing(cast),"holds outside our main phases");
        setup("past-in-flames");pif=add("Past in Flames",ai,ZoneType.Hand);mana(true);grave("Opt");grave("Lightning Bolt");cast=pif.getFirstSpellAbility();cast.setActivatingPlayer(ai);Card pending=add("Opt",p(1),ZoneType.Hand);SpellAbility pendingCast=pending.getFirstSpellAbility();pendingCast.setActivatingPlayer(p(1));pendingCast.setHostCard(game.getAction().moveToStack(pending,pendingCast));pendingCast.getHostCard().setCastSA(pendingCast);game.getStack().add(pendingCast);check(!willing(cast),"holds while another spell is on the stack");
        for(String profile:List.of("","odric")){setup(profile);pif=add("Past in Flames",ai,ZoneType.Hand);check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(pif),"profile '"+profile+"' does not admit Past in Flames");check(AiOverrideProfile.get().getAiLogic(pif).isEmpty(),"profile '"+profile+"' leaves Past in Flames native AI logic unchanged");}
    }
    static void nativeFlashbackResolution(){
        setup("past-in-flames");Card pif=add("Past in Flames",ai,ZoneType.Hand);Card opt=grave("Opt");grave("Lightning Bolt");mana(true);SpellAbility cast=pif.getFirstSpellAbility();cast.setActivatingPlayer(ai);
        check(ComputerUtil.playStack(cast,ai,game),"native payment casts Past in Flames");game.getStack().resolveStack();refresh();
        SpellAbility flash=flashback(opt);check(flash!=null,"native effect grants Opt its flashback ability");
        check(flash!=null&&ComputerUtil.playStack(flash,ai,game),"native payment casts the granted Opt flashback");game.getStack().resolveStack();refresh();
        Card resolvedOpt=game.getCardState(opt,null);check(resolvedOpt!=null&&resolvedOpt.isInZone(ZoneType.Exile),"native flashback resolution exiles Opt");check(ai.getCardsIn(ZoneType.Hand).size()==1,"flashback Opt draws through its original resolution");
    }
    static void baseline(){setup("");Card pif=add("Past in Flames",ai,ZoneType.Hand);mana(true);grave("Opt");grave("Lightning Bolt");SpellAbility cast=pif.getFirstSpellAbility();cast.setActivatingPlayer(ai);check(!AiOverrideProfile.get().ignoreRemoveDeckDuringPlay(pif),"native baseline keeps Past in Flames blocked from normal play");check(!willing(cast),"native PumpAll evaluation declines the graveyard flashback payoff");check(!contains(choices(),pif),"native baseline excludes Past in Flames from normal selection");}
    public static void main(String[] args){GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);MyRandom.setRandom(new Random(20261021));if(args.length>0&&"baseline".equals(args[0]))baseline();else{profileAndValueGate();holdConditionsAndIsolation();nativeFlashbackResolution();}AiOverrideProfile.setActive("");System.out.println("DONE "+passed+" Past in Flames checks");}
}
