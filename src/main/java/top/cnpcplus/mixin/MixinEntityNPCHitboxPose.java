package top.cnpcplus.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.data.NpcRectangularDimensions;

@Mixin(Entity.class)
public abstract class MixinEntityNPCHitboxPose {
    @Shadow private EntityDimensions dimensions;

    // Vanilla's growth branch builds a square from width, bypassing makeBoundingBox.
    // This also covers Gecko's host refresh after its separate model changes size.
    @Inject(method = "refreshDimensions", at = @At("RETURN"), require = 1)
    private void cnpcplus$recenterRectangularRefresh(CallbackInfo ci) {
        if ((Object) this instanceof EntityNPCInterface npc && dimensions instanceof NpcRectangularDimensions) {
            npc.setPos(npc.getX(), npc.getY(), npc.getZ());
        }
    }

    // Vanilla's pose helper constructs a square directly instead of calling makeBoundingBox.
    @Inject(method = "getBoundingBoxForPose", at = @At("RETURN"), cancellable = true, require = 1)
    private void cnpcplus$rectangularPose(Pose pose, CallbackInfoReturnable<AABB> cir) {
        if ((Object) this instanceof EntityNPCInterface npc) {
            EntityDimensions size = npc.getDimensions(pose);
            if (size instanceof NpcRectangularDimensions) {
                cir.setReturnValue(size.makeBoundingBox(npc.position()));
            }
        }
    }
}
