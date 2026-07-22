package bin.cnpcplus.mixin;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"),
        remap = false)
    private void cnpcplus$redirectSetupAnim(EntityModel model, Entity entity, float limbSwing,
                                             float limbSwingAmount, float ageInTicks, float netHeadYaw,
                                             float headPitch) {
        if (entity instanceof EntityCustomNpc npc && npc.isKilled() && npc.isClientSide()) {
            model.setupAnim(entity, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        } else if (entity instanceof LivingEntity living && living.deathTime > 0 && living.level().isClientSide) {
            model.setupAnim(entity, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        } else {
            model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        }
    }

    @Inject(method = "isShaking", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$isShaking(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof EntityCustomNpc npc && npc.isKilled() && npc.isClientSide()) {
            cir.setReturnValue(false);
        }
    }
}