package top.cnpcplus.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Mob.class, remap = false)
public interface MobGoalSelectorAccess {
    @Accessor("goalSelector")
    GoalSelector cnpcplus$getGoalSelector();

    @Accessor("targetSelector")
    GoalSelector cnpcplus$getTargetSelector();
}
