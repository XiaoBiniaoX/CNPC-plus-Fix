package bin.cnpcplus.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class UndeadRevampedTickMixin {

    @Shadow(remap = false) protected abstract void tickDeath();

    @Unique
    private static boolean cnpcplus$isUndeadRevamped(LivingEntity entity) {
        return entity.getClass().getName().startsWith("net.mcreator.undeadrevamp");
    }

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void cnpcplus$forceTickDeath(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!cnpcplus$isUndeadRevamped(self)) return;

        if (self.deathTime > 0 && self.getHealth() > 0.0F) {
            this.tickDeath();
        }
    }
}