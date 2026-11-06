package bin.cnpcplus.mixin.persist;

import bin.cnpcplus.data.NpcExternalData;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.controllers.ServerCloneController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerCloneController.class, remap = false)
public abstract class MixinClonePortableData {
    @Inject(method = "saveClone", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$portableFile(int tab, String name, NBTTagCompound tag, CallbackInfo ci) {
        // saveClone is also called directly (script API and old format migration).
        // Never replace a good client JSON with an unresolved world-local reference.
        if (!NpcExternalData.makePortable(tag)) ci.cancel();
    }
    @Inject(method = "cleanTags", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$materialize(NBTTagCompound tag, CallbackInfo ci) {
        if (!NpcExternalData.makePortable(tag)) ci.cancel();
    }

    @Inject(method = "cleanTags", at = @At("RETURN"), require = 1)
    private void cnpcplus$cleanPassengers(NBTTagCompound tag, CallbackInfo ci) {
        NBTTagCompound data = tag.getCompoundTag("ForgeData");
        for (String key : new String[]{"NpcSpawnerId", "NpcSpawnerNr", "CNPCPlusSpawnGroup"}) data.removeTag(key);
        NBTTagList children = tag.getTagList("Passengers", 10);
        for (int i = 0; i < children.tagCount(); i++) {
            ((ServerCloneController) (Object) this).cleanTags(children.getCompoundTagAt(i));
        }
    }
}
