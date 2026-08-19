package top.cnpcplus.smelting.client;

import top.cnpcplus.smelting.SmeltingRecipeData;

import java.util.ArrayList;
import java.util.List;

/** 客户端缓存的服务端熔炼配方列表（GUI 滚动列表使用）。 */
public final class SmeltingClientData {
    private static List<SmeltingRecipeData> list = new ArrayList<>();

    private SmeltingClientData() {}

    public static void set(List<SmeltingRecipeData> data) {
        list = new ArrayList<>(data);
    }

    public static List<SmeltingRecipeData> get() {
        return list;
    }

    public static SmeltingRecipeData byId(int id) {
        for (SmeltingRecipeData d : list) if (d.id == id) return d;
        return null;
    }
}
