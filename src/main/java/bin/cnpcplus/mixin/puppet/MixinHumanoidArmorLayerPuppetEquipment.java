package bin.cnpcplus.mixin.puppet;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.roles.JobPuppet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import bin.cnpcplus.puppet.JobPuppetAccessor;
import bin.cnpcplus.puppet.PartConfigAccessor;

@Mixin(value = HumanoidArmorLayer.class, priority = 100, remap = false)
public class MixinHumanoidArmorLayerPuppetEquipment {

    @Inject(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/model/HumanoidModel;copyPropertiesTo(Lnet/minecraft/client/model/HumanoidModel;)V",
                     shift = At.Shift.AFTER))
    private void cnpcplus$applyPuppetArmorOffsets(
        PoseStack poseStack, MultiBufferSource buffer, LivingEntity entity,
        EquipmentSlot slot, int packedLight, HumanoidModel<?> model,
        float limbSwing, float limbSwingAmount, float partialTick,
        float ageInTicks, float netHeadYaw, float headPitch,
        CallbackInfo ci
    ) {
        if (!(entity instanceof EntityCustomNpc npc) || npc.job.getType() != 9) return;
        JobPuppet job = (JobPuppet) npc.job;
        if (!job.isActive()) return;
        JobPuppetAccessor acc = (JobPuppetAccessor) job;
        switch (slot) {
            case HEAD -> cnpcplus$apply(acc.cnpcplus$getHelmet(), model.head);
            case CHEST -> cnpcplus$apply(acc.cnpcplus$getChestplate(), model.body);
            case LEGS -> {
                cnpcplus$apply(acc.cnpcplus$getLeggings(), model.leftLeg);
                cnpcplus$apply(acc.cnpcplus$getLeggings(), model.rightLeg);
            }
            case FEET -> {
                cnpcplus$apply(acc.cnpcplus$getBoots(), model.leftLeg);
                cnpcplus$apply(acc.cnpcplus$getBoots(), model.rightLeg);
            }
        }
    }

    private static void cnpcplus$apply(JobPuppet.PartConfig config, ModelPart part) {
        if (config == null || config.disabled || !(config instanceof PartConfigAccessor acc)) return;
        part.x += acc.cnpcplus$getOffsetX();
        part.y += acc.cnpcplus$getOffsetY();
        part.z += acc.cnpcplus$getOffsetZ();
        part.xRot += config.rotationX * (float) Math.PI;
        part.yRot += config.rotationY * (float) Math.PI;
        part.zRot += config.rotationZ * (float) Math.PI;
        part.xScale *= acc.cnpcplus$getScaleX();
        part.yScale *= acc.cnpcplus$getScaleY();
        part.zScale *= acc.cnpcplus$getScaleZ();
    }
}