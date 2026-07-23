package bin.cnpcplus.mixin.puppet;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.ModelPartConfig;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import bin.cnpcplus.accessor.EquipmentModelDataAccessor;

@Mixin(value = HumanoidArmorLayer.class, remap = false)
public class MixinHumanoidArmorLayerScale {

    @Unique
    private final ThreadLocal<Boolean> cnpcplus$didPush = ThreadLocal.withInitial(() -> false);

    @Inject(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At("HEAD"))
    private void cnpcplus$pushScale(
        PoseStack poseStack, MultiBufferSource buffer, LivingEntity entity,
        EquipmentSlot slot, int packedLight, HumanoidModel<?> model,
        float limbSwing, float limbSwingAmount, float partialTick,
        float ageInTicks, float netHeadYaw, float headPitch,
        CallbackInfo ci
    ) {
        if (!(entity instanceof EntityCustomNpc npc) || npc.modelData == null) {
            cnpcplus$didPush.set(false);
            return;
        }
        ModelPartConfig config = cnpcplus$getConfigForSlot(slot, (EquipmentModelDataAccessor) npc.modelData);
        if (config == null || (config.scaleX == 1.0f && config.scaleY == 1.0f && config.scaleZ == 1.0f)) {
            cnpcplus$didPush.set(false);
            return;
        }
        poseStack.pushPose();
        poseStack.scale(config.scaleX, config.scaleY, config.scaleZ);
        cnpcplus$didPush.set(true);
    }

    @Inject(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At("RETURN"))
    private void cnpcplus$popScale(
        PoseStack poseStack, MultiBufferSource buffer, LivingEntity entity,
        EquipmentSlot slot, int packedLight, HumanoidModel<?> model,
        float limbSwing, float limbSwingAmount, float partialTick,
        float ageInTicks, float netHeadYaw, float headPitch,
        CallbackInfo ci
    ) {
        if (cnpcplus$didPush.get()) {
            poseStack.popPose();
            cnpcplus$didPush.set(false);
        }
    }

    @Unique
    private ModelPartConfig cnpcplus$getConfigForSlot(EquipmentSlot slot, EquipmentModelDataAccessor acc) {
        return switch (slot) {
            case HEAD -> acc.getHelmet();
            case CHEST -> acc.getChestplate();
            case LEGS -> acc.getLeggings();
            case FEET -> acc.getBoots();
            default -> null;
        };
    }
}