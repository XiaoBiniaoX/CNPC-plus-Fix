package top.cnpcplus.mixin;

import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.ai.EntityAIFollow;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.config.CnpcPlusServerConfig;

/**
 * B2: 随从被困在坑洞/墙壁时无法传送，且寻路持续失败导致 TPS 波动。
 * 原逻辑：moveTo 失败且玩家不在 16 格内才 tpTo —— 被墙困住时 moveTo 可能返回 true 但走不动，
 * 且 16 格内的玩家不会触发传送。
 * 修复：当随从与玩家的距离超过配置阈值（FollowerTeleportRange，默认12格）时，直接 tpTo，跳过寻路。
 */
@Mixin(value = EntityAIFollow.class, remap = false)
public class MixinEntityAIFollowTeleport {

    @Shadow(remap = false)
    private EntityNPCInterface npc;

    @Shadow(remap = false)
    private LivingEntity owner;

    @Shadow(remap = false)
    public int updateTick;

    @Inject(method = "m_8037_", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$forceTeleportOnDistance(CallbackInfo ci) {
        if (this.npc == null || this.owner == null) return;
        if (!this.npc.isAlive()) return;
        int range = CnpcPlusServerConfig.FollowerTeleportRange.get();
        double distSq = this.npc.distanceToSqr(this.owner);
        if (distSq <= (double) range * range) return;
        this.updateTick = 0;
        this.npc.tpTo(this.owner);
        ci.cancel();
    }
}
