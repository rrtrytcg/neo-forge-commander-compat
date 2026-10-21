import forge.GuiDesktop;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.ai.AiController;
import forge.ai.AiOverrideProfile;
import forge.ai.PlayerControllerAi;
import forge.game.spellability.SpellAbility;
import forge.util.AITimeoutTracker;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Deadline integration on the reconciled candidate; normal controls distinguish interruption from pass. */
public class CompatibilityTimeoutTests {
    static int checks;
    static void check(boolean ok,String label) {
        if(!ok) { throw new AssertionError(label); }
        checks++; System.out.println("PASS "+label);
    }
    static AiController controller() { return ((PlayerControllerAi)DarksteelMutationTests.ai.getController()).getAi(); }
    static SpellAbility choose() throws Exception {
        Method m=AiController.class.getDeclaredMethod("chooseSpellAbilityToPlayFromList",List.class,boolean.class);
        m.setAccessible(true);
        return (SpellAbility)m.invoke(controller(),new java.util.ArrayList<>(List.of(DarksteelMutationTests.spell)),false);
    }
    static boolean interrupted() throws Exception {
        Field f=AiController.class.getDeclaredField("choiceEvaluationInterrupted"); f.setAccessible(true);
        return f.getBoolean(controller());
    }
    static long key() { return AITimeoutTracker.key(DarksteelMutationTests.game.getId(),DarksteelMutationTests.ai.getId()); }
    static void setup() { DarksteelMutationTests.setup(); DarksteelMutationTests.permanent("Craw Wurm",1); }
    public static void main(String[] args) throws Exception {
        GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null,null);
        setup(); check(choose()!=null && !interrupted(),"normal useful spell search is not interrupted");
        long oldKey=key(); AITimeoutTracker.begin(oldKey,1L);
        while(System.nanoTime()<=AITimeoutTracker.deadlineNanos(oldKey,1L)) { Thread.onSpinWait(); }
        try {
            check(choose()==null,"expired native search returns no spell");
            check(interrupted(),"deadline expiry is recorded as interruption, never a genuine pass");
        } finally { AITimeoutTracker.end(oldKey); }
        check(!AITimeoutTracker.isActive(oldKey),"expired nested search and outer session close cleanly");
        check(choose()!=null && !interrupted(),"next normal search clears stale interruption state");
        AITimeoutTracker.begin(oldKey,1L);
        try {
            setup(); check(key()!=oldKey,"game rollover uses a different deadline key");
            check(choose()!=null && !interrupted(),"expired previous game does not contaminate next game");
        } finally { AITimeoutTracker.end(oldKey); }
        check(AITimeoutTracker.remainingNanos()==Long.MAX_VALUE,"deadline binding does not leak to game thread");
        AiOverrideProfile.setActive("");
        check(DarksteelMutationTests.selection()==null,"default profile admission remains blocked on new engine");
        System.out.println("DONE "+checks+" timeout integration checks");
    }
}
