package bin.cnpcplus.mixin.mount;

import bin.cnpcplus.common.IMountControlData;
import bin.cnpcplus.common.IMountControlInput;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import java.util.List;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

// EntityNPCInterface.ais is public, so it is read through a cast instead of @Shadow.
@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinEntityNPCMountControl implements IMountControlInput {
    @Unique private float cnpcplus$strafe;
    @Unique private float cnpcplus$forward;
    @Unique private boolean cnpcplus$jump;
    @Unique private boolean cnpcplus$sneak;

    @Override
    public void cnpcplus$setMountInput(float strafe, float forward, boolean jump, boolean sneak) {
        this.cnpcplus$strafe = strafe;
        this.cnpcplus$forward = forward;
        this.cnpcplus$jump = jump;
        this.cnpcplus$sneak = sneak;
    }

    @Override public float cnpcplus$getMountStrafe() { return this.cnpcplus$strafe; }
    @Override public float cnpcplus$getMountForward() { return this.cnpcplus$forward; }
    @Override public boolean cnpcplus$getMountJump() { return this.cnpcplus$jump; }
    @Override public boolean cnpcplus$getMountSneak() { return this.cnpcplus$sneak; }

    /**
     * Lets the client run its own movement code for a ridden npc.
     *
     * travel() only proceeds when isServerWorld() or canPassengerSteer() holds
     * (offsets 0..11). EntityLiving overrides canPassengerSteer() to require
     * canBeSteered() first (offsets 3060..3071), and EntityLiving.canBeSteered()
     * is a hardcoded "return false" that no CNPC class overrides. So the client
     * never moved a ridden npc at all: it only advanced when a server position
     * packet arrived, which is the stutter felt while riding.
     *
     * Overriding getControllingPassenger instead does not help, because
     * canBeSteered() is checked first, and it has a side effect: Entity.addPassenger
     * (offsets 29..57) picks list.add(0, x) versus list.add(x) based on whether
     * getControllingPassenger() is already a player, so overriding it reorders the
     * passenger list.
     *
     * Declared rather than injected: no CNPC class declares this method, so there
     * is no target to inject into and the mixin contributes the override itself.
     */
    public boolean func_82171_bF() {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (npc.ais == null
                || !((IMountControlData) (Object) npc.ais).cnpcplus$getMountControl()) {
            return false;
        }
        List<Entity> passengers = npc.getPassengers();
        return !passengers.isEmpty() && passengers.get(0) instanceof EntityPlayer;
    }

    // Verified against the shipped jar: the super call is owned by EntityCreature, not EntityLivingBase.
    // require = 1 because the late config sets defaultRequire 0, which would let a
    // mismatch here fail silently and look like "riding just does not work".
    @ModifyArgs(method = "func_191986_a", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/EntityCreature;func_191986_a(FFF)V"),
            remap = false, require = 1)
    private void cnpcplus$applyMountInput(Args args) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (npc.ais == null) return;
        List<Entity> passengers = npc.getPassengers();
        Entity rider = passengers.isEmpty() ? null : passengers.get(0);
        if (!((IMountControlData) (Object) npc.ais).cnpcplus$getMountControl()
                || !(rider instanceof EntityPlayer)
                || npc.ais.movementType == 2) {
            return;
        }
        bin.cnpcplus.common.MountFacing.faceLikeRider(npc, (EntityPlayer) rider);
        // Without this the npc sits at landMovementFactor 0 and never moves; see MountFacing.
        bin.cnpcplus.common.MountFacing.applyGroundSpeed(npc);
        args.set(0, this.cnpcplus$strafe);
        args.set(2, this.cnpcplus$forward);
        if (npc.ais.movementType == 1) {
            args.set(1, this.cnpcplus$jump ? 0.25f : this.cnpcplus$sneak ? -0.25f : 0.0f);
        }
    }

    /**
     * Ground jumping. travel()'s second argument does nothing on the ground path:
     * EntityLivingBase.onLivingUpdate reads the isJumping field instead
     * (offsets 389..445 -> isInWater ? handleJumpWater : onGround && jumpTicks == 0
     * ? jump()), which is also how EntityPlayerMP.setEntityActionState feeds a
     * ridden vanilla mount. So the rider's jump key has to be written to that field.
     *
     * Injected at HEAD of onLivingUpdate so the field is set before the jump block
     * runs in the same tick. setJumping is public on EntityLivingBase, so no
     * @Shadow of the protected field is needed.
     *
     * Goes through the jump helper rather than setJumping directly. Writing the
     * field here does not survive: onLivingUpdate calls updateEntityActionState
     * (offset 353) before it reads the field (offset 389), and that calls
     * EntityJumpHelper.doJump (offsets 305..308), which unconditionally does
     * setJumping(helper.isJumping) -- false unless the helper was asked to jump.
     * So a value written at HEAD is overwritten every tick. The helper is the only
     * writer vanilla respects, and getJumpHelper() is public.
     *
     * Restricted to movementType 0. A flying npc already gets lift from the flight
     * hook's second travel argument, and jump() writes motionY directly, so allowing
     * both would stack two upward impulses per tick and launch the npc.
     *
     * No extra "already airborne" guard is needed: vanilla only jumps when
     * onGround && jumpTicks == 0 (offsets 424..445), and jumpTicks is set to 10
     * after each jump, so a held key repeats at most once per landing.
     */
    @Inject(method = "func_70636_d", at = @At("HEAD"), remap = false, require = 1)
    private void cnpcplus$applyMountJump(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (npc.ais == null || npc.ais.movementType != 0
                || !((IMountControlData) (Object) npc.ais).cnpcplus$getMountControl()) {
            return;
        }
        List<Entity> passengers = npc.getPassengers();
        if (passengers.isEmpty() || !(passengers.get(0) instanceof EntityPlayer)) {
            return;
        }
        if (this.cnpcplus$jump && npc.onGround) {
            npc.getJumpHelper().setJumping();
        }
    }

}
