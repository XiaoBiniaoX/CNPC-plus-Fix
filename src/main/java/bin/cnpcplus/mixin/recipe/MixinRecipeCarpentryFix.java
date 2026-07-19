package bin.cnpcplus.mixin.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.ShapedRecipe;
import noppes.npcs.controllers.data.RecipeCarpentry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * OFFICIAL BUG: RecipeCarpentry.getWidth/getHeight/getResult recurse into themselves.
 */
@Mixin(RecipeCarpentry.class)
public class MixinRecipeCarpentryFix {

    @Inject(method = "getWidth", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusGetWidth(CallbackInfoReturnable<Integer> cir) {
        Object pattern = cnpcplusField((ShapedRecipe) (Object) this, "pattern", "f_44149_");
        cir.setReturnValue(cnpcplusInt(pattern, "width", "f_302331_", 0));
    }

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusGetHeight(CallbackInfoReturnable<Integer> cir) {
        Object pattern = cnpcplusField((ShapedRecipe) (Object) this, "pattern", "f_44149_");
        cir.setReturnValue(cnpcplusInt(pattern, "height", "f_302470_", 0));
    }

    @Inject(method = "getResult", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusGetResult(CallbackInfoReturnable<ItemStack> cir) {
        Object result = cnpcplusField((ShapedRecipe) (Object) this, "result", "f_44150_");
        cir.setReturnValue(result instanceof ItemStack s ? s : ItemStack.EMPTY);
    }

    private static Object cnpcplusField(Object self, String... names) {
        if (self == null) return null;
        Class<?> c = self.getClass();
        while (c != null) {
            for (String name : names) {
                try {
                    var f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f.get(self);
                } catch (ReflectiveOperationException ignored) {
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static int cnpcplusInt(Object self, String name1, String name2, int def) {
        Object v = cnpcplusField(self, name1, name2);
        if (v instanceof Integer i) return i;
        if (v instanceof Number n) return n.intValue();
        // try record-style accessors
        if (self != null) {
            for (String m : new String[]{name1, "get" + Character.toUpperCase(name1.charAt(0)) + name1.substring(1)}) {
                try {
                    var method = self.getClass().getMethod(m);
                    Object r = method.invoke(self);
                    if (r instanceof Integer i) return i;
                    if (r instanceof Number n) return n.intValue();
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        return def;
    }
}