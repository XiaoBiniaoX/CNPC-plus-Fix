package bin.cnpcplus.mixin.mount;

import bin.cnpcplus.common.IMountControlData;
import bin.cnpcplus.common.IMountControlInput;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import noppes.npcs.entity.EntityNPCFlying;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Rider input for flying NPCs. Only movementType 1 (flying) is controlled;
 * movementType 2 (swimming) keeps the vanilla behaviour.
 * ais is inherited from EntityNPCInterface and public, so no @Shadow is used.
 */
@Mixin(value = EntityNPCFlying.class, remap = false)
public abstract class MixinEntityNPCFlyingMountControl {
    // Verified against the shipped jar: this call is owned by EntityNPCFlying itself,
    // and it appears three times (water, lava, air), so every branch is covered.
    // require = 1 because the late config sets defaultRequire 0, which would let a
    // mismatch here fail silently and look like "riding just does not work".
    @ModifyArgs(method = "func_191986_a", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityNPCFlying;func_191958_b(FFFF)V"),
            remap = false, require = 1)
    private void cnpcplus$applyFlyingMountInput(Args args) {
        EntityNPCFlying npc = (EntityNPCFlying) (Object) this;
        if (npc.ais == null || npc.ais.movementType != 1) {
            return;
        }
        List<Entity> passengers = npc.getPassengers();
        Entity rider = passengers.isEmpty() ? null : passengers.get(0);
        if (!((IMountControlData) (Object) npc.ais).cnpcplus$getMountControl()
                || !(rider instanceof EntityPlayer)) {
            return;
        }
        bin.cnpcplus.common.MountFacing.faceLikeRider(npc, (EntityPlayer) rider);
        IMountControlInput input = (IMountControlInput) (Object) this;
        args.set(0, input.cnpcplus$getMountStrafe());
        // Space climbs; releasing it sinks slowly. Sneak is not usable as "descend"
        // because that key dismounts, so gravity has to be the default state.
        args.set(1, input.cnpcplus$getMountJump()
                ? 0.25f : bin.cnpcplus.common.MountFacing.FLIGHT_SINK);
        args.set(2, input.cnpcplus$getMountForward());
        // Rescale the acceleration vanilla picked rather than replacing it: this
        // branch hardcodes a vanilla player's walk speed, which is why flight ignored
        // the npc's speed dial and crawled. See MountFacing.flightSpeed.
        args.set(3, bin.cnpcplus.common.MountFacing.flightSpeed(
                npc, ((Float) args.get(3)).floatValue()));
    }
}
