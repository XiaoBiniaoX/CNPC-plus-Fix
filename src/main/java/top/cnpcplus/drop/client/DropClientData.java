package top.cnpcplus.drop.client;

import top.cnpcplus.drop.DropRecipeData;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端缓存的服务端掉落物配方列表（GUI 滚动列表使用）。
 * 合成判定不在客户端跑（用户要求4=合成完全服务端执行），本缓存只服务编辑器显示。
 */
public final class DropClientData {

    private static List<DropRecipeData> list = new ArrayList<>();

    private DropClientData() {}

    public static void set(List<DropRecipeData> data) {
        list = data == null ? new ArrayList<>() : new ArrayList<>(data);
    }

    public static List<DropRecipeData> get() {
        return new ArrayList<>(list);
    }

    public static DropRecipeData byId(int id) {
        for (DropRecipeData d : list) if (d.id == id) return d;
        return null;
    }
}
