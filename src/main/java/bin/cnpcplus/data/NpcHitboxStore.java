package bin.cnpcplus.data;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataDisplay;

/** Explicit dimensions are blocks at Size=5; zero retains the native/model-addon dimension. */
public final class NpcHitboxStore {
    public static final String KEY = "CNPCPlusHitbox";
    public static final float MAX = 100;
    public static final Settings AUTO = new Settings(0, 0, 0);
    private static final Map<DataDisplay, Settings> VALUES = new WeakHashMap<>();
    private static final Map<DataDisplay, Boolean> SOLID = new WeakHashMap<>();
    private static final Map<EntityNPCInterface, float[]> DIMENSIONS = new WeakHashMap<>();
    private NpcHitboxStore() { }

    public static final class Settings {
        public final float length, width, height;
        public Settings(float length, float width, float height) {
            this.length = clean(length); this.width = clean(width); this.height = clean(height);
        }
        private static float clean(float value) { return Float.isFinite(value) ? Math.max(0, Math.min(MAX, value)) : 0; }
        public static Settings read(NBTTagCompound tag) {
            NBTTagCompound box = tag.getCompoundTag(KEY);
            return new Settings(box.getFloat("Length"), box.getFloat("Width"), box.getFloat("Height"));
        }
        public void write(NBTTagCompound tag) {
            if (length == 0 && width == 0 && height == 0) { tag.removeTag(KEY); return; }
            NBTTagCompound box = new NBTTagCompound();
            box.setFloat("Length", length); box.setFloat("Width", width); box.setFloat("Height", height);
            tag.setTag(KEY, box);
        }
    }
    public static Settings get(DataDisplay display) { return VALUES.getOrDefault(display, AUTO); }
    public static void put(DataDisplay display, Settings settings) { VALUES.put(display, settings); }
    public static boolean isSolid(DataDisplay display) { return SOLID.containsKey(display); }
    public static int mode(DataDisplay display) { return isSolid(display) ? 2 : display.getHasHitbox() ? 0 : 1; }
    public static void solid(DataDisplay display, boolean solid) {
        if (solid) SOLID.put(display, true); else SOLID.remove(display);
    }
    public static void setMode(EntityNPCInterface npc, int mode) {
        npc.display.setHasHitbox(mode != 1);
        solid(npc.display, mode == 2);
        npc.updateClient = true;
        npc.updateHitbox();
    }
    public static void beginRefresh(EntityNPCInterface npc) { DIMENSIONS.remove(npc); }

    /** Called once after the concrete NPC/model addon has computed its native size. */
    public static void refresh(EntityNPCInterface npc) {
        Settings settings = get(npc.display);
        DIMENSIONS.remove(npc);
        if (!npc.display.getHasHitbox() || npc.width <= 1.0E-5f || npc.isKilled()
                || npc.deathTime > 0 || npc.currentAnimation == 2 || npc.currentAnimation == 7
                || settings.length == 0 && settings.width == 0 && settings.height == 0) return;
        float scale = npc.display.getSize() / 5f;
        float x = settings.width == 0 ? npc.width : settings.width * scale;
        float z = settings.length == 0 ? npc.width : settings.length * scale;
        float y = settings.height == 0 ? npc.height : settings.height * scale;
        DIMENSIONS.put(npc, new float[]{x, z, y});
        npc.width = Math.max(x, z); npc.height = y;
        if (npc.width / 2 > World.MAX_ENTITY_RADIUS) World.MAX_ENTITY_RADIUS = npc.width / 2;
        npc.setEntityBoundingBox(new AxisAlignedBB(npc.posX - x / 2, npc.posY, npc.posZ - z / 2,
                npc.posX + x / 2, npc.posY + y, npc.posZ + z / 2));
    }

    /** Keep the incoming center/bottom, including movement before posX/posY are updated. */
    public static AxisAlignedBB apply(EntityNPCInterface npc, AxisAlignedBB box) {
        float[] size = DIMENSIONS.get(npc);
        if (size == null || npc.display == null || !npc.display.getHasHitbox() || npc.width <= 1.0E-5f
                || npc.isKilled() || npc.deathTime > 0 || npc.currentAnimation == 2 || npc.currentAnimation == 7) return box;
        double x = (box.minX + box.maxX) / 2, z = (box.minZ + box.maxZ) / 2;
        return new AxisAlignedBB(x - size[0] / 2, box.minY, z - size[1] / 2,
                x + size[0] / 2, box.minY + size[2], z + size[1] / 2);
    }
}
