package bin.cnpcplus.recipe.storage;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.recipe.id.RecipeIds;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.CustomNpcs;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

/**
 * Per-world recipes.dat only. Cross-world recipes go through RecipePersistent.
 */
public final class RecipeStorage {
    public static final RecipeStorage INSTANCE = new RecipeStorage();
    private static final String NBT_SYNC = "CnpcPlusSyncId";

    private RecipeStorage() {}

    public void loadAll(HolderLookup.Provider provider, RecipeController controller) {
        RecipeIds.INSTANCE.clear();
        File saveDir = CustomNpcs.getLevelSaveDirectory();
        if (saveDir == null) {
            controller.globalRecipes = new HashMap<>();
            controller.anvilRecipes = new HashMap<>();
            return;
        }
        try {
            File file = new File(saveDir, "recipes.dat");
            if (file.exists()) {
                loadFile(provider, controller, file);
            } else {
                File old = new File(saveDir, "recipes.dat_old");
                if (old.exists()) {
                    loadFile(provider, controller, old);
                } else {
                    controller.globalRecipes = new HashMap<>();
                    controller.anvilRecipes = new HashMap<>();
                }
            }
        } catch (Exception e) {
            CnpcPlus.LOGGER.error("[RecipeStorage] load failed", e);
            try {
                File old = new File(saveDir, "recipes.dat_old");
                if (old.exists()) {
                    loadFile(provider, controller, old);
                    return;
                }
            } catch (Exception e2) {
                CnpcPlus.LOGGER.error("[RecipeStorage] load _old failed", e2);
            }
            controller.globalRecipes = new HashMap<>();
            controller.anvilRecipes = new HashMap<>();
        }
        // merge cross-world persistent recipes after world load
        RecipePersistent.INSTANCE.mergeInto(provider, controller);
    }

    private void loadFile(HolderLookup.Provider provider, RecipeController controller, File file) throws Exception {
        CompoundTag root;
        try (InputStream in = new FileInputStream(file)) {
            root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        }
        if (root == null) {
            controller.globalRecipes = new HashMap<>();
            controller.anvilRecipes = new HashMap<>();
            return;
        }
        RecipeIds.INSTANCE.setNextSyncFloor(root.getInt("LastId"));

        HashMap<ResourceLocation, RecipeCarpentry> global = new HashMap<>();
        HashMap<ResourceLocation, RecipeCarpentry> anvil = new HashMap<>();
        ListTag list = root.getList("Data", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            RecipeCarpentry recipe = RecipeCarpentry.load(tag, provider);
            recipe.name = tag.getString("Name");
            if (recipe.name == null || recipe.name.isEmpty()) {
                recipe.name = "recipe_" + i;
            }
            int preferred = tag.contains(NBT_SYNC) ? tag.getInt(NBT_SYNC) : -1;
            int syncId = RecipeIds.INSTANCE.register(recipe, preferred);
            ResourceLocation loc = RecipeIds.INSTANCE.locationOf(syncId);
            if (recipe.isGlobal) {
                global.put(loc, recipe);
            } else {
                anvil.put(loc, recipe);
            }
        }
        controller.globalRecipes = global;
        controller.anvilRecipes = anvil;
        controller.nextId = Math.max(controller.nextId, RecipeIds.INSTANCE.peekNextSyncId());
    }

    public void saveAll(HolderLookup.Provider provider, RecipeController controller) {
        if (provider == null) {
            return;
        }
        File saveDir = CustomNpcs.getLevelSaveDirectory();
        if (saveDir == null) {
            return;
        }
        try {
            ListTag list = new ListTag();
            for (RecipeCarpentry recipe : controller.globalRecipes.values()) {
                if (recipe == null || !recipe.savesRecipe) continue;
                list.add(writeRecipeTag(recipe, provider));
            }
            for (RecipeCarpentry recipe : controller.anvilRecipes.values()) {
                if (recipe == null || !recipe.savesRecipe) continue;
                list.add(writeRecipeTag(recipe, provider));
            }
            CompoundTag root = new CompoundTag();
            root.put("Data", list);
            root.putInt("LastId", Math.max(controller.nextId, RecipeIds.INSTANCE.peekNextSyncId()));
            root.putInt("Version", 1);

            File fileNew = new File(saveDir, "recipes.dat_new");
            File fileOld = new File(saveDir, "recipes.dat_old");
            File file = new File(saveDir, "recipes.dat");
            try (OutputStream out = new FileOutputStream(fileNew)) {
                NbtIo.writeCompressed(root, out);
                out.flush();
            }
            if (fileOld.exists()) {
                //noinspection ResultOfMethodCallIgnored
                fileOld.delete();
            }
            if (file.exists() && !file.renameTo(fileOld)) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
            if (!fileNew.renameTo(file)) {
                try (InputStream in = new FileInputStream(fileNew); OutputStream out = new FileOutputStream(file)) {
                    in.transferTo(out);
                }
                //noinspection ResultOfMethodCallIgnored
                fileNew.delete();
            }
            if (fileNew.exists()) {
                //noinspection ResultOfMethodCallIgnored
                fileNew.delete();
            }
        } catch (Exception e) {
            CnpcPlus.LOGGER.error("[RecipeStorage] save failed", e);
        }
    }

    CompoundTag writeRecipeTag(RecipeCarpentry recipe, HolderLookup.Provider provider) {
        CompoundTag tag = recipe.writeNBT(provider);
        if (recipe.name != null) {
            tag.putString("Name", recipe.name);
        }
        Integer sync = RecipeIds.INSTANCE.syncIdOfRecipe(recipe);
        if (sync != null) {
            tag.putInt(NBT_SYNC, sync);
        }
        return tag;
    }
}
