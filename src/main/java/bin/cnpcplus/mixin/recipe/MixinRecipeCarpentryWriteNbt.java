package bin.cnpcplus.mixin.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.data.RecipeCarpentry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * OFFICIAL BUG: writeNBT putString("Group"/"Name") when null -> NPE on Save.
 */
@Mixin(RecipeCarpentry.class)
public class MixinRecipeCarpentryWriteNbt {

    @Inject(method = "writeNBT", at = @At("HEAD"), remap = false)
    private void cnpcplusSanitize(HolderLookup.Provider provider, CallbackInfoReturnable<CompoundTag> cir) {
        RecipeCarpentry self = (RecipeCarpentry) (Object) this;
        if (self.name == null) {
            self.name = "unnamed";
        }
        Object g = cnpcplusGetField(self, "group");
        if (g == null) {
            cnpcplusSetField(self, "group", "");
        }
    }

    private static Object cnpcplusGetField(Object self, String name) {
        Class<?> c = self.getClass();
        while (c != null) {
            try {
                var f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(self);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private static void cnpcplusSetField(Object self, String name, Object value) {
        Class<?> c = self.getClass();
        while (c != null) {
            try {
                var f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(self, value);
                return;
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            } catch (Exception e) {
                return;
            }
        }
    }
}