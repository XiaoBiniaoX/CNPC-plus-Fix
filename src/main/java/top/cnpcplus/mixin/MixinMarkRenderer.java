package top.cnpcplus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RenderLivingEvent;
import noppes.npcs.client.renderer.MarkRenderer;
import noppes.npcs.controllers.data.MarkData;
import noppes.npcs.shared.client.model.Model2DRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.marks.MarkRotation;
import top.cnpcplus.marks.client.MarkTypes;

@Mixin(value = MarkRenderer.class, remap = false)
public abstract class MixinMarkRenderer {
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;m_252880_(FFF)V"), require = 1)
    private static void cnpcplus$rotate(RenderLivingEvent.Post<?, ?> event, MarkData.Mark mark, CallbackInfo ci) {
        if (!(mark instanceof MarkRotation rotation)) return;
        float angle = (System.nanoTime() % 15_000_000_000L) / 1_000_000_000f * 24;
        if (rotation.cnpcplus$horizontal()) event.getPoseStack().mulPose(Axis.YP.rotationDegrees(angle));
        if (rotation.cnpcplus$vertical()) event.getPoseStack().mulPose(Axis.XP.rotationDegrees(angle));
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/shared/client/model/Model2DRenderer;render(Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"), require = 1)
    private static void cnpcplus$texture(Model2DRenderer renderer, ResourceLocation texture, PoseStack pose,
            VertexConsumer buffer, int light, int overlay, float red, float green, float blue, float alpha,
            RenderLivingEvent.Post<?, ?> event, MarkData.Mark mark) {
        ResourceLocation selected = MarkTypes.texture(mark.type, texture);
        renderer.render(selected, pose, event.getMultiBufferSource().getBuffer(RenderType.entityCutout(selected)),
                light, overlay, red, green, blue, alpha);
    }
}
