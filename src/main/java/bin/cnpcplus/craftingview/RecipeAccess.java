package bin.cnpcplus.craftingview;

import bin.cnpcplus.recipe.RecipeCarpentryOffsetAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.controllers.data.RecipeCarpentry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RecipeAccess {
    private static net.minecraft.client.multiplayer.ClientPacketListener connection;
    private static final Map<ResourceLocation, RecipeCarpentry> globalRecipes = new java.util.HashMap<>();
    private static final Map<ResourceLocation, RecipeCarpentry> anvilRecipes = new java.util.HashMap<>();
    private static final Map<ResourceLocation, RecipeCarpentry> pendingGlobal = new java.util.HashMap<>();
    private static final Map<ResourceLocation, RecipeCarpentry> pendingAnvil = new java.util.HashMap<>();
    private RecipeAccess() {}

    private static void checkConnection() {
        var current = Minecraft.getInstance().getConnection();
        if (connection == current) return;
        connection = current;
        globalRecipes.clear(); anvilRecipes.clear();
        pendingGlobal.clear(); pendingAnvil.clear();
    }

    public static void acceptList(int type, net.minecraft.nbt.CompoundTag data, boolean end) {
        checkConnection();
        var pending = type == 6 ? pendingGlobal : pendingAnvil;
        var list = data.getList("Data", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var tag = list.getCompound(i);
            int syncId = tag.getInt(bin.cnpcplus.recipe.RecipeNbtKeys.SYNC_ID);
            if (syncId <= 0) continue;
            var recipe = RecipeCarpentry.load(tag, connection.registryAccess());
            recipe.isGlobal = type == 6;
            pending.put(bin.cnpcplus.recipe.id.RecipeIds.INSTANCE.locationOf(syncId), recipe);
        }
        if (!end) return;
        var recipes = type == 6 ? globalRecipes : anvilRecipes;
        recipes.clear(); recipes.putAll(pending); pending.clear();
        top.cnpcplus.client.gui.SubGuiQuestSettings.refreshCraftRecipes();
    }

    public static void acceptUpdate(ResourceLocation id, net.minecraft.nbt.CompoundTag data) {
        checkConnection();
        globalRecipes.remove(id); anvilRecipes.remove(id);
        if (!data.isEmpty()) {
            var recipe = RecipeCarpentry.load(data, connection.registryAccess());
            (recipe.isGlobal ? globalRecipes : anvilRecipes).put(id, recipe);
        }
        top.cnpcplus.client.gui.SubGuiQuestSettings.refreshCraftRecipes();
    }

    /** Sidebar shows only recipes whose availability the current player satisfies. */
    private static boolean isAvailable(RecipeCarpentry r) {
        try {
            Player player = Minecraft.getInstance().player;
            if (player == null || r.availability == null) return true;
            return r.availability.isAvailable(player);
        } catch (Throwable t) {
            return true;
        }
    }

    public static List<RecipeView> getAnvilRecipes() {
        return getAnvilRecipes(false);
    }

    public static List<RecipeView> getAnvilRecipes(boolean includeUnavailable) {
        List<RecipeView> list = new ArrayList<>();
        checkConnection();
        for (Map.Entry<ResourceLocation, RecipeCarpentry> e : anvilRecipes.entrySet()) {
            RecipeCarpentry r = e.getValue();
            if (r == null || r.getResult() == null || r.getResult().isEmpty()) continue;
            if (!includeUnavailable && !isAvailable(r)) continue;
            RecipeCarpentryOffsetAccessor off = (RecipeCarpentryOffsetAccessor) r;
            int ox = off.cnpcplusHasSavedOffset() ? off.cnpcplusGetOffsetX() : 0;
            int oy = off.cnpcplusHasSavedOffset() ? off.cnpcplusGetOffsetY() : 0;
            list.add(new RecipeView(e.getKey(), r, ox, oy));
        }
        return list;
    }

    public static List<RecipeView> getGlobalRecipes() {
        return getGlobalRecipes(false);
    }

    public static List<RecipeView> getGlobalRecipes(boolean includeUnavailable) {
        List<RecipeView> list = new ArrayList<>();
        checkConnection();
        for (Map.Entry<ResourceLocation, RecipeCarpentry> e : globalRecipes.entrySet()) {
            RecipeCarpentry r = e.getValue();
            if (r == null || !r.isGlobal) continue;
            if (r.getResult() == null || r.getResult().isEmpty()) continue;
            if (!includeUnavailable && !isAvailable(r)) continue;
            RecipeCarpentryOffsetAccessor off = (RecipeCarpentryOffsetAccessor) r;
            int ox = off.cnpcplusHasSavedOffset() ? off.cnpcplusGetOffsetX() : 0;
            int oy = off.cnpcplusHasSavedOffset() ? off.cnpcplusGetOffsetY() : 0;
            list.add(new RecipeView(e.getKey(), r, ox, oy));
        }
        return list;
    }

    public static RecipeCarpentry getAnvil(ResourceLocation id) {
        checkConnection();
        return anvilRecipes.get(id);
    }

    public static RecipeCarpentry getGlobal(ResourceLocation id) {
        checkConnection();
        return globalRecipes.get(id);
    }
}
