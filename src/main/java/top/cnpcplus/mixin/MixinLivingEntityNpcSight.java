package top.cnpcplus.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.ai.NpcSightGuard;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityNpcSight {
    @Unique private boolean cnpcplus$invalidSightReported;

    @Inject(method = "hasLineOfSight", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$validateNpcSight(Entity target, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof EntityNPCInterface) && !(target instanceof EntityNPCInterface)) return;
        if (target.level() != self.level()) return; // Vanilla handles cross-dimension targets.
        Vec3 from = self.getEyePosition();
        Vec3 to = target.getEyePosition();
        boolean finite = NpcSightGuard.isFinite(from) && NpcSightGuard.isFinite(to);
        if (!finite && !cnpcplus$invalidSightReported) {
            LoggerFactory.getLogger("cnpcplus").warn(
                    "[npc-sight] Rejected non-finite sight ray: {} {} -> {} {}",
                    self.getUUID(), from, target.getUUID(), to);
        }
        cnpcplus$invalidSightReported = !finite;
        if (!NpcSightGuard.canTrace(from, to, self.level()::hasChunkAt)) cir.setReturnValue(false);
    }
}
