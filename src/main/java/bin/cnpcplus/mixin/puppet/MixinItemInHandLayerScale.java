package bin.cnpcplus.mixin.puppet;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.ModelPartConfig;
import noppes.npcs.ModelDataShared;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import bin.cnpcplus.accessor.EquipmentModelDataAccessor;

@Mixin(value = ItemInHandLayer.class, priority = 200, remap = false)
public class MixinItemInHandLayerScale {

    @Unique
    private static final ThreadLocal<Boolean> cnpcplus$didPush = ThreadLocal.withInitial(() -> false);

    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", shift = At.Shift.BEFORE))
    private void cnpcplus$preHand(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        ModelPartConfig config = cnpcplus$getHandConfig(entity, stack);
        if (config != null) {
            poseStack.pushPose();
            poseStack.scale(config.scaleX, config.scaleY, config.scaleZ);
            cnpcplus$didPush.set(true);
        }
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"))
    private void cnpcplus$postHand(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (cnpcplus$didPush.get()) {
            poseStack.popPose();
            cnpcplus$didPush.set(false);
        }
    }

    @Unique
    private static ModelPartConfig cnpcplus$getHandConfig(LivingEntity entity, ItemStack stack) {
        if (!(entity instanceof EntityCustomNpc)) return null;
        EntityCustomNpc npc = (EntityCustomNpc) entity;
        ModelDataShared data = npc.modelData;
        if (data == null) return null;
        if (stack == npc.getMainHandItem()) {
            return ((EquipmentModelDataAccessor)data).getMainhand();
        } else if (stack == npc.getOffhandItem()) {
            return ((EquipmentModelDataAccessor)data).getOffhand();
        }
        return null;
    }
}
