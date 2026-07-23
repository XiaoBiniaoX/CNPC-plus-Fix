package bin.cnpcplus.mixin.puppet;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.ModelPartConfig;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import bin.cnpcplus.accessor.EquipmentModelDataAccessor;

@Mixin(value = CustomHeadLayer.class, remap = false)
public class MixinCustomHeadLayerScale {

    @Unique
    private static final ThreadLocal<Boolean> cnpcplus$didPush = ThreadLocal.withInitial(() -> false);

    @Inject(method = "render", at = @At("HEAD"))
    private void cnpcplus$scaleHeadItem(PoseStack poseStack, MultiBufferSource buffer, int packedLight, LivingEntity entity,
                                        float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                                        float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof EntityCustomNpc npc) || npc.modelData == null) {
            cnpcplus$didPush.set(false);
            return;
        }
        ModelPartConfig config = ((EquipmentModelDataAccessor) npc.modelData).getHelmet();
        if (config == null || (config.scaleX == 1.0f && config.scaleY == 1.0f && config.scaleZ == 1.0f)) {
            cnpcplus$didPush.set(false);
            return;
        }
        poseStack.pushPose();
        poseStack.scale(config.scaleX, config.scaleY, config.scaleZ);
        cnpcplus$didPush.set(true);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void cnpcplus$popHeadItemScale(PoseStack poseStack, MultiBufferSource buffer, int packedLight, LivingEntity entity,
                                           float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                                           float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (cnpcplus$didPush.get()) {
            poseStack.popPose();
            cnpcplus$didPush.set(false);
        }
    }
}
