package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.entity.EntityLivingBase;
import noppes.npcs.ai.EntityAIFollow;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 距离瞬移与失败退避共用一个调用点；0 关闭，失败按 10..200 tick 重试。 */
@Mixin(value = EntityAIFollow.class, remap = false)
public abstract class MixinEntityAIFollowBackoff {
    @Shadow private EntityNPCInterface npc;
    @Shadow private EntityLivingBase owner;
    @Shadow public int updateTick;
    @Unique private int cnpcplus$penalty;
    @Unique private long cnpcplus$nextTeleport;

    @Inject(method = "func_75246_d()V", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$teleportBeyondRange(CallbackInfo ci) {
        int range = CnpcPlusConfig.getFollowerTeleportRange();
        if (range <= 0 || !cnpcplus$canTeleport(this.npc, this.owner)) return;
        if (this.npc.getDistanceSq(this.owner) <= (double) range * range) {
            this.cnpcplus$penalty = 0;
            return;
        }
        cnpcplus$tryTeleport(this.npc, this.owner);
        ci.cancel();
    }

    @Redirect(method = "func_75246_d()V", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityNPCInterface;tpTo(Lnet/minecraft/entity/EntityLivingBase;)V"), require = 1)
    private void cnpcplus$backoffAfterTeleport(EntityNPCInterface npc, EntityLivingBase owner) {
        cnpcplus$tryTeleport(npc, owner);
    }

    @Unique private static boolean cnpcplus$canTeleport(EntityNPCInterface npc, EntityLivingBase owner) {
        return npc != null && owner != null && !npc.world.isRemote && npc.isEntityAlive() && !npc.isKilled()
                && !npc.isRiding() && !npc.isBeingRidden() && owner.isEntityAlive();
    }

    @Unique private void cnpcplus$tryTeleport(EntityNPCInterface npc, EntityLivingBase owner) {
        int range = CnpcPlusConfig.getFollowerTeleportRange();
        if (range <= 0 || !cnpcplus$canTeleport(npc, owner) || npc.getDistanceSq(owner) <= (double) range * range) return;
        long now = npc.world.getTotalWorldTime();
        if (now < this.cnpcplus$nextTeleport) return;
        npc.tpTo(owner);
        npc.getNavigator().clearPath();
        this.updateTick = 0;
        if (npc.getDistanceSq(owner) <= (double) range * range) this.cnpcplus$penalty = 0;
        else this.cnpcplus$penalty = this.cnpcplus$penalty == 0 ? 10 : Math.min(200, this.cnpcplus$penalty * 2);
        this.cnpcplus$nextTeleport = now + Math.max(10, this.cnpcplus$penalty);
    }
}
