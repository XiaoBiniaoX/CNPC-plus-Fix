package top.cnpcplus.mixin;

import noppes.npcs.client.layer.LayerInterface;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LayerInterface.class, remap = false)
public abstract class MixinLayerInterfaceNpcAlpha {
    @Shadow protected EntityCustomNpc npc;

    @Inject(method = "alpha", at = @At("RETURN"), cancellable = true, require = 1)
    private void cnpcplus$layerAlpha(CallbackInfoReturnable<Float> cir) {
        if (npc != null && npc.display.getVisible() == 2) cir.setReturnValue(cir.getReturnValue() * 0.5F);
    }
}
