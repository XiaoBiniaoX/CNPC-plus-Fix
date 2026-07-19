package bin.cnpcplus.mixin.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.data.RecipeCarpentry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * OFFICIAL STUB gap: RecipeCarpentry.load never assigns name; group may be null.
 */
@Mixin(RecipeCarpentry.class)
public class MixinRecipeCarpentryLoadName {

    @Inject(method = "load", at = @At("RETURN"), remap = false)
    private static void cnpcplusLoadName(CompoundTag compound, HolderLookup.Provider provider, CallbackInfoReturnable<RecipeCarpentry> cir) {
        RecipeCarpentry recipe = cir.getReturnValue();
        if (recipe == null || compound == null) return;
        if (compound.contains("Name")) {
            recipe.name = compound.getString("Name");
        }
        if (recipe.name == null || recipe.name.isEmpty()) {
            recipe.name = "unnamed";
        }
        String group = compound.contains("Group") ? compound.getString("Group") : "";
        if (group == null) group = "";
        cnpcplusSetGroup(recipe, group);
    }

    private static void cnpcplusSetGroup(Object recipe, String group) {
        Class<?> c = recipe.getClass();
        while (c != null) {
            try {
                var f = c.getDeclaredField("group");
                f.setAccessible(true);
                f.set(recipe, group);
                return;
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            } catch (Exception e) {
                return;
            }
        }
    }
}