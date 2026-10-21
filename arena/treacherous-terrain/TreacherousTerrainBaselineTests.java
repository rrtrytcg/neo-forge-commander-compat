import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.*;
import forge.game.zone.ZoneType;
public class TreacherousTerrainBaselineTests extends DarksteelMutationTests {
 public static void main(String[] args){GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);setup();ai.getZone(ZoneType.Hand).remove(aura);aura=add("Treacherous Terrain",ai,ZoneType.Hand);spell=aura.getFirstSpellAbility();spell.setActivatingPlayer(ai);AiOverrideProfile.setActive("default");refresh();
 check(ComputerUtilCard.isCardRemAIDeck(aura),"original default AI block retained");check(selection()==null,"native controller excludes blocked Terrain");
 check(SpellApiToAi.Converter.get(spell).canPlayWithSubs(ai,spell).willingToPlay(),"forced native RepeatEach approves even zero opposing lands and insufficient mana");
 for(var sa:aura.getSpellAbilities()){sa.setActivatingPlayer(ai);System.out.println("NATIVE_SHAPE "+sa.isSpell()+" "+sa.getMapParams()+" COST "+sa.getPayCosts()+" mana="+sa.getPayCosts().getTotalMana());}
 System.out.println("DONE "+passed+" baseline checks");}
}
