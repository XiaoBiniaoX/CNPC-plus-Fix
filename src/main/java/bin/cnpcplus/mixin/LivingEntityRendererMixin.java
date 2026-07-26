package bin.cnpcplus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Inject(method = "setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At("HEAD"), remap = false)
    private void cnpcplus$freezeSetupRotations(LivingEntity entity, PoseStack poseStack, float bob, float yBodyRot,
                                                float partialTick, float scale, CallbackInfo ci) {
        if (entity instanceof EntityNPCInterface npc && npc.isKilled()) {
            entity.walkDist = 0.0F;
            entity.walkDistO = 0.0F;
            entity.attackAnim = 0.0F;
            entity.oAttackAnim = 0.0F;
        }
    }
}