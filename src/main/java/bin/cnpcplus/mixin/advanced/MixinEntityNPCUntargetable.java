package bin.cnpcplus.mixin.advanced;

import bin.cnpcplus.common.IUntargetableAccess;
import net.minecraft.entity.EntityLivingBase;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 238-12：拦截所有把「不可仇恨 NPC」设为目标/攻击目标的入口。
 * AI、受击反击、脚本最终都走 setAttackTarget / onAttack。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinEntityNPCUntargetable {

    @Inject(method = "func_70624_b", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void cnpcplus$blockUntargetableTarget(EntityLivingBase target, CallbackInfo ci) {
        if (this.cnpcplus$isBlocked(target)) {
            ci.cancel();
        }
    }

    @Inject(method = "onAttack", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void cnpcplus$blockUntargetableAttack(EntityLivingBase target, CallbackInfo ci) {
        if (this.cnpcplus$isBlocked(target)) {
            ci.cancel();
        }
    }

    @Inject(method = "func_70636_d", at = @At("HEAD"), remap = false, require = 1)
    private void cnpcplus$dropUntargetable(CallbackInfo ci) {
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        if (self.world == null || self.world.isRemote) return;
        if (this.cnpcplus$isBlocked(self.getAttackTarget())) {
            self.setAttackTarget(null);
        }
    }

    @Unique
    private boolean cnpcplus$isBlocked(EntityLivingBase target) {
        if (!(target instanceof EntityNPCInterface)) return false;
        EntityNPCInterface npc = (EntityNPCInterface) target;
        return npc.advanced != null && ((IUntargetableAccess) npc.advanced).cnpcplus$isUntargetable();
    }
}
