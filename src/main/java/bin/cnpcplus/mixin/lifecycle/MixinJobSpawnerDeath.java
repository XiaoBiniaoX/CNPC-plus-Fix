package bin.cnpcplus.mixin.lifecycle;

import bin.cnpcplus.common.RespawnCycleStore;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.JobSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A summoner's empty-spawn exits are deaths, not permanent entity deletion. */
@Mixin(value = JobSpawner.class, remap = false)
public class MixinJobSpawnerDeath {
    @Redirect(method = "aiUpdateTask", at = @At(value = "INVOKE", target = "Lnoppes/npcs/entity/EntityNPCInterface;func_70106_y()V"), remap = false)
    private void cnpcplus$normalDeath(EntityNPCInterface npc) {
        RespawnCycleStore.forceRespawn(npc);
        npc.func_70106_y();
    }
}
