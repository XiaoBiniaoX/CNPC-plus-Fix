package top.cnpcplus.smithing.client;

import top.cnpcplus.smithing.SmithingRecipeData;

import java.util.ArrayList;
import java.util.List;

/** 编辑列表在本次连接首次打开时加载，之后按配方 ID 更新。 */
public final class SmithingClientData {
    private static List<SmithingRecipeData> list;

    private SmithingClientData() {}

    public static void set(List<SmithingRecipeData> data) {
        list = new ArrayList<>(data);
    }

    public static void clear() {
        list = null;
    }

    public static boolean isLoaded() {
        return list != null;
    }

    public static List<SmithingRecipeData> get() {
        return list == null ? List.of() : list;
    }

    public static SmithingRecipeData byId(int id) {
        for (SmithingRecipeData d : get()) if (d.id == id) return d;
        return null;
    }
}
