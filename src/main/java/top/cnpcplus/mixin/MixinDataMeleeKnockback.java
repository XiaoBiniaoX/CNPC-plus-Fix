package top.cnpcplus.mixin;

import noppes.npcs.entity.data.DataMelee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A5: 近战击退支持负数（用户明确要求保留），clamp 到 [-10,10] 防止越界。
 * 用户原始需求仅要求修复「留空保存崩溃」，负数本身是合法功能。
 */
@Mixin(value = DataMelee.class, remap = false)
public class MixinDataMeleeKnockback {

    @Shadow(remap = false)
    private int knockback;

    @Inject(method = "setKnockback", at = @At("HEAD"), cancellable = true, remap = false)
    private void onSetKnockback(int kb, CallbackInfo ci) {
        this.knockback = Math.max(-10, Math.min(10, kb));
        ci.cancel();
    }
}