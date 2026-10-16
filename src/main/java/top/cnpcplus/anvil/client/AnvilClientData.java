package top.cnpcplus.anvil.client;

import top.cnpcplus.anvil.AnvilRecipeData;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端缓存的服务端铁砧配方列表（GUI 滚动列表 + AnvilCraftHandler 客户端事件源使用）。
 * 进入铁律：只在本缓存为空时才向服务端请求全量列表，
 * 打开界面禁止无条件全量重建/请求（用户明确要求「每次进去不许顿一下」）。
 */
public final class AnvilClientData {
    private static List<AnvilRecipeData> list = new ArrayList<>();

    private AnvilClientData() {}

    public static void set(List<AnvilRecipeData> data) {
        list = new ArrayList<>(data);
    }

    public static List<AnvilRecipeData> get() {
        return list;
    }

    public static AnvilRecipeData byId(int id) {
        for (AnvilRecipeData d : list) if (d.id == id) return d;
        return null;
    }
}
