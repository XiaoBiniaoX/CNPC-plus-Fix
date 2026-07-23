package bin.cnpcplus.mixin.puppet;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.roles.JobPuppet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import bin.cnpcplus.puppet.JobPuppetAccessor;
import bin.cnpcplus.puppet.PartConfigAccessor;

@Mixin(value = LivingEntityRenderer.class, remap = false)
public class MixinLivingEntityRendererPuppetEquipment {
    @Redirect(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V"))
    private void cnpcplus$redirectLayerRender(RenderLayer layer, PoseStack poseStack, MultiBufferSource buffer, int packedLight, Entity entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        String layerName = layer.getClass().getName();
        boolean isSlashBlade = layerName.equals("mods.flammpfeil.slashblade.client.renderer.layers.LayerMainBlade");
        boolean isTaczLayer = layerName.contains("tacz") || layerName.contains("com.tacz");
        if ((isSlashBlade || isTaczLayer) && entity instanceof EntityCustomNpc npc && npc.job.getType() == 9) {
            JobPuppet job = (JobPuppet) npc.job;
            if (job.isActive()) {
                JobPuppet.PartConfig config = ((JobPuppetAccessor) job).cnpcplus$getMainhand();
                if (config != null && !config.disabled && config instanceof PartConfigAccessor pca) {
                    poseStack.translate(pca.cnpcplus$getOffsetX(), pca.cnpcplus$getOffsetY(), pca.cnpcplus$getOffsetZ());
                    poseStack.mulPose(Axis.XP.rotationDegrees(config.rotationX * 180));
                    poseStack.mulPose(Axis.YP.rotationDegrees(config.rotationY * 180));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(config.rotationZ * 180));
                }
            }
        }
        layer.render(poseStack, buffer, packedLight, entity, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch);
    }
}
