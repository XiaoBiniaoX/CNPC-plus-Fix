package top.cnpcplus.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import noppes.npcs.client.renderer.RenderNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** 将 alpha 写进顶点；全局 ShaderColor 会在缓冲提交前被官方代码重置。 */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRendererNpcAlpha {
    @ModifyArg(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"), index = 7)
    private float cnpcplus$vertexAlpha(float alpha) {
        var npc = RenderNPCInterface.currentNpc;
        return npc != null && npc.display.getVisible() == 2 ? alpha * 0.5f : alpha;
    }
}
