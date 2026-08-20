package bin.cnpcplus.mixin.lifecycle;

import bin.cnpcplus.common.RespawnCycleStore;
import noppes.npcs.entity.EntityNPCInterface;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Re-apply the final dimensions after CNPC changes the raw width/height fields. */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCInterfaceHitbox {
    @Inject(method = "reset", at = @At("TAIL"), remap = false)
    private void cnpcplus$restoreSpawnCycle(CallbackInfo ci) {
        RespawnCycleStore.restore((EntityNPCInterface) (Object) this);
    }

    @Inject(method = "updateHitbox", at = @At("TAIL"), remap = false)
    private void cnpcplus$refreshBoundingBox(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        Entity entity = (Entity) npc;
        float width = entity.width;
        float height = entity.height;
        npc.setEntityBoundingBox(new AxisAlignedBB(npc.posX - width / 2.0, npc.posY,
            npc.posZ - width / 2.0, npc.posX + width / 2.0,
            npc.posY + height, npc.posZ + width / 2.0));
    }
}
