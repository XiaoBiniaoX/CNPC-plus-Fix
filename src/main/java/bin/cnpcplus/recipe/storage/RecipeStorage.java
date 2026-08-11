package bin.cnpcplus.recipe.storage;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.recipe.RecipeNbtKeys;
import bin.cnpcplus.recipe.id.RecipeIds;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.CustomNpcs;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Per-world recipes.dat + merge shared_recipes.dat from CustomNpcs.Dir.
 * saveAll only writes the world file (never wipes shared).
 */
public final class RecipeStorage {
    public static final RecipeStorage INSTANCE = new RecipeStorage();

    private RecipeStorage() {}

    private static File worldDir() {
        return CustomNpcs.getWorldSaveDirectory();
    }

    public void loadAll(RecipeController controller) {
        RecipeIds.INSTANCE.clear();
        SharedRecipeStore.INSTANCE.load();

        HashMap<Integer, RecipeCarpentry> global = new HashMap<Integer, RecipeCarpentry>();
        HashMap<Integer, RecipeCarpentry> anvil = new HashMap<Integer, RecipeCarpentry>();

        File wdir = worldDir();
        File worldFile = null;
        if (wdir != null) {
            File f = new File(wdir, "recipes.dat");
            if (f.exists()) worldFile = f;
            else {
                File old = new File(wdir, "recipes.dat_old");
                if (old.exists()) worldFile = old;
            }
        }

        try {
            if (worldFile != null) {
                loadInto(worldFile, global, anvil);
            }
        } catch (Exception e) {
            CnpcPlus.LOGGER.error("[RecipeStorage] world load failed", e);
        }

        // Merge shared (skip names already present from world; world wins for this session)
        Set<String> present = new HashSet<String>();
        for (RecipeCarpentry r : global.values()) {
            if (r != null && r.name != null) present.add(r.name.toLowerCase(Locale.ROOT));
        }
        for (RecipeCarpentry r : anvil.values()) {
            if (r != null && r.name != null) present.add(r.name.toLowerCase(Locale.ROOT));
        }
        for (RecipeCarpentry shared : SharedRecipeStore.INSTANCE.all()) {
            if (shared == null || shared.name == null) continue;
            String key = shared.name.toLowerCase(Locale.ROOT);
            if (present.contains(key)) continue;
            NBTTagCompound tag = shared.writeNBT();
            tag.setString("Name", shared.name);
            if (shared.id > 0) {
                tag.setInteger(RecipeNbtKeys.SYNC_ID, shared.id);
                tag.setInteger("ID", shared.id);
            }
            RecipeCarpentry copy = RecipeCarpentry.read(tag);
            copy.name = shared.name;
            int preferred = shared.id > 0 ? shared.id : -1;
            int syncId = RecipeIds.INSTANCE.register(copy, preferred);
            copy.id = syncId;
            if (copy.isGlobal) global.put(Integer.valueOf(syncId), copy);
            else anvil.put(Integer.valueOf(syncId), copy);
            present.add(key);
        }

        controller.globalRecipes = global;
        controller.anvilRecipes = anvil;
        controller.nextId = Math.max(controller.nextId, RecipeIds.INSTANCE.peekNextSyncId());
    }

    private void loadInto(File file, HashMap<Integer, RecipeCarpentry> global,
                          HashMap<Integer, RecipeCarpentry> anvil) throws Exception {
        InputStream in = new FileInputStream(file);
        NBTTagCompound root;
        try {
            root = CompressedStreamTools.readCompressed(in);
        } finally {
            in.close();
        }
        if (root == null) return;
        RecipeIds.INSTANCE.setNextSyncFloor(root.getInteger("LastId"));
        NBTTagList list = root.getTagList("Data", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            RecipeCarpentry recipe = RecipeCarpentry.read(tag);
            if (recipe.name == null || recipe.name.isEmpty()) {
                recipe.name = tag.hasKey("Name") ? tag.getString("Name") : ("recipe_" + i);
            }
            if (recipe.name == null || recipe.name.isEmpty()) recipe.name = "recipe_" + i;
            int preferred = -1;
            if (tag.hasKey(RecipeNbtKeys.SYNC_ID)) preferred = tag.getInteger(RecipeNbtKeys.SYNC_ID);
            else if (recipe.id > 0) preferred = recipe.id;
            int syncId = RecipeIds.INSTANCE.register(recipe, preferred);
            recipe.id = syncId;
            if (recipe.isGlobal) global.put(Integer.valueOf(syncId), recipe);
            else anvil.put(Integer.valueOf(syncId), recipe);
        }
    }

    /** World-only save. Shared recipes stay in shared_recipes.dat. */
    public void saveAll(RecipeController controller) {
        File saveDir = worldDir();
        if (saveDir == null) {
            CnpcPlus.LOGGER.warn("[RecipeStorage] cannot save, no world dir");
            return;
        }
        try {
            NBTTagList list = new NBTTagList();
            for (RecipeCarpentry recipe : controller.globalRecipes.values()) {
                if (recipe == null || !recipe.savesRecipe) continue;
                list.appendTag(writeRecipeTag(recipe));
            }
            for (RecipeCarpentry recipe : controller.anvilRecipes.values()) {
                if (recipe == null || !recipe.savesRecipe) continue;
                list.appendTag(writeRecipeTag(recipe));
            }
            NBTTagCompound root = new NBTTagCompound();
            root.setTag("Data", list);
            root.setInteger("LastId", Math.max(controller.nextId, RecipeIds.INSTANCE.peekNextSyncId()));
            root.setInteger("Version", 1);

            File fileNew = new File(saveDir, "recipes.dat_new");
            File fileOld = new File(saveDir, "recipes.dat_old");
            File file = new File(saveDir, "recipes.dat");
            OutputStream out = new FileOutputStream(fileNew);
            try {
                CompressedStreamTools.writeCompressed(root, out);
                out.flush();
            } finally {
                out.close();
            }
            if (fileOld.exists()) fileOld.delete();
            if (file.exists() && !file.renameTo(fileOld)) file.delete();
            if (!fileNew.renameTo(file)) {
                copyFile(fileNew, file);
                fileNew.delete();
            }
            if (fileNew.exists()) fileNew.delete();
        } catch (Exception e) {
            CnpcPlus.LOGGER.error("[RecipeStorage] save failed", e);
        }
    }

    private static void copyFile(File from, File to) throws Exception {
        InputStream in = new FileInputStream(from);
        try {
            OutputStream out = new FileOutputStream(to);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
                out.flush();
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    private NBTTagCompound writeRecipeTag(RecipeCarpentry recipe) {
        NBTTagCompound tag = recipe.writeNBT();
        if (recipe.name != null) tag.setString("Name", recipe.name);
        Integer sync = RecipeIds.INSTANCE.syncIdOfRecipe(recipe);
        if (sync != null) {
            tag.setInteger(RecipeNbtKeys.SYNC_ID, sync.intValue());
            tag.setInteger("ID", sync.intValue());
        }
        return tag;
    }
}
