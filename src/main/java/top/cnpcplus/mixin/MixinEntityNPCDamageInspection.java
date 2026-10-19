package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.ai.DamageInspectionController;

@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinEntityNPCDamageInspection {
    @Inject(method = "updateTasks", at = @At("RETURN"), remap = false)
    private void cnpcplus$installInspectionGoal(CallbackInfo ci) {
        DamageInspectionController.install((EntityNPCInterface) (Object) this);
    }

    @Inject(method = "m_6475_", at = @At("HEAD"), remap = false)
    private void cnpcplus$inspectUnseenDamage(DamageSource source, float amount, CallbackInfo ci) {
        DamageInspectionController.onHurt((EntityNPCInterface) (Object) this, source, amount);
    }

    @Inject(method = "m_142687_", at = @At("HEAD"), remap = false)
    private void cnpcplus$removeInspectionGoal(net.minecraft.world.entity.Entity.RemovalReason reason, CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (reason == net.minecraft.world.entity.Entity.RemovalReason.DISCARDED
                || reason == net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK
                || reason == net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_WITH_PLAYER) {
            DamageInspectionController.remove(npc);
        }
    }

    @Inject(method = "m_7378_", at = @At("RETURN"), remap = false)
    private void cnpcplus$ensureInspectionGoalOnLoad(CompoundTag tag, CallbackInfo ci) {
        DamageInspectionController.install((EntityNPCInterface) (Object) this);
    }

    @Inject(method = "reset", at = @At("HEAD"), remap = false)
    private void cnpcplus$clearInspectionOnReset(CallbackInfo ci) {
        DamageInspectionController.reset((EntityNPCInterface) (Object) this);
    }

    @Inject(method = "onCollide", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$throttleCollisionScan(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (!top.cnpcplus.ai.NpcAiOptimizerAccess.enabled()) return;
        if (!top.cnpcplus.ai.NpcAiOptimizerAccess.collideWhenScriptsDisabled()
                && !top.cnpcplus.ai.NpcAiOptimizerAccess.scriptsEnabled(npc)) {
            ci.cancel();
            return;
        }
        int interval = top.cnpcplus.ai.NpcAiOptimizerAccess.collideInterval();
        if (interval > 4 && !top.cnpcplus.ai.NpcAiOptimizerAccess.shouldRun(npc, interval)) ci.cancel();
    }

}
