package bin.cnpcplus.mixin.mount;

import noppes.npcs.ai.EntityAIReturn;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps a ridden NPC from walking (or teleporting) back to its start position
 * while a player is sitting on it. Once the player dismounts, the task starts
 * again on the next tick and the NPC returns home as usual.
 *
 * Vanilla CNPC only checks func_184218_aH (isRiding: the NPC is a passenger on
 * something else) and never checks isBeingRidden, so EntityAIReturn keeps
 * running while a player rides the NPC. func_75246_d then drags the NPC home
 * and, after 600 ticks or 5 stuck checks, calls func_70107_b to hard teleport
 * it (EntityAIReturn L98-110), taking the rider along.
 *
 * Both func_75250_a (shouldExecute) and func_75253_b (shouldContinueExecuting)
 * already call func_184218_aH exactly once each, verified against the shipped
 * jar (constant pool #58, owner noppes/npcs/entity/EntityNPCInterface).
 * Redirecting that single call covers both methods and hands us the npc
 * instance for free, so the private final EntityAIReturn.npc field is never
 * touched (@Shadow against noppes private fields is unreliable, see findings.md).
 *
 * Guarding shouldExecute alone would let an already running return task finish,
 * including the teleport; guarding shouldContinueExecuting alone would still
 * start the task for one tick and issue a path request every time.
 */
@Mixin(value = EntityAIReturn.class, remap = false)
public class MixinEntityAIReturnMount {
    @Redirect(method = {"func_75250_a", "func_75253_b"}, at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityNPCInterface;func_184218_aH()Z"), remap = false)
    private boolean cnpcplus$blockReturnWhileRidden(EntityNPCInterface npc) {
        // isRiding is the vanilla condition; isBeingRidden is the case CNPC misses.
        return npc.isRiding() || npc.isBeingRidden();
    }
}
