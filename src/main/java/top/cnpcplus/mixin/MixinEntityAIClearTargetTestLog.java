package top.cnpcplus.mixin;

import noppes.npcs.ai.target.EntityAIClearTarget;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 240 诊断测试日志（[tgt-test]）：敌对 NPC「主动拿到目标后又被清掉」侦测。
 * EntityAIClearTarget.start（m_8056_）把 npc.setTarget(null) —— 此处记录被清时刻。
 * 临时测试用，定位后整类删除并从 mixins.json 移除。
 */
@Mixin(value = EntityAIClearTarget.class, remap = false)
public abstract class MixinEntityAIClearTargetTestLog {
    private static final org.slf4j.Logger cnpcplus$TEST_LOG =
            org.slf4j.LoggerFactory.getLogger("cnpcplus");

    @Shadow(remap = false)
    private EntityNPCInterface npc;

    @Inject(method = "m_8056_", at = @At("TAIL"), remap = false)
    private void cnpcplus$testClearLog(CallbackInfo ci) {
        if (this.npc == null || this.npc.level() == null) return;
        cnpcplus$TEST_LOG.warn("[tgt-test] clearTarget npcId={} oldTarget={} tick={}",
                this.npc.getId(), this.npc.getTarget(), this.npc.level().getGameTime());
    }
}
