package top.cnpcplus.mixin;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.data.NpcHitboxStore;

@Mixin(value = {EntityNPCInterface.class, EntityCustomNpc.class}, priority = 900, remap = false)
public abstract class MixinEntityNPCHitboxDimensions {
    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true, require = 1)
    private void cnpcplus$rectangularHitbox(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        cir.setReturnValue(NpcHitboxStore.apply((EntityNPCInterface) (Object) this, cir.getReturnValue()));
    }
}
