package bin.cnpcplus.stats;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import noppes.npcs.Resistances;

/** Vanilla 1.12.2 sources, using the same script IDs as the modern edition. */
public final class DamageResistanceTable {
    public static final float LIMIT = 100000;
    public static final String[] TYPES = {
        "in_fire", "lightning_bolt", "on_fire", "lava", "hot_floor", "in_wall", "cramming", "drown",
        "starve", "cactus", "fall", "fly_into_wall", "out_of_world", "generic", "magic", "wither",
        "dragon_breath", "falling_block", "falling_anvil", "mob_attack", "player_attack", "arrow",
        "fireball", "thrown", "indirect_magic", "thorns", "explosion", "player_explosion", "fireworks"
    };
    private static final String[] SOURCES = {
        "inFire", "lightningBolt", "onFire", "lava", "hotFloor", "inWall", "cramming", "drown",
        "starve", "cactus", "fall", "flyIntoWall", "outOfWorld", "generic", "magic", "wither",
        "dragonBreath", "fallingBlock", "anvil", "mob", "player", "arrow", "fireball", "thrown",
        "indirectMagic", "thorns", "explosion", "explosion.player", "fireworks"
    };
    private static final Map<Resistances, float[]> VALUES = new WeakHashMap<>();
    private DamageResistanceTable() {}
    public static int index(String id) {
        if (id == null || !id.matches("(?:minecraft:)?[a-z0-9_]+")) throw new IllegalArgumentException("Not a vanilla damage type: " + id);
        String path = new ResourceLocation(id).getPath();
        for (int i = 0; i < TYPES.length; i++) if (TYPES[i].equals(path)) return i;
        throw new IllegalArgumentException("Unknown vanilla 1.12.2 damage type: " + id);
    }
    public static int source(DamageSource source) {
        if (source.isProjectile() && "onFire".equals(source.damageType)) return index("fireball");
        for (int i = 0; i < SOURCES.length; i++) if (SOURCES[i].equals(source.damageType)) return i;
        return source.isProjectile() ? index("arrow") : -1;
    }
    public static float validate(float value) {
        if (!Float.isFinite(value) || Math.abs(value) > LIMIT) throw new IllegalArgumentException("Resistance must be -100000..100000 percent");
        return value;
    }
    public static synchronized float[] values(Resistances resistance) { return VALUES.computeIfAbsent(resistance, key -> new float[TYPES.length]); }
    public static float get(Resistances resistance, String type) { return values(resistance)[index(type)]; }
    public static void set(Resistances resistance, String type, float value) { values(resistance)[index(type)] = validate(value); }
    public static void read(Resistances resistance, NBTTagCompound tag) {
        float[] result = new float[TYPES.length];
        NBTTagCompound array = tag.getCompoundTag("CNPCPlusDamageTypes");
        for (int i = 0; i < result.length; i++) {
            String type = TYPES[i];
            float legacy = 1;
            if (type.equals("generic") || type.equals("cramming") || type.equals("falling_anvil") || type.equals("cactus"))
                legacy = tag.hasKey("CNPCPlusGenericDamage", 99) ? tag.getFloat("CNPCPlusGenericDamage") : 1;
            else if (type.equals("indirect_magic") || type.equals("thorns") || type.equals("magic") || type.equals("lightning_bolt"))
                legacy = tag.hasKey("CNPCPlusMagicDamage", 99) ? tag.getFloat("CNPCPlusMagicDamage") : 1;
            else if (type.equals("arrow") || type.equals("fireball") || type.equals("thrown")) legacy = tag.hasKey("Arrow", 99) ? tag.getFloat("Arrow") : 1;
            else if (type.equals("mob_attack") || type.equals("player_attack")) legacy = tag.hasKey("Melee", 99) ? tag.getFloat("Melee") : 1;
            else if (type.equals("explosion") || type.equals("player_explosion")) legacy = tag.hasKey("Explosion", 99) ? tag.getFloat("Explosion") : 1;
            float value = tag.hasKey("CNPCPlusDamageTypes", 10) ? array.getFloat(type) : (legacy - 1) * 100;
            result[i] = Float.isFinite(value) ? Math.max(-LIMIT, Math.min(LIMIT, value)) : 0;
        }
        synchronized (DamageResistanceTable.class) { VALUES.put(resistance, result); }
        float knockback = tag.hasKey("Knockback", 99) ? tag.getFloat("Knockback") : 1;
        resistance.knockback = Float.isFinite(knockback) ? Math.max(1 - LIMIT / 100, Math.min(1 + LIMIT / 100, knockback)) : 1;
    }
    public static void write(Resistances resistance, NBTTagCompound tag) {
        NBTTagCompound array = new NBTTagCompound();
        float[] values = values(resistance);
        for (int i = 0; i < TYPES.length; i++) array.setFloat(TYPES[i], values[i]);
        tag.setTag("CNPCPlusDamageTypes", array);
        tag.removeTag("CNPCPlusGenericDamage"); tag.removeTag("CNPCPlusMagicDamage");
    }
}
