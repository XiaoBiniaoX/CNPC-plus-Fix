package top.cnpcplus.mixin;

import net.minecraft.world.entity.Mob;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * serverAiStep 探针（241f 已清理旧日志，保留空壳避免 mixins.json 注册项变化；实际不再输出）。
 */
@Mixin(Mob.class)
public abstract class MixinNpcFixServerAiStepTestLog {

    @Inject(method = {"serverAiStep", "m_6140_"}, at = @At("HEAD"), require = 1)
    private void cnpcplus$noop(CallbackInfo ci) {
    }
}
