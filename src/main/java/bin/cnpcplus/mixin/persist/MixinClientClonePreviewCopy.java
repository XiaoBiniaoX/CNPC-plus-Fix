package bin.cnpcplus.mixin.persist;

import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.client.gui.GuiNpcMobSpawnerAdd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Native compatibility loaders may mutate their input; never mutate the JSON export draft. */
@Mixin(value = GuiNpcMobSpawnerAdd.class, remap = false)
public abstract class MixinClientClonePreviewCopy {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityList;func_75615_a(Lnet/minecraft/nbt/NBTTagCompound;Lnet/minecraft/world/World;)Lnet/minecraft/entity/Entity;"), index = 0, require = 1)
    private NBTTagCompound cnpcplus$detachedPreview(NBTTagCompound tag) { return tag.copy(); }
}
