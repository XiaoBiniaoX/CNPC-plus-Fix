package bin.cnpcplus.mixin.puppet;

import bin.cnpcplus.puppet.PartConfigAccessor;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import noppes.npcs.client.model.ModelBipedAlt;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.roles.JobPuppet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModelBipedAlt.class, remap = false)
public class MixinModelBipedAltBodyOffset {

    @Inject(method = "func_78087_a", at = @At("RETURN"))
    private void cnpcplus$applyBodyOffsets(float par1, float par2, float par3, float par4, float par5, float par6,
                                            Entity entity, CallbackInfo ci) {
        if (!(entity instanceof EntityCustomNpc)) return;
        EntityCustomNpc player = (EntityCustomNpc) entity;
        if (player.advanced == null || player.advanced.job != 9) return;
        if (!(player.jobInterface instanceof JobPuppet)) return;
        JobPuppet job = (JobPuppet) player.jobInterface;
        if (!job.isActive()) return;

        ModelBipedAlt self = (ModelBipedAlt) (Object) this;
        applyOff(self.bipedHead, job.head);
        applyOff(self.bipedHeadwear, job.head);
        applyOff(self.bipedBody, job.body);
        applyOff(self.bipedLeftArm, job.larm);
        applyOff(self.bipedRightArm, job.rarm);
        applyOff(self.bipedLeftLeg, job.lleg);
        applyOff(self.bipedRightLeg, job.rleg);
    }

    @Unique
    private static void applyOff(ModelRenderer part, JobPuppet.PartConfig cfg) {
        if (part == null || cfg == null || cfg.disabled) return;
        float ox = 0, oy = 0, oz = 0;
        if (cfg instanceof PartConfigAccessor) {
            PartConfigAccessor acc = (PartConfigAccessor) cfg;
            ox = acc.cnpcplus$getOffsetX();
            oy = acc.cnpcplus$getOffsetY();
            oz = acc.cnpcplus$getOffsetZ();
        } else {
            try {
                ox = cfg.getClass().getField("cnpcplusOffsetX").getFloat(cfg);
                oy = cfg.getClass().getField("cnpcplusOffsetY").getFloat(cfg);
                oz = cfg.getClass().getField("cnpcplusOffsetZ").getFloat(cfg);
            } catch (Throwable t) {
                return;
            }
        }
        if (ox == 0 && oy == 0 && oz == 0) return;
        part.rotationPointX += ox;
        part.rotationPointY += oy;
        part.rotationPointZ += oz;
    }
}
