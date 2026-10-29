package top.cnpcplus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import noppes.npcs.client.renderer.RenderProjectile;
import noppes.npcs.entity.EntityProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.ranged.RangedSpinStore;

/** Continuously rotates 3D projectile item models using the accumulated XYZ spin angles. */
@Mixin(value = RenderProjectile.class, remap = false)
public class MixinRenderProjectileRoll {

    private static final String RENDER_METHOD =
            "render(Lnoppes/npcs/entity/EntityProjectile;FFLcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @Inject(method = RENDER_METHOD, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderStatic(Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;IILcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;I)V",
            ordinal = 0, shift = At.Shift.BEFORE), require = 1, remap = false)
    private void cnpcplus$applyContinuousSpin(EntityProjectile projectile, float entityYaw, float partialTicks,
                                                PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                                CallbackInfo ci) {
        if (projectile.isArrow() || !projectile.is3D()) return;
        float x = RangedSpinStore.angleOf(projectile, RangedSpinStore.AXIS_X, partialTicks);
        float y = RangedSpinStore.angleOf(projectile, RangedSpinStore.AXIS_Y, partialTicks);
        float z = RangedSpinStore.angleOf(projectile, RangedSpinStore.AXIS_Z, partialTicks);
        if (x == 0.0f && y == 0.0f && z == 0.0f) return;

        // The hook is immediately before RenderProjectile submits its 3D item
        // to ItemRenderer, after yaw/pitch were applied to the projectile pose.
        poseStack.mulPose(Axis.XP.rotationDegrees(x));
        poseStack.mulPose(Axis.YP.rotationDegrees(y));
        poseStack.mulPose(Axis.ZP.rotationDegrees(z));
    }
}
