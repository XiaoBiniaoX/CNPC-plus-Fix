package top.cnpcplus.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataAI;
import noppes.npcs.mixin.EntityLivingIMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCFlyingFix {
    @Shadow(remap = false) public DataAI ais;
    @Unique private boolean cnpcplus$deferredCollision;

    @Redirect(method = "m_8107_", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityNPCInterface;onCollide()V"))
    private void cnpcplus$deferFlyingCollision(EntityNPCInterface npc) {
        if (this.ais.movementType == 1) {
            this.cnpcplus$deferredCollision = true;
        } else {
            npc.onCollide();
        }
    }

    @Inject(method = "m_8107_", at = @At("RETURN"))
    private void cnpcplus$checkFlyingCollisionAfterMove(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (!this.cnpcplus$deferredCollision) return;
        this.cnpcplus$deferredCollision = false;
        if (!npc.isClientSide()) npc.onCollide();
    }

    @ModifyArg(method = "m_7023_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/PathfinderMob;m_7023_(Lnet/minecraft/world/phys/Vec3;)V",
            ordinal = 0), index = 0)
    private Vec3 cnpcplus$verticalFlightControl(Vec3 movement) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        LivingEntity rider = npc.getControllingPassenger();
        if (this.ais.movementType != 1 || !this.ais.mountControl || rider == null) return movement;
        double vertical = ((EntityLivingIMixin) rider).jumping() ? 1.0 : rider.isShiftKeyDown() ? -1.0 : movement.y;
        return new Vec3(movement.x, vertical, movement.z);
    }
}
