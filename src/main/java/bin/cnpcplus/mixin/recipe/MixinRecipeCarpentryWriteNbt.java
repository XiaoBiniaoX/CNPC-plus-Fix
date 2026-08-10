package bin.cnpcplus.mixin.recipe;

import bin.cnpcplus.recipe.RecipeNbtKeys;
import bin.cnpcplus.recipe.id.RecipeIds;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.controllers.data.RecipeCarpentry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeCarpentry.class)
public class MixinRecipeCarpentryWriteNbt {

    @Inject(method = "writeNBT", at = @At("HEAD"), remap = false)
    private void cnpcplusSanitize(CallbackInfoReturnable<NBTTagCompound> cir) {
        RecipeCarpentry self = (RecipeCarpentry) (Object) this;
        if (self.name == null) {
            self.name = "unnamed";
        }
    }

    @Inject(method = "writeNBT", at = @At("RETURN"), remap = false)
    private void cnpcplusAttachSyncId(CallbackInfoReturnable<NBTTagCompound> cir) {
        NBTTagCompound tag = cir.getReturnValue();
        if (tag == null) return;
        RecipeCarpentry self = (RecipeCarpentry) (Object) this;
        Integer sync = RecipeIds.INSTANCE.syncIdOfRecipe(self);
        if (sync == null && self.id > 0) {
            sync = Integer.valueOf(self.id);
        }
        if (sync == null && self.name != null) {
            sync = RecipeIds.INSTANCE.syncIdByName(self.name);
        }
        if (sync != null && sync.intValue() > 0) {
            tag.setInteger(RecipeNbtKeys.SYNC_ID, sync.intValue());
            tag.setInteger("ID", sync.intValue());
        }
        if (self.name != null) {
            tag.setString("Name", self.name);
        }
    }
}
