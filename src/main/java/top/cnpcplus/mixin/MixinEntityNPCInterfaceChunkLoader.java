package top.cnpcplus.mixin;

import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.JobChunkLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCInterfaceChunkLoader {

    @Shadow
    public noppes.npcs.roles.JobInterface job;

    @Inject(method = "m_8119_", at = @At("RETURN"))
    private void cnpcplus$tickChunkLoaderJob(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (npc.level().isClientSide || !(this.job instanceof JobChunkLoader loader)) return;
        npc.canUpdate(true);
        if (npc.tickCount % 20 == 0) {
            loader.aiShouldExecute();
        }
    }

}
