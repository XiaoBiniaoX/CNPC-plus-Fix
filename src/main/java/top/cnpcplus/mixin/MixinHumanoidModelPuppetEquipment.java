package top.cnpcplus.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.roles.JobPuppet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.puppet.PartConfigAccessor;

@Mixin(value = HumanoidModel.class, priority = 100)
public class MixinHumanoidModelPuppetEquipment<T extends LivingEntity> {
    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void cnpcplus$applyPuppetModelOffsets(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof EntityCustomNpc npc) || npc.job.getType() != 9) return;
        JobPuppet job = (JobPuppet) npc.job;
        if (!job.isActive()) return;
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        cnpcplus$apply(job.head, model.head);
        cnpcplus$apply(job.body, model.body);
        cnpcplus$apply(job.larm, model.leftArm);
        cnpcplus$apply(job.rarm, model.rightArm);
        cnpcplus$apply(job.lleg, model.leftLeg);
        cnpcplus$apply(job.rleg, model.rightLeg);
    }

    private static void cnpcplus$apply(JobPuppet.PartConfig config, ModelPart part) {
        if (config == null || config.disabled || !(config instanceof PartConfigAccessor acc)) return;
        part.x += acc.cnpcplus$getOffsetX();
        part.y += acc.cnpcplus$getOffsetY();
        part.z += acc.cnpcplus$getOffsetZ();
        part.xRot += config.rotationX * (float) Math.PI;
        part.yRot += config.rotationY * (float) Math.PI;
        part.zRot += config.rotationZ * (float) Math.PI;
    }
}
