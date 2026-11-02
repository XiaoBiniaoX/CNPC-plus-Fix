package bin.cnpcplus.mixin.render;

import noppes.npcs.client.layer.LayerGlow;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(value = LayerGlow.class, remap = false)
public abstract class MixinLayerGlowAlpha {
    @ModifyArgs(method = "doRenderLayer(Lnoppes/npcs/entity/EntityNPCInterface;FFFFFFF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;func_179112_b(II)V"), require = 1)
    private void cnpcplus$glowBlend(Args args, EntityNPCInterface npc, float limb, float amount, float partial,
                                  float age, float yaw, float pitch, float scale) {
        if (npc.isInvisible()) args.set(0, 770); // SRC_ALPHA + ONE preserves additive glow, attenuated with the body.
    }
    @ModifyArgs(method = "doRenderLayer(Lnoppes/npcs/entity/EntityNPCInterface;FFFFFFF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;func_179131_c(FFFF)V"), require = 1)
    private void cnpcplus$glowAlpha(Args args, EntityNPCInterface npc, float limb, float amount, float partial,
                                  float age, float yaw, float pitch, float scale) {
        if (npc.isInvisible()) args.set(3, 0.15f);
    }
}
