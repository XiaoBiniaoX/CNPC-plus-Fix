package top.cnpcplus.mixin;

import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.JobChunkLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class MixinEntityCanUpdateChunkLoader {
    // Forge has canUpdate()Z and canUpdate(Z)V — must target the getter explicitly
    @Inject(method = "canUpdate()Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$forceChunkLoaderNpcCanUpdate(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof EntityNPCInterface npc && npc.job instanceof JobChunkLoader) {
            cir.setReturnValue(true);
        }
    }
}
