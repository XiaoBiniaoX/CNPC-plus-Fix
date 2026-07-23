package bin.cnpcplus.mixin.puppet;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.roles.JobPuppet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import bin.cnpcplus.puppet.JobPuppetAccessor;
import bin.cnpcplus.puppet.PartConfigAccessor;

@Mixin(value = ItemInHandLayer.class, priority = 100, remap = false)
public class MixinItemInHandLayerPuppetEquipment {
    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", shift = At.Shift.BEFORE))
    private void cnpcplus$applyPuppetHandOffset(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (!(entity instanceof EntityCustomNpc npc) || npc.job.getType() != 9) return;
        JobPuppet job = (JobPuppet) npc.job;
        if (!job.isActive()) return;
        JobPuppetAccessor acc = (JobPuppetAccessor) job;
        JobPuppet.PartConfig config = arm == entity.getMainArm() ? acc.cnpcplus$getMainhand() : acc.cnpcplus$getOffhand();
        if (config == null || config.disabled || !(config instanceof PartConfigAccessor pca)) return;
        poseStack.translate(pca.cnpcplus$getOffsetX(), pca.cnpcplus$getOffsetY(), pca.cnpcplus$getOffsetZ());
        poseStack.mulPose(Axis.XP.rotationDegrees(config.rotationX * 180));
        poseStack.mulPose(Axis.YP.rotationDegrees(config.rotationY * 180));
        poseStack.mulPose(Axis.ZP.rotationDegrees(config.rotationZ * 180));
    }
}
