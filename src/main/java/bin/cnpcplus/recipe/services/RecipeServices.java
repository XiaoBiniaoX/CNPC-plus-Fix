package bin.cnpcplus.recipe.services;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.recipe.CraftUtils;
import bin.cnpcplus.recipe.RecipeDebug;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Item compare + inject global recipes into Forge recipe registry.
 * 1.12 freezes the recipe registry after startup; must unfreeze to inject.
 */
public final class RecipeServices {
    private static Method unfreezeMethod;
    private static Method freezeMethod;
    private static Method removeMethod;
    private static Method addMethod;
    private static boolean methodsResolved;

    private RecipeServices() {}

    public static boolean compareItems(ItemStack required, ItemStack actual, boolean ignoreDamage, boolean ignoreNBT) {
        return CraftUtils.matches(actual, required, ignoreDamage, ignoreNBT);
    }

    public static void reloadGlobalIntoRecipeManager(RecipeController controller) {
        if (controller == null) return;

        IForgeRegistry<IRecipe> registry = RecipeController.Registry;
        if (registry == null) {
            try {
                registry = ForgeRegistries.RECIPES;
            } catch (Throwable ignored) {
            }
        }
        if (registry == null) {
            return;
        }

        resolveMethods();
        boolean unfroze = invokeUnfreeze(registry);

        List<ResourceLocation> toRemove = new ArrayList<ResourceLocation>();
        try {
            for (IRecipe recipe : registry) {
                if (recipe == null) continue;
                ResourceLocation name = recipe.getRegistryName();
                if (name != null && "customnpcs".equals(name.getNamespace())
                        && name.getPath() != null && name.getPath().startsWith("global/")) {
                    toRemove.add(name);
                }
            }
        } catch (Throwable t) {
            CnpcPlus.LOGGER.warn("[RecipeServices] iterate failed: " + t);
        }

        for (int i = 0; i < toRemove.size(); i++) {
            invokeRemove(registry, toRemove.get(i));
        }

        if (controller.globalRecipes != null) {
            for (Map.Entry<Integer, RecipeCarpentry> e : controller.globalRecipes.entrySet()) {
                RecipeCarpentry recipe = e.getValue();
                if (recipe == null || !recipe.isGlobal) continue;
                if (!recipe.isValid()) continue;

                ResourceLocation id = new ResourceLocation("customnpcs", "global/id_" + e.getKey().intValue());
                try {
                    clearRegistryName(recipe);
                    recipe.setRegistryName(id);
                    invokeRemove(registry, id);
                    invokeAdd(registry, recipe);
                    if (RecipeDebug.enabled()) {
                        RecipeDebug.info("inject id={} name={} valid={}", id, recipe.name, Boolean.valueOf(recipe.isValid()));
                    }
                } catch (Throwable t) {
                    CnpcPlus.LOGGER.warn("[RecipeServices] register failed id=" + id + " : " + t);
                }
            }
        }

        if (unfroze) {
            invokeFreeze(registry);
        }
    }

    private static void resolveMethods() {
        if (methodsResolved) return;
        methodsResolved = true;
        try {
            unfreezeMethod = ForgeRegistry.class.getDeclaredMethod("unfreeze");
            unfreezeMethod.setAccessible(true);
        } catch (Throwable t) {
            CnpcPlus.LOGGER.warn("[RecipeServices] no unfreeze: " + t);
        }
        try {
            freezeMethod = ForgeRegistry.class.getDeclaredMethod("freeze");
            freezeMethod.setAccessible(true);
        } catch (Throwable t) {
            CnpcPlus.LOGGER.warn("[RecipeServices] no freeze: " + t);
        }
        try {
            removeMethod = ForgeRegistry.class.getDeclaredMethod("remove", ResourceLocation.class);
            removeMethod.setAccessible(true);
        } catch (Throwable t) {
            CnpcPlus.LOGGER.warn("[RecipeServices] no remove: " + t);
        }
        try {
            // package-private: int add(int id, V value, String owner)
            addMethod = ForgeRegistry.class.getDeclaredMethod("add", int.class, Object.class, String.class);
            addMethod.setAccessible(true);
        } catch (Throwable t) {
            try {
                addMethod = ForgeRegistry.class.getDeclaredMethod("add", int.class, IRecipe.class, String.class);
                addMethod.setAccessible(true);
            } catch (Throwable t2) {
                CnpcPlus.LOGGER.warn("[RecipeServices] no add: " + t2);
            }
        }
    }

    private static boolean invokeUnfreeze(IForgeRegistry<IRecipe> registry) {
        if (unfreezeMethod == null || !(registry instanceof ForgeRegistry)) return false;
        try {
            unfreezeMethod.invoke(registry);
            return true;
        } catch (Throwable t) {
            CnpcPlus.LOGGER.warn("[RecipeServices] unfreeze invoke failed: " + t);
            return false;
        }
    }

    private static void invokeFreeze(IForgeRegistry<IRecipe> registry) {
        if (freezeMethod == null || !(registry instanceof ForgeRegistry)) return;
        try {
            freezeMethod.invoke(registry);
        } catch (Throwable t) {
        }
    }

    private static void invokeRemove(IForgeRegistry<IRecipe> registry, ResourceLocation id) {
        if (removeMethod == null || !(registry instanceof ForgeRegistry)) return;
        try {
            removeMethod.invoke(registry, id);
        } catch (Throwable t) {
        }
    }

    private static void invokeAdd(IForgeRegistry<IRecipe> registry, IRecipe recipe) throws Exception {
        if (addMethod != null && registry instanceof ForgeRegistry) {
            addMethod.invoke(registry, Integer.valueOf(-1), recipe, "cnpcplus");
            return;
        }
        // last resort
        registry.register(recipe);
    }

    private static void clearRegistryName(IRecipe recipe) {
        try {
            Class<?> c = recipe.getClass();
            while (c != null) {
                try {
                    Field f = c.getDeclaredField("registryName");
                    f.setAccessible(true);
                    f.set(recipe, null);
                    return;
                } catch (NoSuchFieldException e) {
                    c = c.getSuperclass();
                }
                }
            } catch (Throwable t) {
            }
        }
    }
