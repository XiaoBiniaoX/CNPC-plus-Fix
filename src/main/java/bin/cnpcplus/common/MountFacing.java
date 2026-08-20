package bin.cnpcplus.common;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;

/**
 * Prepares a ridden npc so the movement code can actually move it.
 *
 * Facing has to be copied because travel()/moveRelative() resolve their
 * strafe/forward arguments relative to the entity's own facing, while a ridden
 * npc's facing is still whatever its AI last set. Without this the requested
 * movement largely cancels out.
 *
 * Speed has to be supplied because a ridden npc stops pathfinding, and every
 * speed value the movement code reads is owned by the pathfinding helpers:
 *
 *  - Ground (EntityLivingBase.travel, offsets 695..726) reads getAIMoveSpeed()
 *    when onGround. EntityMoveHelper.onUpdateMoveHelper only calls
 *    setAIMoveSpeed on its active branches; its WAIT branch (offset 546) just
 *    calls setMoveForward(0). A ridden npc sits in WAIT forever, so
 *    landMovementFactor keeps its constructor value of 0 -- any input
 *    multiplied by 0 is no movement at all.
 *
 *  - Flight (EntityNPCFlying.func_191986_a) never reads that field. It hardcodes
 *    the speed as 0.1f/0.02f instead, i.e. a vanilla player's walk speed, so a
 *    ridden flying npc ignored its own speed dial entirely.
 *
 * Lives outside the mixin package on purpose: classes in bin.cnpcplus.mixin.*
 * must all be @Mixin classes or the game dies with IllegalClassLoadError.
 */
public final class MountFacing {
    private MountFacing() {}

    /**
     * Ride speed as a multiple of the npc's own configured speed, so 1.0 is
     * "as fast as this npc walks by itself".
     *
     * The AI does not walk at 1.0x the raw attribute: EntityMoveHelper multiplies
     * the attribute by the per-task speed its caller passed to setMoveTo
     * (offsets 226..241). CNPC's own tasks pass 1.0 for wandering
     * (EntityAIWander.java:128) and 1.3 for chasing
     * (EntityAIAttackTarget.java:96). 1.3 is used here: a rider expects the mount
     * to move like a pursuing npc, not a strolling one.
     *
     * This is a feel value rather than a derivation, so it stays a knob.
     */
    public static final float RIDDEN_SPEED_FACTOR = 1.3F;

    /** Pitch is halved so looking straight down does not pitch the mount absurdly. */
    public static void faceLikeRider(EntityLivingBase mount, EntityLivingBase rider) {
        if (mount == null || rider == null) {
            return;
        }
        // setRotation is protected, so the public fields are assigned directly;
        // that is all setRotation does anyway.
        mount.rotationYaw = rider.rotationYaw;
        mount.prevRotationYaw = rider.rotationYaw;
        mount.rotationPitch = rider.rotationPitch * 0.5F;
        mount.prevRotationPitch = mount.rotationPitch;
        mount.renderYawOffset = mount.rotationYaw;
        mount.rotationYawHead = mount.rotationYaw;
    }

    /**
     * Replaces the stale pathfinding speed before travel() reads it.
     *
     * This mirrors what EntityMoveHelper does for the AI (offsets 226..241):
     * setAIMoveSpeed(taskSpeed * MOVEMENT_SPEED). CNPC's wander task passes 1.0
     * (EntityAIWander.java:128) and its chase tasks pass 1.3 to 1.33, so 1.0 here
     * matches an npc walking of its own accord.
     */
    public static void applyGroundSpeed(EntityLivingBase mount) {
        float speed = (float) mount.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED)
                .getAttributeValue();
        mount.setAIMoveSpeed(speed * RIDDEN_SPEED_FACTOR);
    }

    /** The speed EntityNPCFlying.java:67 hardcodes into its moveRelative argument. */
    private static final float FLIGHT_BASE_SPEED = 0.1F;

    /**
     * Steady sink applied while flying and not holding jump.
     *
     * Sneak cannot mean "descend" because that key dismounts, so descending has to
     * be the resting state. A quarter of the climb rate (0.25) reads as a glide
     * rather than a drop, and it still loses altitude while strafing.
     */
    public static final float FLIGHT_SINK = -0.0625F;

    /**
     * Rescales the acceleration EntityNPCFlying passes to moveRelative so flight
     * uses the npc's own speed instead of vanilla's hardcoded walk speed.
     *
     * The argument is an acceleration, and both paths build it the same way --
     * speed * (0.16277136 / slipperiness^3) on the ground, a bare constant in the
     * air -- except flight substitutes a literal 0.1f/0.02f where travel() reads
     * getAIMoveSpeed(). 0.1 is a vanilla player's walk speed; an npc's is
     * MOVEMENT_SPEED (0.25 at the default dial of 5), so flight ran at 0.1/0.25 of
     * ground speed regardless of the dial. Dividing out the 0.1 and multiplying the
     * real speed back in makes both paths agree.
     *
     * Note this is a ratio applied to whichever value vanilla picked, so the
     * ground/air distinction and the slipperiness term are preserved. Replacing the
     * argument outright is what broke flight earlier: the substituted value has to
     * stay in scale with the motion damping on the same branch.
     */
    public static float flightSpeed(EntityLivingBase mount, float vanillaAcceleration) {
        float speed = (float) mount.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED)
                .getAttributeValue();
        return vanillaAcceleration * (speed / FLIGHT_BASE_SPEED) * RIDDEN_SPEED_FACTOR;
    }
}
