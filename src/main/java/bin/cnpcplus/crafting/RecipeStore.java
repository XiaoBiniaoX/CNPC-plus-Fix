package bin.cnpcplus.crafting;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.data.NbtFiles;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.CustomNpcs;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/** 仅服务端保存权威配方；客户端编辑列表用独立缓存，集成服不互相覆盖。 */
public final class RecipeStore {
    private static final Map<CraftKind, NavigableMap<Integer, CustomRecipe>> DATA = new EnumMap<>(CraftKind.class);
    private static final java.util.Set<CraftKind> FAILED = EnumSet.noneOf(CraftKind.class);
    private static boolean ready;
    /** 上一次写盘失败的 lang key；随 PacketRecipeState 回传客户端显示。 */
    private static String notice = "";

    private RecipeStore() { }
    public static void start() { ready = true; }
    public static void clear() { NbtFiles.flush(); DATA.clear(); FAILED.clear(); ready = false; notice = ""; }
    private static void notify(String key) { notice = key; }
    /** 取走并清空待发送的提示，避免同一条错误重复弹。 */
    public static String takeNotice() { String out = notice; notice = ""; return out; }

    private static Path path(CraftKind kind) {
        if (CustomNpcs.Dir == null) throw new IllegalStateException("CustomNPCs world directory is not ready");
        return CustomNpcs.Dir.toPath().resolve(kind.id + "_recipes.dat");
    }

    private static NavigableMap<Integer, CustomRecipe> ensure(CraftKind kind) {
        if (!ready) return Collections.emptyNavigableMap();
        NavigableMap<Integer, CustomRecipe> map = DATA.get(kind);
        if (map != null) return map;
        map = new TreeMap<>();
        try {
            NBTTagList list = NbtFiles.read(path(kind)).getTagList("Data", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                CustomRecipe recipe = CustomRecipe.fromNBT(kind, list.getCompoundTagAt(i));
                if (recipe.id >= 0 && recipe.valid()) map.put(recipe.id, recipe);
            }
        } catch (Exception error) {
            FAILED.add(kind);
            CnpcPlus.LOGGER.error("Cannot load {} recipes; writes disabled to preserve the file", kind.id, error);
        }
        DATA.put(kind, map);
        return map;
    }

    public static List<CustomRecipe> list(CraftKind kind) { return new ArrayList<>(ensure(kind).values()); }

    public static CustomRecipe get(CraftKind kind, int id) {
        CustomRecipe recipe = ensure(kind).get(id);
        return recipe == null ? null : recipe.copy();
    }

    public static CustomRecipe save(CustomRecipe recipe) {
        NavigableMap<Integer, CustomRecipe> map = ensure(recipe.kind);
        if (!ready) { notify("cnpcplus.recipe.error.notready"); return null; }
        if (FAILED.contains(recipe.kind)) { notify("cnpcplus.recipe.error.io"); return null; }
        if (!recipe.valid()) { notify("cnpcplus.recipe.error.invalid"); return null; }
        if (recipe.id < 0) recipe.id = map.isEmpty() ? 1 : map.lastKey() + 1;
        else if (!map.containsKey(recipe.id)) { notify("cnpcplus.recipe.error.stale"); return null; }
        map.put(recipe.id, recipe.copy());
        persist(recipe.kind);
        return recipe;
    }

    public static void remove(CraftKind kind, int id) {
        NavigableMap<Integer, CustomRecipe> map = ensure(kind);
        if (ready && !FAILED.contains(kind) && map.remove(id) != null) persist(kind);
    }

    private static void persist(CraftKind kind) {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (CustomRecipe recipe : ensure(kind).values()) list.appendTag(recipe.toNBT(true));
        tag.setTag("Data", list);
        tag.setInteger("Version", 1);
        NbtFiles.write(path(kind), tag);
    }
}
