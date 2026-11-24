package top.cnpcplus.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.ai.ReturnHomeState;

@Mixin(value = MoveControl.class, remap = false)
public abstract class MixinReturnHomeMoveControl {
    @Shadow @Final protected Mob mob;
    @Shadow public abstract void setWantedPosition(double x, double y, double z, double speed);

    @Inject(method = "tick", at = @At("HEAD"), require = 1)
    private void cnpcplus$walkFinalGap(CallbackInfo ci) {
        if (!(mob instanceof EntityNPCInterface npc)) return;
        var target = ReturnHomeState.finalApproach(npc);
        if (target == null) return;
        npc.setXxa(0);

        if (ReturnHomeState.arrived(npc, target)) {
            setWantedPosition(npc.getX(), npc.getY(), npc.getZ(), 0);
            npc.setDeltaMovement(0, npc.getDeltaMovement().y, 0);
        } else {
            double distance = Math.hypot(target.x - npc.getX(), target.z - npc.getZ());
            double speed = npc.getAttributeValue(Attributes.MOVEMENT_SPEED);
            // CNPC.getSpeed returns the configured speed again during travel,
            // independently of MoveControl's setSpeed (which also writes zza).
            double speedModifier = speed > 0
                    ? Math.min(1.0, distance * 0.6 / (speed * Math.max(1.0, npc.getSpeed()))) : 0;
            setWantedPosition(target.x, target.y, target.z, speedModifier);
        }
    }
}
