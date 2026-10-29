package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntityLiving;
import noppes.npcs.controllers.data.CloneSpawnData;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.JobSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 沿用 1.20.1：本体正常死亡、全部模式精确槽位、随机模式召唤死亡记忆。 */
@Mixin(value = JobSpawner.class, remap = false)
public class MixinJobSpawnerFix {
    @Shadow(remap = false)
    private int number;

    @Redirect(method = "aiUpdateTask", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityNPCInterface;discard()V"), require = 3)
    private void cnpcplus$dieInsteadOfDiscard(EntityNPCInterface npc) {
        npc.remove(Entity.RemovalReason.KILLED);
    }

    @Inject(method = "spawnEntity(I)Lnoppes/npcs/api/entity/IEntityLiving;",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$spawnOncePerSlot(int slot, CallbackInfoReturnable<IEntityLiving> cir) {
        JobSpawner self = (JobSpawner) (Object) this;
        if (self.spawnType != 1) return;
        if (self.npc == null || self.npc.level().isClientSide) {
            cir.setReturnValue(null);
            return;
        }
        CloneSpawnData sd = self.data.get(slot);
        CompoundTag compound = sd == null ? null : sd.getCompound();
        LivingEntity living = compound == null || !compound.contains("id") ? null
                : ((JobSpawnerInvoker) this).cnpcplus$spawnEntity(compound);
        cir.setReturnValue(living == null ? null : (IEntityLiving) NpcAPI.Instance().getIEntity(living));
    }

    @Redirect(method = "aiUpdateTask", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/roles/JobSpawner;spawnEntity(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/entity/LivingEntity;"), require = 1)
    private LivingEntity cnpcplus$randomSpawnOrDie(JobSpawner self, CompoundTag compound) {
        if (self.npc == null || self.npc.level().isClientSide) return null;
        if (!self.doesntDie && this.number > 0) {
            self.npc.remove(Entity.RemovalReason.KILLED);
            return null;
        }
        LivingEntity living = ((JobSpawnerInvoker) this).cnpcplus$spawnEntity(compound);
        if (living != null) this.number++;
        return living;
    }
}
