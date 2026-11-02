package top.cnpcplus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import noppes.npcs.client.layer.LayerGlow;
import noppes.npcs.client.renderer.RenderNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = LayerGlow.class, remap = false)
public abstract class MixinLayerGlowNpcAlpha {
    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnoppes/npcs/entity/EntityNPCInterface;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"), require = 1)
    private void cnpcplus$overlayAlpha(EntityModel<?> model, PoseStack pose, VertexConsumer buffer, int light, int overlay) {
        var npc = RenderNPCInterface.currentNpc;
        model.renderToBuffer(pose, buffer, light, overlay, npc != null && npc.display.getVisible() == 2 ? 0x7FFFFFFF : -1);
    }
}
