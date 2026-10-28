package bin.cnpcplus.melee;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import noppes.npcs.entity.data.DataRanged;
import noppes.npcs.entity.EntityProjectile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import net.minecraft.world.Explosion;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** 238-4：远程投掷物的多效果列表，挂在 DataRanged 的复合 NBT 上。 */
public final class RangedBuffStore {

    public static final String NBT_KEY = "CNPCPlusRangedBuffs";
    public static final int MAX_ENTRIES = 32;
    public static final int MAX_AMPLIFIER = 255;
    public static final int MAX_SECONDS = 99999;
    public static final String FIRE_KEY = "cnpcplus:fire";

    private static final Map<Object, Map<String, int[]>> BUFFS = new WeakHashMap<>();
    private static final Object LOCK = new Object();
    private static final ThreadLocal<EntityProjectile> EXPLODING = new ThreadLocal<>();

    private RangedBuffStore() {
    }

    public static Explosion explode(EntityProjectile projectile, World world, Entity source,
                                    double x, double y, double z, float strength, boolean fire, boolean terrain) {
        EntityProjectile previous = EXPLODING.get();
        EXPLODING.set(projectile);
        try { return world.newExplosion(source, x, y, z, strength, fire, terrain); }
        finally {
            if (previous == null) EXPLODING.remove();
            else EXPLODING.set(previous);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        EntityProjectile projectile = EXPLODING.get();
        if (projectile == null || event.getWorld().isRemote) return;
        // Start 事件取消时不会到 Detonate；使用 Forge 最终受影响列表，不另扫方盒。
        for (Entity entity : event.getAffectedEntities()) {
            if (entity instanceof EntityLivingBase) apply(projectile, (EntityLivingBase) entity);
        }
    }

    public static void migrateLegacy(DataRanged ranged) {
        int type = ranged.getEffectType();
        if (type == 0) return;
        String key = type == 1 ? FIRE_KEY : nameOf(noppes.npcs.api.constants.PotionEffectType.getMCType(type));
        if (key != null && !get(ranged).containsKey(key)) {
            put(ranged, key, ranged.getEffectStrength(), ranged.getEffectTime());
        }
        ranged.setEffect(0, 0, 5);
    }

    public static boolean isFire(String registryName) {
        return FIRE_KEY.equals(registryName);
    }

    public static Map<String, int[]> get(Object ranged) {
        if (ranged == null) return new LinkedHashMap<String, int[]>();
        synchronized (LOCK) {
            Map<String, int[]> map = BUFFS.get(ranged);
            if (map == null) {
                map = new LinkedHashMap<String, int[]>();
                BUFFS.put(ranged, map);
            }
            return map;
        }
    }

    public static Map<String, int[]> peek(Object ranged) {
        if (ranged == null) return null;
        synchronized (LOCK) {
            return BUFFS.get(ranged);
        }
    }

    public static void put(DataRanged ranged, String registryName, int amplifier, int seconds) {
        if (ranged == null || registryName == null || registryName.isEmpty()) return;
        Map<String, int[]> map = get(ranged);
        synchronized (LOCK) {
            if (!map.containsKey(registryName) && map.size() >= MAX_ENTRIES) return;
            map.put(registryName, new int[]{clampAmplifier(amplifier), clampSeconds(seconds)});
        }
    }

    public static void remove(DataRanged ranged, String registryName) {
        Map<String, int[]> map = peek(ranged);
        if (map == null || registryName == null) return;
        synchronized (LOCK) {
            map.remove(registryName);
        }
    }

    public static void clear(DataRanged ranged) {
        Map<String, int[]> map = peek(ranged);
        if (map == null) return;
        synchronized (LOCK) {
            map.clear();
        }
    }

    public static int clampAmplifier(int value) {
        if (value < 0) return 0;
        return Math.min(value, MAX_AMPLIFIER);
    }

    public static int clampSeconds(int value) {
        if (value < 1) return 1;
        return Math.min(value, MAX_SECONDS);
    }

    public static Potion resolve(String registryName) {
        if (registryName == null || registryName.isEmpty()) return null;
        try {
            return Potion.REGISTRY.getObject(new ResourceLocation(registryName));
        } catch (Exception e) {
            return null;
        }
    }

    public static String nameOf(Potion potion) {
        if (potion == null) return null;
        ResourceLocation key = Potion.REGISTRY.getNameForObject(potion);
        return key == null ? null : key.toString();
    }

    public static void snapshot(DataRanged ranged, EntityProjectile projectile) {
        synchronized (LOCK) {
            Map<String, int[]> source = BUFFS.get(ranged);
            if (source == null || source.isEmpty()) {
                BUFFS.remove(projectile);
                return;
            }
            Map<String, int[]> copy = new LinkedHashMap<>();
            for (Map.Entry<String, int[]> entry : source.entrySet()) copy.put(entry.getKey(), entry.getValue().clone());
            BUFFS.put(projectile, copy);
        }
    }

    public static void apply(Object ranged, net.minecraft.entity.EntityLivingBase victim) {
        if (ranged == null || victim == null) return;
        Map<String, int[]> buffs = peek(ranged);
        if (buffs == null || buffs.isEmpty()) return;
        synchronized (LOCK) {
            for (Map.Entry<String, int[]> entry : buffs.entrySet()) {
                int[] value = entry.getValue();
                if (value == null || value.length < 2) continue;
                if (isFire(entry.getKey())) {
                    victim.setFire(clampSeconds(value[1]));
                    continue;
                }
                Potion potion = resolve(entry.getKey());
                if (potion != null) {
                    victim.addPotionEffect(new net.minecraft.potion.PotionEffect(
                            potion, clampSeconds(value[1]) * 20, clampAmplifier(value[0])));
                }
            }
        }
    }

    public static void write(Object ranged, NBTTagCompound compound) {
        if (compound == null) return;
        Map<String, int[]> map = peek(ranged);
        if (map == null || map.isEmpty()) {
            compound.removeTag(NBT_KEY);
            return;
        }
        NBTTagList list = new NBTTagList();
        synchronized (LOCK) {
            for (Map.Entry<String, int[]> entry : map.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("Effect", entry.getKey());
                tag.setInteger("Amp", clampAmplifier(entry.getValue()[0]));
                tag.setInteger("Seconds", clampSeconds(entry.getValue()[1]));
                list.appendTag(tag);
            }
        }
        compound.setTag(NBT_KEY, list);
    }

    public static void read(Object ranged, NBTTagCompound compound) {
        if (ranged == null || compound == null) return;
        if (!compound.hasKey(NBT_KEY, 9)) {
            synchronized (LOCK) { BUFFS.remove(ranged); }
            return;
        }
        Map<String, int[]> map = get(ranged);
        synchronized (LOCK) {
            map.clear();
            if (!compound.hasKey(NBT_KEY, 9)) return;
            NBTTagList list = compound.getTagList(NBT_KEY, 10);
            for (int i = 0; i < list.tagCount() && map.size() < MAX_ENTRIES; i++) {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                String name = tag.getString("Effect");
                if (name == null || name.isEmpty()) continue;
                map.put(name, new int[]{
                        clampAmplifier(tag.getInteger("Amp")),
                        clampSeconds(tag.getInteger("Seconds"))
                });
            }
        }
    }

    public static List<String> keys(DataRanged ranged) {
        Map<String, int[]> map = peek(ranged);
        List<String> out = new ArrayList<String>();
        if (map == null) return out;
        synchronized (LOCK) {
            out.addAll(map.keySet());
        }
        return out;
    }
}
