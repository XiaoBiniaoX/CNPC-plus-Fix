package bin.cnpcplus.mixin.ai;

import bin.cnpcplus.common.INpcGroundNavigation;
import net.minecraft.entity.EntityLiving;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PathNavigateGround.class)
public abstract class MixinPathNavigateGroundNpcSurface extends PathNavigate {
    protected MixinPathNavigateGroundNpcSurface(EntityLiving entity, World world) {
        super(entity, world);
    }

    @Inject(method = "getPathablePosY", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$aboveSurface(CallbackInfoReturnable<Integer> cir) {
        if (entity instanceof INpcGroundNavigation && entity.onGround
                && !entity.isInWater() && !entity.isInLava()) {
            cir.setReturnValue(MathHelper.floor(entity.getEntityBoundingBox().minY + 0.999999D));
        }
    }
}
