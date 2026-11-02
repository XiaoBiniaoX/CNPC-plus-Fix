package top.cnpcplus.data;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.AABB;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataDisplay;

/** Explicit block dimensions at Size=5; zero preserves the native/Gecko axis. */
public final class NpcHitboxStore {
    public static final String KEY = "CNPCPlusHitbox";
    public static final float MAX = 100;
    public static final Settings AUTO = new Settings(0, 0, 0);
    private static final Map<DataDisplay, Settings> VALUES = new WeakHashMap<>();
    private static final Map<EntityNPCInterface, Rectangle> RECTANGLES = new WeakHashMap<>();
    private record Rectangle(EntityDimensions dimensions, float xRatio, float zRatio) {}
    private NpcHitboxStore() {}

    public record Settings(float length, float width, float height, float shadowScale) {
        public Settings(float length, float width, float height) { this(length, width, height, 1); }
        public Settings {
            length = clean(length);
            width = clean(width);
            height = clean(height);
            shadowScale = Float.isFinite(shadowScale) ? Math.max(0, Math.min(1, shadowScale)) : 1;
        }
        public static Settings read(CompoundTag tag) {
            CompoundTag box = tag.getCompound(KEY);
            return new Settings(box.getFloat("Length"), box.getFloat("Width"), box.getFloat("Height"),
                    box.contains("ShadowScale", 99) ? box.getFloat("ShadowScale") : 1);
        }
        public void write(CompoundTag tag) {
            if (equals(AUTO)) { tag.remove(KEY); return; }
            CompoundTag box = new CompoundTag();
            box.putFloat("Length", length);
            box.putFloat("Width", width);
            box.putFloat("Height", height);
            box.putFloat("ShadowScale", shadowScale);
            tag.put(KEY, box);
        }
    }
    private static float clean(float value) { return Float.isFinite(value) ? Math.max(0, Math.min(MAX, value)) : 0; }
    public static synchronized Settings get(DataDisplay display) { return VALUES.getOrDefault(display, AUTO); }
    public static synchronized void put(DataDisplay display, Settings settings) {
        if (settings == null || settings.equals(AUTO)) VALUES.remove(display);
        else VALUES.put(display, settings);
    }
    public static void read(DataDisplay display, CompoundTag tag) { put(display, Settings.read(tag)); }
    public static void write(DataDisplay display, CompoundTag tag) { get(display).write(tag); }

    public static synchronized EntityDimensions apply(EntityNPCInterface npc, EntityDimensions nativeSize) {
        Rectangle previous = RECTANGLES.get(npc);
        // Parent and subclass RETURN injections must not apply the same setting twice.
        if (previous != null && nativeSize == previous.dimensions) return nativeSize;
        Settings settings = get(npc.display);
        if (npc.display == null || npc.display.getHitboxState() == 1 || nativeSize == null || nativeSize.width() <= 1.0E-5F
                || settings.length == 0 && settings.width == 0 && settings.height == 0) {
            RECTANGLES.remove(npc);
            return nativeSize;
        }
        float modelSize = npc.display.getSize();
        float scale = Float.isFinite(modelSize) && modelSize > 0 ? modelSize / 5 : 1;
        float x = settings.width == 0 ? nativeSize.width() : settings.width * scale;
        float z = settings.length == 0 ? nativeSize.width() : settings.length * scale;
        float height = settings.height == 0 ? nativeSize.height() : settings.height * scale;
        float width = Math.max(x, z);
        float vertical = nativeSize.height() > 0 ? height / nativeSize.height() : 1;
        EntityDimensions dimensions = new EntityDimensions(width, height, nativeSize.eyeHeight() * vertical,
                nativeSize.attachments().scale(width / nativeSize.width(), vertical, width / nativeSize.width()), nativeSize.fixed());
        RECTANGLES.put(npc, new Rectangle(dimensions, x / width, z / width));
        return dimensions;
    }

    /** Final record dimensions stay square for navigation; the real box keeps independent X/Z. */
    public static synchronized AABB box(EntityNPCInterface npc, AABB nativeBox) {
        Rectangle rectangle = RECTANGLES.get(npc);
        if (rectangle == null || npc.display == null || npc.display.getHitboxState() == 1) return nativeBox;
        double width = nativeBox.getXsize();
        if (width <= 1.0E-5) return nativeBox;
        double x = (nativeBox.minX + nativeBox.maxX) / 2, z = (nativeBox.minZ + nativeBox.maxZ) / 2;
        double halfX = width * rectangle.xRatio / 2, halfZ = nativeBox.getZsize() * rectangle.zRatio / 2;
        return new AABB(x - halfX, nativeBox.minY, z - halfZ, x + halfX, nativeBox.maxY, z + halfZ);
    }
}
