package top.cnpcplus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
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
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"), require = 1)
    private static void cnpcplus$rotate(LivingEntity entity, PoseStack pose, MultiBufferSource buffer, int light, MarkData.Mark mark, CallbackInfo ci) {
        MarkRotation rotation = (MarkRotation)mark;
        float angle = (System.nanoTime() % 15_000_000_000L) / 1_000_000_000f * 24;
        if (rotation.cnpcplus$horizontal()) pose.mulPose(Axis.YP.rotationDegrees(angle));
        if (rotation.cnpcplus$vertical()) pose.mulPose(Axis.XP.rotationDegrees(angle));
    }
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnoppes/npcs/shared/client/model/Model2DRenderer;render(Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"), require = 1)
    private static void cnpcplus$texture(Model2DRenderer renderer, ResourceLocation texture, PoseStack pose, VertexConsumer consumer,
            int light, int overlay, float red, float green, float blue, float alpha,
            LivingEntity entity, PoseStack original, MultiBufferSource buffer, int packedLight, MarkData.Mark mark) {
        ResourceLocation selected = MarkTypes.texture(mark.type, texture);
        renderer.render(selected, pose, buffer.getBuffer(RenderType.entityCutout(selected)), light, overlay, red, green, blue, alpha);
    }
}
