package bin.cnpcplus.mixin.recipe;

import bin.cnpcplus.recipe.runtime.CraftingInputMatcher;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Gate global recipes on the vanilla workbench (ContainerWorkbench) by
 * recipe.availability. The carpentry bench already checks availability
 * natively (ContainerCarpentryBench.onCraftMatrixChanged); the workbench
 * does not, because CraftingManager.matches() has no player context.
 * All member access is reflective (runtime classes are SRG-obfuscated).
 */
@Mixin(ContainerWorkbench.class)
public class MixinContainerWorkbenchGate {

    @Inject(method = "func_75130_a", at = @At("RETURN"), remap = false)
    private void cnpcplusGate(IInventory inv, CallbackInfo ci) {
        ContainerWorkbench self = (ContainerWorkbench) (Object) this;
        if (!(inv instanceof InventoryCrafting)) return;
        World world = getFieldByType(self, World.class);
        if (world == null || world.isRemote) return;

        List<Slot> slots = getSlots(self);
        if (slots == null || slots.isEmpty()) return;
        Slot resultSlot = slots.get(0);
        ItemStack result = resultSlot.getStack();
        if (result == null || result.isEmpty()) return;

        RecipeController controller = RecipeController.instance;
        if (controller == null || controller.globalRecipes == null) return;

        RecipeCarpentry recipe = null;
        for (RecipeCarpentry rc : controller.globalRecipes.values()) {
            if (rc == null) continue;
            if (CraftingInputMatcher.matches(rc, (InventoryCrafting) inv)) {
                recipe = rc;
                break;
            }
        }
        if (recipe == null) return;

        EntityPlayer player = getFieldByType(self, EntityPlayer.class);
        if (player == null) return;
        if (!recipe.availability.isAvailable(player)) {
            resultSlot.putStack(ItemStack.EMPTY);
        }
    }

    private static <T> T getFieldByType(Object target, Class<T> type) {
        Class<?> c = target.getClass();
        while (c != null && c != Object.class) {
            try {
                Field[] fields = c.getDeclaredFields();
                for (int i = 0; i < fields.length; i++) {
                    Field f = fields[i];
                    if (!type.isAssignableFrom(f.getType())) continue;
                    f.setAccessible(true);
                    Object v = f.get(target);
                    if (v != null && type.isInstance(v)) {
                        return type.cast(v);
                    }
                }
            } catch (Throwable ignored) {
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static List<Slot> getSlots(Object target) {
        Class<?> c = target.getClass();
        while (c != null && c != Object.class) {
            try {
                Field[] fields = c.getDeclaredFields();
                for (int i = 0; i < fields.length; i++) {
                    Field f = fields[i];
                    if (f.getType() != List.class) continue;
                    f.setAccessible(true);
                    Object v = f.get(target);
                    if (v instanceof List && !((List<?>) v).isEmpty()
                            && ((List<?>) v).get(0) instanceof Slot) {
                        return (List<Slot>) v;
                    }
                }
            } catch (Throwable ignored) {
            }
            c = c.getSuperclass();
        }
        return null;
    }
}
