package bin.cnpcplus.mixin.render;

import bin.cnpcplus.mixin.perf.ILayerNpcAccessor;
import noppes.npcs.client.layer.LayerHeadwear;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = LayerHeadwear.class, remap = false)
public abstract class MixinLayerHeadwearAlpha {
    @ModifyArg(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;func_179131_c(FFFF)V"), index = 3, require = 1)
    private float cnpcplus$headwearAlpha(float alpha) {
        return ((ILayerNpcAccessor) this).cnpcplus$getNpc().isInvisible() ? 0.15f : alpha;
    }
}
