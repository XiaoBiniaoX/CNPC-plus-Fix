package top.cnpcplus.mixin;

import net.minecraft.world.entity.Mob;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 240d 诊断测试日志（定位后整段删除）：serverAiStep 是否被调用。
 *
 * <p>与心跳（npcTick）配合定位「canUse 零调用」：心跳证明 tick/baseTick 链在跑；
 * 若本探针在服务端对 NPC 也零日志，则断点在 aiStep 门（isImmobile/isEffectiveAi）
 * 或 serverAiStep 未被调用；若本探针有日志而 canUse 仍零日志，则断点在
 * GoalSelector.tick 内部（配合心跳的 tgtGoals 计数判定）。
 */
@Mixin(Mob.class)
public abstract class MixinNpcFixServerAiStepTestLog {
    /** 240 测试日志专用 slf4j logger（区别于生产 ERROR 日志）。 */
    @Unique
    private static final org.slf4j.Logger cnpcplus$TEST_LOG = LoggerFactory.getLogger("cnpcplus");

    // 方法名同时给 mojmap 与 SRG 两个候选（m_6140_ 为 SRG，javap 实证）；require=1 失败即崩，禁静默失效
    @Inject(method = {"serverAiStep", "m_6140_"}, at = @At("HEAD"), require = 1)
    private void cnpcplus$testServerAiStepLog(CallbackInfo ci) {
        if (!((Object) this instanceof EntityNPCInterface npc)) return;
        if (npc.level() == null || npc.level().isClientSide()) return;
        if (npc.tickCount % 200 != 0 && npc.tickCount > 2) return;
        cnpcplus$TEST_LOG.warn(
                "[tgt-test] serverAiStep id={} t={} tickCount={} noAi={}",
                npc.getId(), npc.level().getGameTime(), npc.tickCount, npc.isNoAi());
    }
}
