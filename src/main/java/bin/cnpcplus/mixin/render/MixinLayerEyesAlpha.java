package bin.cnpcplus.mixin.render;

import bin.cnpcplus.mixin.perf.ILayerNpcAccessor;
import noppes.npcs.client.layer.LayerEyes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = LayerEyes.class, remap = false)
public abstract class MixinLayerEyesAlpha {
    // Eyes use vertex colors, not the current GL color used by the other parts.
    @ModifyArg(method = "drawRect", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/BufferBuilder;func_181666_a(FFFF)Lnet/minecraft/client/renderer/BufferBuilder;"),
            index = 3, require = 4)
    private float cnpcplus$eyeAlpha(float alpha) {
        return ((ILayerNpcAccessor) this).cnpcplus$getNpc().isInvisible() ? 0.15f : alpha;
    }
}
