package top.cnpcplus.mixin;

import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.ai.UntargetableAccess;

/**
 * 独立于阵营逻辑的索敌保护：所有 NPC 的目标最终都会经过 EntityNPCInterface#setTarget，
 * 在这里拒绝“开启了不会被索敌”的 NPC 作为目标，覆盖 AI、受击反击、脚本等全部入口。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinEntityNPCUntargetable {
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$blockUntargetable(LivingEntity target, CallbackInfo ci) {
        if (this.cnpcplus$isBlocked(target)) {
            ci.cancel();
        }
    }

    @Inject(method = "onAttack", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$blockUntargetableOnAttack(LivingEntity target, CallbackInfo ci) {
        if (this.cnpcplus$isBlocked(target)) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void cnpcplus$dropUntargetable(CallbackInfo ci) {
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        if (self.level().isClientSide) return;
        if (this.cnpcplus$isBlocked(self.getTarget())) {
            self.setTarget(null);
        }
    }

    @Unique
    private boolean cnpcplus$isBlocked(LivingEntity target) {
        return target instanceof EntityNPCInterface targetNpc && targetNpc.advanced != null
                && ((UntargetableAccess) targetNpc.advanced).cnpcplus$isUntargetable();
    }
}
