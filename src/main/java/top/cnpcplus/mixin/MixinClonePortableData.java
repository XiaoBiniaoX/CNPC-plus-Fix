package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.ServerCloneController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.persist.NpcDataExternalStore;

@Mixin(value = ServerCloneController.class, remap = false)
public abstract class MixinClonePortableData {
    @Inject(method = "cleanTags", at = @At("HEAD"), require = 1)
    private void cnpcplus$materializeBeforeUuidRemoval(CompoundTag tag, CallbackInfo ci) {
        NpcDataExternalStore.makeAllPortable(tag);
    }

    @Inject(method = "cleanTags", at = @At("RETURN"), require = 1)
    private void cnpcplus$cleanPassengers(CompoundTag tag, CallbackInfo ci) {
        CompoundTag data = tag.getCompound("ForgeData");
        data.remove("NpcSpawnerId");
        data.remove("NpcSpawnerNr");
        data.remove("CNPCPlusSpawnGroup");
        for (var child : tag.getList("Passengers", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            ((ServerCloneController)(Object)this).cleanTags((CompoundTag)child);
        }
    }
}
