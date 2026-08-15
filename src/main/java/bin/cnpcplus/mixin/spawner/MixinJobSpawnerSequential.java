package bin.cnpcplus.mixin.spawner;

import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.api.entity.IEntityLivingBase;
import noppes.npcs.roles.JobSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = JobSpawner.class, remap = false)
public class MixinJobSpawnerSequential {

    @Redirect(method = "aiUpdateTask", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/roles/JobSpawner;spawnEntity(I)Lnoppes/npcs/api/entity/IEntityLivingBase;"),
            remap = false)
    private IEntityLivingBase cnpcplus$fixSequentialIndex(JobSpawner self, int nextSlot) {
        return self.spawnEntity(nextSlot - 1);
    }

    // 回退：历史 compound 没有 ClonedName，但有 NPC 的 "Name"
    @Inject(method = "getTitle(Lnet/minecraft/nbt/NBTTagCompound;)Ljava/lang/String;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$getTitleFallback(NBTTagCompound compound, CallbackInfoReturnable<String> cir) {
        if (compound == null) return;
        if (compound.hasKey("ClonedName")) return; // 让原版处理
        if (compound.hasKey("Name")) {
            cir.setReturnValue(compound.getString("Name"));
        }
    }
}
