package top.cnpcplus.mixin;

import noppes.npcs.client.layer.LayerGlow;
import noppes.npcs.client.renderer.RenderNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = LayerGlow.class, remap = false)
public abstract class MixinLayerGlowNpcAlpha {
    @ModifyArg(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnoppes/npcs/entity/EntityNPCInterface;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;m_7695_(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"), index = 7, require = 1)
    private float cnpcplus$overlayAlpha(float alpha) {
        var npc = RenderNPCInterface.currentNpc;
        return npc != null && npc.display.getVisible() == 2 ? alpha * 0.5f : alpha;
    }
}
