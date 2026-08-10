package bin.cnpcplus.mixin.recipe;

import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.controllers.data.RecipeCarpentry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ensure name is never empty after read.
 */
@Mixin(RecipeCarpentry.class)
public class MixinRecipeCarpentryLoadName {

    @Inject(method = "read", at = @At("RETURN"), remap = false)
    private static void cnpcplusLoadName(NBTTagCompound compound, CallbackInfoReturnable<RecipeCarpentry> cir) {
        RecipeCarpentry recipe = cir.getReturnValue();
        if (recipe == null || compound == null) return;
        if (compound.hasKey("Name")) {
            recipe.name = compound.getString("Name");
        }
        if (recipe.name == null || recipe.name.isEmpty()) {
            recipe.name = "unnamed";
        }
    }
}
