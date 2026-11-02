package top.cnpcplus.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import noppes.npcs.client.renderer.RenderNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** 1.21.1 stores alpha in the high byte of the vertex color, not ShaderColor. */
@Mixin(value = LivingEntityRenderer.class, remap = false)
public abstract class MixinLivingEntityRendererNpcAlpha {
    @ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"), index = 4, require = 1)
    private int cnpcplus$vertexAlpha(int color) {
        var npc = RenderNPCInterface.currentNpc;
        return npc != null && npc.display.getVisible() == 2 ? (color & 0xFFFFFF) | ((color >>> 24) / 2 << 24) : color;
    }
}
