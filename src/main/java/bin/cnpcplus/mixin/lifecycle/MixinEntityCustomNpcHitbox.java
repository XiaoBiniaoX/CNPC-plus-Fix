package bin.cnpcplus.mixin.lifecycle;

import noppes.npcs.entity.EntityCustomNpc;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityCustomNpc.class, remap = false)
public class MixinEntityCustomNpcHitbox {
    @Inject(method = "updateHitbox", at = @At("TAIL"), remap = false)
    private void cnpcplus$refreshBoundingBox(CallbackInfo ci) {
        EntityCustomNpc npc = (EntityCustomNpc) (Object) this;
        Entity entity = (Entity) npc;
        float width = entity.width;
        float height = entity.height;
        npc.setEntityBoundingBox(new AxisAlignedBB(npc.posX - width / 2.0, npc.posY,
            npc.posZ - width / 2.0, npc.posX + width / 2.0,
            npc.posY + height, npc.posZ + width / 2.0));
    }
}
