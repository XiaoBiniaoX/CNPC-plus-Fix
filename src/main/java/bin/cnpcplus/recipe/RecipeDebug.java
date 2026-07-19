package bin.cnpcplus.recipe;

import bin.cnpcplus.CnpcPlus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import noppes.npcs.CustomNpcs;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Debug instrumentation for Phase3 funnel. Not business logic.
 * Never gate business behavior on these logs.
 */
public final class RecipeDebug {
    public static boolean ENABLED = true;

    private RecipeDebug() {}

    public static boolean enabled() {
        return ENABLED;
    }

    public static void info(String fmt, Object... args) {
        if (!ENABLED) return;
        CnpcPlus.LOGGER.info("[RecipeDebug] " + fmt, args);
    }

    public static void probeRecipe(RecipeCarpentry recipe, ResourceLocation id) {
        if (!ENABLED || recipe == null) return;
        MinecraftServer server = CustomNpcs.Server;
        if (server == null) {
            info("probe skip: no server");
            return;
        }
        RecipeManager manager = server.getRecipeManager();
        int hash = System.identityHashCode(manager);
        info("probe managerHash={}", hash);

        Optional<? extends RecipeHolder<?>> byKey = id != null ? manager.byKey(id) : Optional.empty();
        info("probe byKey id={} present={}", id, byKey.isPresent());

        int craftingSize = 0;
        boolean inList = false;
        try {
            List<RecipeHolder<CraftingRecipe>> all = manager.getAllRecipesFor(RecipeType.CRAFTING);
            craftingSize = all.size();
            if (id != null) {
                for (RecipeHolder<CraftingRecipe> h : all) {
                    if (id.equals(h.id())) {
                        inList = true;
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            info("probe getAllRecipesFor failed: {}", t.toString());
        }
        info("probe craftingRecipes.size={} inCraftingList={}", craftingSize, inList);

        Object type = null;
        Object cat = null;
        boolean can3 = false;
        try { type = recipe.getType(); } catch (Throwable ignored) {}
        try { cat = recipe.category(); } catch (Throwable ignored) {}
        try { can3 = recipe.canCraftInDimensions(3, 3); } catch (Throwable ignored) {}
        info("probe candidate id={} getType()={} category()={} canCraft3x3={}", id, type, cat, can3);

        try {
            CraftingInput input = buildInputFromRecipe(recipe);
            ServerLevel level = server.overworld();
            if (input != null && level != null) {
                boolean matches = false;
                try {
                    matches = recipe.matches(input, level);
                } catch (Throwable t) {
                    info("probe matches error: {}", t.toString());
                }
                info("probe candidate matches={}", matches);

                Optional<RecipeHolder<CraftingRecipe>> found =
                        manager.getRecipeFor(RecipeType.CRAFTING, input, level);
                info("probe getRecipeFor empty={} id={}",
                        found.isEmpty(),
                        found.map(RecipeHolder::id).orElse(null));
            } else {
                info("probe skip getRecipeFor: input/level null");
            }
        } catch (Throwable t) {
            info("probe matches/getRecipeFor error: {}", t.toString());
        }
    }

    public static void probeAllGlobals() {
        if (!ENABLED) return;
        RecipeController c = RecipeController.instance;
        if (c == null || c.globalRecipes == null) {
            info("probeAll: no controller/global");
            return;
        }
        info("probeAll Storage anvil={} global={}",
                c.anvilRecipes != null ? c.anvilRecipes.size() : -1,
                c.globalRecipes.size());
        for (var e : c.globalRecipes.entrySet()) {
            ResourceLocation loc = e.getKey();
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("customnpcs",
                    "global/" + (loc != null ? loc.getPath().replace(':', '_') : "unknown"));
            probeRecipe(e.getValue(), id);
        }
    }

    public static ResourceLocation injectIdOf(ResourceLocation storageKey) {
        String path = storageKey != null ? storageKey.getPath().replace(':', '_') : "unknown";
        return ResourceLocation.fromNamespaceAndPath("customnpcs", "global/" + path);
    }

    private static CraftingInput buildInputFromRecipe(RecipeCarpentry recipe) {
        int w = Math.max(1, recipe.getWidth());
        int h = Math.max(1, recipe.getHeight());
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 9; i++) items.add(ItemStack.EMPTY);
        var ings = recipe.getIngredients();
        if (ings == null) return CraftingInput.of(3, 3, items);
        int ox = 0, oy = 0;
        if (recipe instanceof RecipeCarpentryOffsetAccessor off && off.cnpcplusHasSavedOffset()) {
            ox = Math.min(off.cnpcplusGetOffsetX(), 2);
            oy = Math.min(off.cnpcplusGetOffsetY(), 2);
        }
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                int idx = col + row * w;
                if (idx < 0 || idx >= ings.size()) continue;
                Ingredient ing = ings.get(idx);
                ItemStack stack = ItemStack.EMPTY;
                if (ing != null && !ing.isEmpty()) {
                    ItemStack[] arr = ing.getItems();
                    if (arr.length > 0) stack = arr[0].copy();
                }
                int mx = col + ox;
                int my = row + oy;
                if (mx >= 0 && my >= 0 && mx < 3 && my < 3) {
                    items.set(mx + my * 3, stack);
                }
            }
        }
        return CraftingInput.of(3, 3, items);
    }
}