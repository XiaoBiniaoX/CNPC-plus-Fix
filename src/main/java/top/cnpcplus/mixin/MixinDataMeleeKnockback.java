package top.cnpcplus.mixin;

import noppes.npcs.entity.data.DataMelee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DataMelee.class, remap = false)
public class MixinDataMeleeKnockback {

    @Shadow
    private int knockback;

    @Inject(method = "setKnockback", at = @At("HEAD"), cancellable = true, remap = false)
    private void onSetKnockback(int kb, CallbackInfo ci) {
        this.knockback = Math.max(-10, Math.min(10, kb));
        ci.cancel();
    }
}
