package top.cnpcplus.mixin;

import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataAdvanced;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.roles.JobTestProfession;
import top.cnpcplus.roles.RoleVillagerTrader;

/**
 * 在 DataAdvanced.setRole / setJob 的 HEAD 拦截自定义 type ID，
 * 赶在原版的 {@code role -= 2; %= 8} / {@code job %= 12} 篡改之前。
 */
@Mixin(value = DataAdvanced.class, remap = false)
public class MixinDataAdvancedCustomRoleJob {

    @Shadow
    private EntityNPCInterface npc;

    @Inject(method = "setRole", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$setCustomRole(int role, CallbackInfo ci) {
        if (role == 8) {
            if (!(this.npc.role instanceof RoleVillagerTrader)) {
                this.npc.role = new RoleVillagerTrader(this.npc);
            }
            ci.cancel();
        }
    }

    @Inject(method = "setJob", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$setCustomJob(int job, CallbackInfo ci) {
        if (job == 12) {
            if (!this.npc.level().isClientSide) {
                this.npc.job.reset();
            }
            if (!(this.npc.job instanceof JobTestProfession)) {
                this.npc.job = new JobTestProfession(this.npc);
            }
            ci.cancel();
        }
    }
}
