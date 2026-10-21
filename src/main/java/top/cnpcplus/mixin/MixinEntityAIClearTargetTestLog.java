package top.cnpcplus.mixin;

import noppes.npcs.ai.target.EntityAIClearTarget;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EntityAIClearTarget 探针（241f 已清理旧日志，保留空壳避免 mixins.json 注册项变化；实际不再输出）。
 */
@Mixin(value = EntityAIClearTarget.class, remap = false)
public abstract class MixinEntityAIClearTargetTestLog {

    @Shadow(remap = false)
    private EntityNPCInterface npc;

    @Inject(method = "m_8056_", at = @At("TAIL"), remap = false)
    private void cnpcplus$noop(CallbackInfo ci) {
    }
}
