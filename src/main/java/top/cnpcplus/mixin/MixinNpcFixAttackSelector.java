package top.cnpcplus.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.ai.selector.NPCAttackSelector;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.common.util.LogWriter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.ai.NpcAiOptimizerAccess;

/** 241h：廉价范围拒绝 + 拒绝原因诊断日志（限流 40t）。 */
@Mixin(value = NPCAttackSelector.class, remap = false)
public abstract class MixinNpcFixAttackSelector {
    @Unique
    private long cnpcplus$lastLog = Long.MIN_VALUE;

    @Shadow(remap = false)
    private EntityNPCInterface npc;

    @Inject(method = "isEntityApplicable", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$cheapReject(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!NpcAiOptimizerAccess.enabled() || this.npc == null || entity == null
                || entity == this.npc || !entity.isAlive()) return;
        int maxRange = NpcAiOptimizerAccess.maxAggroRange();
        if (maxRange > 0 && !this.npc.isInRange(entity, maxRange)) cir.setReturnValue(false);
    }

    @Inject(method = "isEntityApplicable", at = @At("RETURN"), remap = false)
    private void cnpcplus$diagResult(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (this.npc == null || entity == null || entity == this.npc) return;
        if (!(entity instanceof ServerPlayer player)) return;
        long now = this.npc.level() == null ? 0 : this.npc.level().getGameTime();
        if (now - cnpcplus$lastLog < 40L) return;
        cnpcplus$lastLog = now;
        boolean ret = cir.getReturnValueZ();
        int aggro = this.npc.stats == null ? -1 : this.npc.stats.aggroRange;
        boolean inRange = this.npc.isInRange(entity, aggro);
        boolean factionAgg = false;
        try {
            factionAgg = this.npc.faction != null && this.npc.faction.isAggressiveToPlayer(player);
        } catch (Throwable ignored) {}
        int factionId = this.npc.faction == null ? -1 : this.npc.faction.id;
        int neutralPts = this.npc.faction == null ? -1 : this.npc.faction.neutralPoints;
        int playerPts = -1;
        try {
            playerPts = noppes.npcs.controllers.data.PlayerData.get(player)
                    .factionData.getFactionPoints(player, factionId);
        } catch (Throwable ignored) {}
        boolean creative = player.getAbilities().instabuild;
        LogWriter.warn("[sel] id=" + this.npc.getId() + " ret=" + ret + " inRange=" + inRange
                + " aggro=" + aggro + " factionId=" + factionId + " neutralPts=" + neutralPts
                + " playerPts=" + playerPts + " factionAgg=" + factionAgg + " creative=" + creative);
    }
}
