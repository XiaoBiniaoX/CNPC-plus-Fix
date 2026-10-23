package top.cnpcplus.mixin;

import net.minecraft.world.entity.Entity;
import noppes.npcs.api.wrapper.EntityWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CNPC 1.20.1 的 IEntity#setBurning(int) 约定参数为秒，但 EntityWrapper 直接把该值
 * 传给 Entity#setRemainingFireTicks（裸 tick），导致 setBurning(5) 只燃烧 5 tick。
 * 这里改为原版 setSecondsOnFire，恢复秒语义并走火焰保护衰减。
 */
@Mixin(value = EntityWrapper.class, remap = false)
public class MixinEntityWrapperSetBurning {
    @Shadow(remap = false) protected Entity entity;

    @Inject(method = "setBurning", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$setBurningSeconds(int seconds, CallbackInfo ci) {
        if (this.entity != null) {
            this.entity.setSecondsOnFire(seconds);
        }
        ci.cancel();
    }
}
