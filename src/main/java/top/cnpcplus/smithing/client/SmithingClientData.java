package top.cnpcplus.smithing.client;

import top.cnpcplus.smithing.SmithingRecipeData;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端缓存的服务端锻造配方列表（GUI 滚动列表使用），226 从 SmeltingClientData 复制。
 * 226 本步数据链路未接（恒空表）；后续接入铁律：只在本缓存为空时才向服务端请求全量列表，
 * 打开界面禁止无条件全量重建/请求（用户明确要求「每次进去不许顿一下」）。
 */
public final class SmithingClientData {
    private static List<SmithingRecipeData> list = new ArrayList<>();

    private SmithingClientData() {}

    public static void set(List<SmithingRecipeData> data) {
        list = new ArrayList<>(data);
    }

    public static List<SmithingRecipeData> get() {
        return list;
    }

    public static SmithingRecipeData byId(int id) {
        for (SmithingRecipeData d : list) if (d.id == id) return d;
        return null;
    }
}
