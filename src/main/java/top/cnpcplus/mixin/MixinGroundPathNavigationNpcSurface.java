package top.cnpcplus.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GroundPathNavigation.class)
public abstract class MixinGroundPathNavigationNpcSurface extends PathNavigation {
    protected MixinGroundPathNavigationNpcSurface(Mob mob, Level level) { super(mob, level); }

    @Inject(method = "getSurfaceY", at = @At("RETURN"), cancellable = true)
    private void cnpcplus$surfaceAboveFurniture(CallbackInfoReturnable<Integer> cir) {
        if (!(mob instanceof EntityNPCInterface) || !mob.onGround() || mob.isInWater()) return;
        int y = Mth.ceil(mob.getY());
        if (y <= cir.getReturnValue()) return;
        BlockPos support = BlockPos.containing(mob.getX(), mob.getY(), mob.getZ());
        if (!level.getBlockState(support).getCollisionShape(level, support).isEmpty()
                && level.noCollision(mob, mob.getBoundingBox().deflate(1.0E-5))) {
            cir.setReturnValue(y);
        }
    }
}
