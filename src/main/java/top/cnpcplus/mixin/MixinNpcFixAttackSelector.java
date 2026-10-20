package top.cnpcplus.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.ai.selector.NPCAttackSelector;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.ai.NpcAiOptimizerAccess;

/** Only performs cheap, opt-in range/liveness rejection; faction behavior stays native. */
@Mixin(value = NPCAttackSelector.class, remap = false)
public abstract class MixinNpcFixAttackSelector {
    /** 240 测试日志专用 logger（诊断用，定位后删除）。 */
    @Unique
    private static final org.slf4j.Logger cnpcplus$TEST_LOG = org.slf4j.LoggerFactory.getLogger("cnpcplus");

    @Unique
    private long cnpcplus$lastSelLog = Long.MIN_VALUE;

    @Unique
    private int cnpcplus$selLogCount = 0;

    @Shadow(remap = false)
    private EntityNPCInterface npc;

    @Inject(method = "isEntityApplicable", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$cheapReject(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!NpcAiOptimizerAccess.enabled() || this.npc == null || entity == null
                || entity == this.npc || !entity.isAlive()) return;
        int maxRange = NpcAiOptimizerAccess.maxAggroRange();
        if (maxRange > 0 && !this.npc.isInRange(entity, maxRange)) cir.setReturnValue(false);
    }

    /** 240 测试日志：selector 对玩家的判定明细（仅记玩家，40t 限流，首 5 次不限）。 */
    @Inject(method = "isEntityApplicable", at = @At("RETURN"), remap = false)
    private void cnpcplus$testSelectorLog(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (this.npc == null || entity == null || this.npc.level() == null) return;
        if (!(entity instanceof ServerPlayer)) return;
        long now = this.npc.level().getGameTime();
        if (cnpcplus$selLogCount >= 5 && now - cnpcplus$lastSelLog < 40L) return;
        cnpcplus$lastSelLog = now;
        cnpcplus$selLogCount++;
        double dist = this.npc.distanceTo(entity);
        boolean factionAggro = this.npc.faction != null
                && this.npc.faction.isAggressiveToPlayer((net.minecraft.world.entity.player.Player) entity);
        boolean sensingLos = this.npc.getSensing().hasLineOfSight(entity);
        cnpcplus$TEST_LOG.warn(
                "[tgt-test] selector={} dist={} aggroRange={} factionAggro={} directLOS={} sensingLos={} player={}",
                cir.getReturnValueZ(), String.format("%.1f", dist), this.npc.stats == null ? -1 : this.npc.stats.aggroRange,
                factionAggro, this.npc.ais == null || this.npc.ais.directLOS, sensingLos,
                entity.getName().getString());
    }
}
