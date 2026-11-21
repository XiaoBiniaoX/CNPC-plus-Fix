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

@Mixin(MoveControl.class)
public class MixinReturnHomeMoveControl {
    @Shadow @Final protected Mob mob;

    @Inject(method = "tick", at = @At("HEAD"))
    private void cnpcplus$walkFinalGap(CallbackInfo ci) {
        if (!(mob instanceof EntityNPCInterface npc)) return;
        var target = ReturnHomeState.finalApproach(npc);
        if (target == null) return;

        if (ReturnHomeState.arrived(npc, target)) {
            npc.getMoveControl().setWantedPosition(npc.getX(), npc.getY(), npc.getZ(), 0);
        } else {
            double distance = Math.hypot(target.x - npc.getX(), target.z - npc.getZ());
            double speed = npc.getAttributeValue(Attributes.MOVEMENT_SPEED);
            double speedModifier = speed > 0 ? Math.min(1.0, distance * 0.6 / speed) : 0;
            npc.getMoveControl().setWantedPosition(target.x, target.y, target.z, speedModifier);
        }
    }
}
