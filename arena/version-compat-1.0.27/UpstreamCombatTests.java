import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.ComputerUtilCombat;
import forge.game.card.Card;
import forge.game.combat.Combat;

/** Native new-engine regressions: preserve the upstream commander damage/blocker fix. */
public class UpstreamCombatTests {
    static int checks;
    static void check(boolean ok,String label) {
        if(!ok)throw new AssertionError(label);
        checks++;System.out.println("PASS "+label);
    }
    static Combat attack(Card c,int defender) {
        Combat combat=new Combat(c.getController());
        combat.addAttacker(c,DarksteelMutationTests.p(defender));
        return combat;
    }
    static boolean danger(Combat combat) {
        return !ComputerUtilCombat.getLifeThreateningCommanders(DarksteelMutationTests.ai,combat).isEmpty();
    }
    public static void main(String[] args) {
        GuiBase.setInterface(new GuiDesktop());FModel.initialize(null,null);
        DarksteelMutationTests.setup();
        Card commander=DarksteelMutationTests.permanent("Grizzly Bears",1);commander.setCommander(true);
        DarksteelMutationTests.ai.addCommanderDamage(commander,19);
        Combat combat=attack(commander,0);
        check(danger(combat),"unblocked commander reaches 21");
        combat.addBlocker(commander,DarksteelMutationTests.permanent("Grizzly Bears",0));
        check(!danger(combat),"blocked nontrampler no longer falsely threatens commander lethal");
        check(!danger(attack(commander,2)),"commander attacking another opponent is irrelevant");
        DarksteelMutationTests.setup();
        commander=DarksteelMutationTests.permanent("Colossal Dreadmaw",1);commander.setCommander(true);
        DarksteelMutationTests.ai.addCommanderDamage(commander,17);combat=attack(commander,0);
        check(danger(combat),"unblocked trampling commander is lethal");
        combat.addBlocker(commander,DarksteelMutationTests.permanent("Grizzly Bears",0));
        check(danger(combat),"trample remainder after one blocker reaches 21");
        combat.addBlocker(commander,DarksteelMutationTests.permanent("Grizzly Bears",0));
        check(!danger(combat),"multiple blockers absorb enough trample commander damage");
        DarksteelMutationTests.ai.addCommanderDamage(commander,4);
        check(!danger(attack(commander,0)),"already recorded 21 does not invent another preventive block");
        System.out.println("DONE "+checks+" new upstream combat checks");
    }
}
