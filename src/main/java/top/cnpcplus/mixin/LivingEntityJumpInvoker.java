package top.cnpcplus.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 暴露 vanilla protected jumpFromGround 给 CNPC 骑乘跳跃使用。 */
@Mixin(LivingEntity.class)
public interface LivingEntityJumpInvoker {
    @Invoker("jumpFromGround")
    void cnpcplus$jumpFromGround();
}
