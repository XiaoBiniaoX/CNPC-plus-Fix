package bin.cnpcplus.recipe.services;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.recipe.CraftUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import noppes.npcs.CustomNpcs;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;
import bin.cnpcplus.smelting.SmeltingRecipeData;
import bin.cnpcplus.smelting.SmeltingRecipeParser;
import bin.cnpcplus.smelting.SmeltingRecipeRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Platform services: item compare (1.20.1 fuzzy rules) + RecipeManager inject.
 */
public final class RecipeServices {
    private RecipeServices() {}

    public static ResourceLocation itemKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    /**
     * Replaces NoppesUtilPlayer.compareItems for recipe matching.
     * ignoreDamage / ignoreNBT keep CNPC recipe flags but use 1.20.1 cnpcplus semantics.
     */
    public static boolean compareItems(ItemStack required, ItemStack actual, boolean ignoreDamage, boolean ignoreNBT) {
        // CraftUtils expects (playerStack, required, ...)
        return CraftUtils.matches(actual, required, ignoreDamage, ignoreNBT);
    }

    public static void reloadGlobalIntoRecipeManager(RecipeController controller) {
        MinecraftServer server = CustomNpcs.Server;
        if (server == null) {
            return;
        }
        if (controller == null) return;

        RecipeManager manager = server.getRecipeManager();
        if (manager == null) return;

        List<RecipeHolder<?>> next = new ArrayList<>();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            ResourceLocation id = holder.id();
            if (id != null && "customnpcs".equals(id.getNamespace()) && id.getPath().startsWith("global/")) {
                continue;
            }
            next.add(holder);
        }

        int added = 0;
        if (controller.globalRecipes != null) {
            for (Map.Entry<ResourceLocation, RecipeCarpentry> e : controller.globalRecipes.entrySet()) {
                RecipeCarpentry recipe = e.getValue();
                if (recipe == null || !recipe.isGlobal) continue;
                if (recipe.getResult() == null || recipe.getResult().isEmpty()) continue;
                if (recipe.getIngredients() == null || recipe.getIngredients().isEmpty()) continue;
                String path = e.getKey() != null ? e.getKey().getPath() : ("recipe_" + added);
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("customnpcs", "global/" + path.replace(':', '_'));
                next.add(new RecipeHolder<>(id, recipe));
                added++;
            }
        }

        try {
            manager.replaceRecipes(next);
        } catch (Exception ex) {
            CnpcPlus.LOGGER.error("[RecipeServices] replaceRecipes failed", ex);
        }
    }

    public static void reloadSmeltingRecipes(MinecraftServer server) {
        if (server == null || server.getRecipeManager() == null) return;
        List<RecipeHolder<?>> next = new ArrayList<>();
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            ResourceLocation id = holder.id();
            if (id == null || !"cnpcplus".equals(id.getNamespace()) ||
                    !(id.getPath().startsWith("smelting/") || id.getPath().startsWith("blasting/") || id.getPath().startsWith("smoking/"))) {
                next.add(holder);
            }
        }
        for (SmeltingRecipeData data : SmeltingRecipeRegistry.list(server.registryAccess())) {
            for (var recipe : SmeltingRecipeParser.parse(data)) {
                String path = recipe.getType() == net.minecraft.world.item.crafting.RecipeType.BLASTING ? "blasting/" :
                        recipe.getType() == net.minecraft.world.item.crafting.RecipeType.SMOKING ? "smoking/" : "smelting/";
                next.add(new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("cnpcplus", path + data.id), recipe));
            }
        }
        server.getRecipeManager().replaceRecipes(next);
    }

    public static void updateGlobalRecipe(ResourceLocation key, RecipeCarpentry recipe) {
        MinecraftServer server = CustomNpcs.Server;
        if (server == null) return;
        var packet = new bin.cnpcplus.craftingview.network.PacketCarpentryRecipe(key,
                recipe == null ? new net.minecraft.nbt.CompoundTag() : recipe.writeNBT(server.registryAccess()));
        for (var player : server.getPlayerList().getPlayers()) {
            if (net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(player.connection, packet.type().id()))
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet);
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("customnpcs", "global/" + key.getPath());
        RecipeHolder<?> holder = recipe != null && recipe.isGlobal && !recipe.getResult().isEmpty()
                && !recipe.getIngredients().isEmpty() ? new RecipeHolder<>(id, recipe) : null;
        top.cnpcplus.craftingview.RecipeIndexUpdates.replace(server.getRecipeManager(), id, holder);
        top.cnpcplus.craftingview.RecipeDeltaPacket.broadcast(server, id, holder);
    }

    public static void updateSmeltingRecipe(MinecraftServer server, int recipeId) {
        SmeltingRecipeData data = SmeltingRecipeRegistry.get(server.registryAccess(), recipeId);
        Map<String, RecipeHolder<?>> replacements = new java.util.HashMap<>();
        if (data != null) for (var recipe : SmeltingRecipeParser.parse(data)) {
            String kind = recipe.getType() == net.minecraft.world.item.crafting.RecipeType.BLASTING ? "blasting/" :
                    recipe.getType() == net.minecraft.world.item.crafting.RecipeType.SMOKING ? "smoking/" : "smelting/";
            var id = ResourceLocation.fromNamespaceAndPath("cnpcplus", kind + recipeId);
            replacements.put(kind, new RecipeHolder<>(id, recipe));
        }
        for (String kind : List.of("smelting/", "blasting/", "smoking/")) {
            var id = ResourceLocation.fromNamespaceAndPath("cnpcplus", kind + recipeId);
            var holder = replacements.get(kind);
            top.cnpcplus.craftingview.RecipeIndexUpdates.replace(server.getRecipeManager(), id, holder);
            top.cnpcplus.craftingview.RecipeDeltaPacket.broadcast(server, id, holder);
        }
    }
}
