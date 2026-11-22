package top.cnpcplus.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Mob.class)
public interface MobPursuitTargetAccess {
    @Accessor("target") void cnpcplus$setRawTarget(LivingEntity target);
}
