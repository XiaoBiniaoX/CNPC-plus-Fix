package top.cnpcplus.perf.client;

import net.minecraft.resources.ResourceLocation;
import noppes.npcs.shared.client.model.util.Polygon;

/** 只缓存 CNPC 原版算法的结果；淘汰后照常重新计算，不改变轮廓或隐藏模型。 */
public final class Model2DOutlineCache {
    public record Key(ResourceLocation location, int texX, int texY, int width, int height,
                      float textureWidth, float textureHeight, float x1, float x2, float y1, float y2) {}
    private static final BoundedLru<Key, Polygon[]> OUTLINES = new BoundedLru<>(2048);
    private Model2DOutlineCache() {}
    public static Polygon[] get(Key key) { return OUTLINES.get(key); }
    public static void put(Key key, Polygon[] polygons) {
        if (polygons != null) OUTLINES.put(key, polygons);
    }
    public static void setCapacity(int capacity) { OUTLINES.setCapacity(capacity); }
    public static void invalidateAll() { OUTLINES.clear(); }
}
