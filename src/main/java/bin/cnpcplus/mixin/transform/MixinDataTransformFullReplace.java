package bin.cnpcplus.mixin.transform;

import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.controllers.data.DataTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 238-8：昼夜外观切换时整体替换 display/modelData，避免 NBTMerge 残留旧模型键。 */
@Mixin(value = DataTransform.class, remap = false)
public abstract class MixinDataTransformFullReplace {

    @Inject(method = "readToNBT", at = @At("TAIL"), require = 1)
    private void cnpcplus$clearPersistedEditing(NBTTagCompound tag, CallbackInfo ci) {
        ((DataTransform) (Object) this).editingModus = false;
    }

    @Redirect(method = "transform", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/NBTTags;NBTMerge(Lnet/minecraft/nbt/NBTTagCompound;Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;",
            ordinal = 0), remap = false, require = 1)
    private NBTTagCompound cnpcplus$replaceDisplay(NBTTagCompound current, NBTTagCompound target) {
        return target;
    }

    @Redirect(method = "transform", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/NBTTags;NBTMerge(Lnet/minecraft/nbt/NBTTagCompound;Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;",
            ordinal = 1), remap = false, require = 1)
    private NBTTagCompound cnpcplus$replaceModelData(NBTTagCompound current, NBTTagCompound target) {
        return target;
    }
}
