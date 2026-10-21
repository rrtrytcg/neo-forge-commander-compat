import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.game.zone.ZoneType;
public class NyxWeaverBaselineTests extends DarksteelMutationTests {
 public static void main(String[]args){GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);setup();ai.getZone(ZoneType.Hand).remove(aura);aura=add("Nyx Weaver",ai,ZoneType.Hand);spell=aura.getFirstSpellAbility();spell.setActivatingPlayer(ai);AiOverrideProfile.setActive("default");refresh();check(ComputerUtilCard.isCardRemAIDeck(aura),"native script retains RemoveDeck warning");check(selection()==null,"native controller excludes blocked Nyx spell");ai.getZone(ZoneType.Hand).remove(aura);aura=permanent("Nyx Weaver",0);var cheap=add("Grizzly Bears",ai,ZoneType.Graveyard);refresh();var ability=aura.getSpellAbilities().stream().filter(s->!s.isSpell()).findFirst().orElseThrow();ability.setActivatingPlayer(ai);check(SpellApiToAi.Converter.get(ability).canPlayWithSubs(ai,ability).willingToPlay()&&ability.getTargetCard()==cheap,"forced generic recovery trades healthy Weaver for cheap creature");
 for(var sa:aura.getSpellAbilities())System.out.println("NATIVE_SHAPE "+sa.isSpell()+" "+sa.getMapParams()+" COST "+sa.getPayCosts()+" mana="+sa.getPayCosts().getTotalMana());
 for(var t:aura.getTriggers())System.out.println("NATIVE_TRIGGER "+t.getMapParams()+" EFFECT "+t.ensureAbility().getMapParams());System.out.println("DONE "+passed+" baseline checks");}
}
