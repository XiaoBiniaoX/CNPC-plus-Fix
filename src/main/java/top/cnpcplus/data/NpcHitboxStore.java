package top.cnpcplus.data;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityDimensions;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataDisplay;

/** Zero means native dimensions; explicit dimensions are blocks at display Size=5. */
public final class NpcHitboxStore {
    public static final String KEY = "CNPCPlusHitbox";
    public static final float MAX = 100.0f;
    public static final Settings AUTO = new Settings(0, 0, 0);
    private static final Map<DataDisplay, Settings> VALUES = new WeakHashMap<>();

    private NpcHitboxStore() {}

    public record Settings(float length, float width, float height, float shadowScale) {
        public Settings(float length, float width, float height) {
            this(length, width, height, 1);
        }

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
            if (equals(AUTO)) {
                tag.remove(KEY);
                return;
            }
            CompoundTag box = new CompoundTag();
            box.putFloat("Length", length);
            box.putFloat("Width", width);
            box.putFloat("Height", height);
            box.putFloat("ShadowScale", shadowScale);
            tag.put(KEY, box);
        }

        public EntityDimensions dimensions(EntityDimensions nativeSize, float modelSize) {
            // Never expand a disabled/corpse hitbox or apply twice across subclass/super calls.
            if ((length == 0 && width == 0 && height == 0) || nativeSize == null || nativeSize.width <= 1.0E-5f
                    || nativeSize instanceof NpcRectangularDimensions) return nativeSize;
            float scale = Float.isFinite(modelSize) && modelSize > 0 ? modelSize / 5.0f : 1.0f;
            return new NpcRectangularDimensions(
                    width == 0 ? nativeSize.width : width * scale,
                    length == 0 ? nativeSize.width : length * scale,
                    height == 0 ? nativeSize.height : height * scale);
        }
    }

    private static float clean(float value) {
        return Float.isFinite(value) ? Math.max(0, Math.min(MAX, value)) : 0;
    }

    public static Settings get(DataDisplay display) {
        synchronized (VALUES) {
            return VALUES.getOrDefault(display, AUTO);
        }
    }

    public static void put(DataDisplay display, Settings settings) {
        if (display == null) return;
        synchronized (VALUES) {
            if (settings == null || settings.equals(AUTO)) VALUES.remove(display);
            else VALUES.put(display, settings);
        }
    }

    public static void read(DataDisplay display, CompoundTag tag) {
        put(display, Settings.read(tag));
    }

    public static void write(DataDisplay display, CompoundTag tag) {
        get(display).write(tag);
    }

    public static EntityDimensions apply(EntityNPCInterface npc, EntityDimensions nativeSize) {
        if (npc.display == null || npc.display.getHitboxState() == 1) return nativeSize;
        EntityDimensions size = get(npc.display).dimensions(nativeSize, npc.display.getSize());
        if (size != null && npc.level() != null && size.width / 2 > npc.level().getMaxEntityRadius()) {
            npc.level().increaseMaxEntityRadius(size.width / 2);
        }
        return size;
    }
}
