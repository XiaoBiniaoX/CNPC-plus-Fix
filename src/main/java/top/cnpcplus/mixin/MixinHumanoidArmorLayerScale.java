package top.cnpcplus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.ModelPartConfig;
import noppes.npcs.ModelDataShared;
import noppes.npcs.entity.EntityCustomNpc;
import top.cnpcplus.accessor.EquipmentModelDataAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public class MixinHumanoidArmorLayerScale {

    @Unique
    private static final ThreadLocal<Boolean> cnpcplus$didPush = ThreadLocal.withInitial(() -> false);

    @Inject(method = "renderArmorPiece", at = @At("HEAD"))
    private void cnpcplus$onRenderArmorPieceHead(PoseStack poseStack, MultiBufferSource buffer, LivingEntity entity, EquipmentSlot slot, int light, HumanoidModel<LivingEntity> model, CallbackInfo ci) {
        if (!(entity instanceof EntityCustomNpc)) {
            cnpcplus$didPush.set(false);
            return;
        }
        ModelPartConfig config = cnpcplus$getEquipConfigForSlot((EntityCustomNpc) entity, slot);
        if (config == null || (config.scaleX == 1.0f && config.scaleY == 1.0f && config.scaleZ == 1.0f)) {
            cnpcplus$didPush.set(false);
            return;
        }
        poseStack.pushPose();
        poseStack.scale(config.scaleX, config.scaleY, config.scaleZ);
        cnpcplus$didPush.set(true);
    }

    @Inject(method = "renderArmorPiece", at = @At("RETURN"))
    private void cnpcplus$onRenderArmorPieceReturn(PoseStack poseStack, MultiBufferSource buffer, LivingEntity entity, EquipmentSlot slot, int light, HumanoidModel<LivingEntity> model, CallbackInfo ci) {
        if (cnpcplus$didPush.get()) {
            poseStack.popPose();
            cnpcplus$didPush.set(false);
        }
    }

    @Unique
    private static ModelPartConfig cnpcplus$getEquipConfigForSlot(EntityCustomNpc npc, EquipmentSlot slot) {
        ModelDataShared data = npc.modelData;
        if (data == null) return null;
        EquipmentModelDataAccessor accessor = (EquipmentModelDataAccessor) data;
        switch (slot) {
            case FEET: return accessor.getBoots();
            case LEGS: return accessor.getLeggings();
            case CHEST: return accessor.getChestplate();
            case HEAD: return accessor.getHelmet();
        }
        return null;
    }
}
