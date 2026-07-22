package bin.cnpcplus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.client.renderer.RenderCustomNpc;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.mixin.WalkAnimationStateMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderCustomNpc.class)
public class RenderCustomNpcMixin {

    @Inject(method = "render(Lnoppes/npcs/entity/EntityCustomNpc;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = "Lnoppes/npcs/client/renderer/RenderNPCInterface;render(Lnoppes/npcs/entity/EntityNPCInterface;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", remap = false),
        remap = false)
    private void cnpcplus$freezeBeforeSuperRender(EntityCustomNpc npcParam, float entityYaw, float partialTicks,
                                                    PoseStack matrixStack, MultiBufferSource buffer, int packedLight,
                                                    CallbackInfo ci) {
        if (npcParam.isKilled() && npcParam.isClientSide()) {
            cnpcplus$freezeRenderState(npcParam);
        }
    }

    private static void cnpcplus$freezeRenderState(EntityCustomNpc npc) {
        npc.walkDist = 0.0F;
        npc.walkDistO = 0.0F;
        npc.walkAnimation.setSpeed(0.0F);
        ((WalkAnimationStateMixin)(Object)npc.walkAnimation).setSpeedOld(0.0F);
        ((WalkAnimationStateMixin)(Object)npc.walkAnimation).setPosition(0.0F);
        npc.yBodyRotO = npc.yBodyRot;
        npc.yHeadRotO = npc.yHeadRot;
        npc.yRotO = npc.getYRot();
        npc.xRotO = npc.getXRot();
        npc.setXRot(0.0F);
        npc.xRotO = 0.0F;
        npc.attackAnim = 0.0F;
        npc.oAttackAnim = 0.0F;
        npc.swingTime = 0;
        npc.setDeltaMovement(0.0, 0.0, 0.0);
        npc.hurtTime = 0;

        LivingEntity modelEntity = npc.modelData.getEntity(npc);
        if (modelEntity != null) {
            modelEntity.walkDist = 0.0F;
            modelEntity.walkDistO = 0.0F;
            modelEntity.walkAnimation.setSpeed(0.0F);
            ((WalkAnimationStateMixin)(Object)modelEntity.walkAnimation).setSpeedOld(0.0F);
            ((WalkAnimationStateMixin)(Object)modelEntity.walkAnimation).setPosition(0.0F);
            modelEntity.yBodyRotO = modelEntity.yBodyRot;
            modelEntity.yHeadRotO = modelEntity.yHeadRot;
            modelEntity.yRotO = modelEntity.getYRot();
            modelEntity.xRotO = 0.0F;
            modelEntity.setXRot(0.0F);
            modelEntity.attackAnim = 0.0F;
            modelEntity.oAttackAnim = 0.0F;
            modelEntity.swingTime = 0;
            modelEntity.setDeltaMovement(0.0, 0.0, 0.0);
            modelEntity.deathTime = Math.min(npc.deathTime, 20);
            modelEntity.hurtTime = 0;
        }
    }
}