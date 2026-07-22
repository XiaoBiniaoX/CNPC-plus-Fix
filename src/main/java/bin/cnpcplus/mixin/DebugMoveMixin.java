package bin.cnpcplus.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityCustomNpc;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class DebugMoveMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    static {
        LOGGER.info("[DEBUG_MOVE_MIXIN] DebugMoveMixin loaded!");
    }

    @Inject(method = "m_6083_", at = @At("HEAD"), cancellable = false)
    private void cnpcplus$debugEntityTick(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof EntityCustomNpc && ((EntityCustomNpc) self).isKilled() && self.level().isClientSide) {
            LOGGER.info("[ENTITY_TICK] tick={} id={} pos=({},{},{}) delta=({},{},{})",
                self.tickCount, self.getId(),
                self.getX(), self.getY(), self.getZ(),
                self.getDeltaMovement().x, self.getDeltaMovement().y, self.getDeltaMovement().z);
        }
    }

    @Inject(method = "m_6478_", at = @At("HEAD"), cancellable = false)
    private void cnpcplus$debugMove(MoverType type, Vec3 delta, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof EntityCustomNpc && ((EntityCustomNpc) self).isKilled() && self.level().isClientSide) {
            LOGGER.info("[MOVE_CALLED] tick={} id={} delta=({},{},{})",
                self.tickCount, self.getId(),
                delta.x, delta.y, delta.z);
        }
    }

    @Inject(method = "m_20343_", at = @At("HEAD"), cancellable = false)
    private void cnpcplus$debugSetPos(double x, double y, double z, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof EntityCustomNpc && ((EntityCustomNpc) self).isKilled() && self.level().isClientSide) {
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            String caller = stack.length > 3 ? stack[3].toString() : "unknown";
            LOGGER.info("[SETPOS_CALLED] tick={} id={} pos=({},{},{}) caller={}",
                self.tickCount, self.getId(), x, y, z, caller);
        }
    }

    @Inject(method = "m_20219_", at = @At("HEAD"), cancellable = false)
    private void cnpcplus$debugSetOldPosAndRot(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof EntityCustomNpc && ((EntityCustomNpc) self).isKilled() && self.level().isClientSide) {
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            String caller = stack.length > 3 ? stack[3].toString() : "unknown";
            LOGGER.info("[SET_OLD_POS] tick={} id={} caller={}",
                self.tickCount, self.getId(), caller);
        }
    }
}