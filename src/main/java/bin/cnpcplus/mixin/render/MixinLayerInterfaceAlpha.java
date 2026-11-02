package bin.cnpcplus.mixin.render;

import noppes.npcs.client.layer.LayerInterface;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = LayerInterface.class, remap = false)
public abstract class MixinLayerInterfaceAlpha {
    @Shadow protected EntityCustomNpc npc;

    // The native hurt tint replaces the ghost alpha with 0.3; retain the body alpha.
    @ModifyArg(method = "func_177141_a", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;func_179131_c(FFFF)V", ordinal = 1), index = 3, require = 1)
    private float cnpcplus$hurtAlpha(float alpha) { return npc.isInvisible() ? 0.15f : alpha; }
}
