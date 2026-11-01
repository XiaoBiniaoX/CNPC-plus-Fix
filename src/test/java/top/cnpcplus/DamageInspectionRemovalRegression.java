package top.cnpcplus;

import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcplus.ai.DamageInspectionController;
import top.cnpcplus.ai.DamageInspectionGoal;
import top.cnpcplus.mixin.MobGoalSelectorAccess;

/** Executes the production removal path, including callbacks that reenter removal. */
public final class DamageInspectionRemovalRegression {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        try {
            Bootstrap.bootStrap();
        } catch (ExceptionInInitializerError unavailable) {
            Throwable cause = unavailable;
            while (cause.getCause() != null) cause = cause.getCause();
            if (!(cause instanceof NoSuchMethodException)
                    || !cause.getMessage().equals("net.minecraftforge.network.NetworkEvent.<init>()")) throw unavailable;
            System.out.println("HEADLESS_ONLY: untransformed Forge network hook; not a loader smoke test");
        }
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Npc npc = (Npc) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(Npc.class);
        npc.selector = new Selector(npc);
        Field field = DamageInspectionController.class.getDeclaredField("GOALS");
        field.setAccessible(true);
        Map<EntityNPCInterface, DamageInspectionGoal> goals = (Map<EntityNPCInterface, DamageInspectionGoal>) field.get(null);
        DamageInspectionGoal goal = new DamageInspectionGoal(npc);
        goal.request(1, 2, 3);
        npc.selector.addGoal(0, goal);
        goals.put(npc, goal);
        DamageInspectionController.remove(npc);
        check(!goals.containsKey(npc) && !goal.isPending(), "goal not removed before callbacks");
        check(npc.navigationReads == 1 && npc.selector.removals == 1, "reentrant cancellation/removal repeated work");
        check(npc.selector.getAvailableGoals().isEmpty(), "native GoalSelector retained goal");
        DamageInspectionController.remove(npc);
        DamageInspectionController.remove(null);
        check(npc.selector.removals == 1, "idempotent remove touched selector");
        System.out.println("DAMAGE_INSPECTION_REMOVAL_REGRESSION=PASS");
    }

    private static final class Npc extends EntityCustomNpc implements MobGoalSelectorAccess {
        Selector selector;
        int navigationReads;
        private Npc() { super(null, null); }
        @Override public PathNavigation getNavigation() {
            navigationReads++;
            DamageInspectionController.remove(this);
            return null;
        }
        @Override public GoalSelector cnpcplus$getGoalSelector() { return selector; }
        @Override public GoalSelector cnpcplus$getTargetSelector() { return selector; }
    }

    private static final class Selector extends GoalSelector {
        final Npc npc;
        int removals;
        Selector(Npc npc) {
            super(() -> InactiveProfiler.INSTANCE);
            this.npc = npc;
        }
        @Override public void removeGoal(Goal goal) {
            removals++;
            DamageInspectionController.remove(npc);
            super.removeGoal(goal);
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
