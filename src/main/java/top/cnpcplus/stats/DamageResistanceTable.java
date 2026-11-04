package top.cnpcplus.stats;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.Resistances;

/** Fixed vanilla 1.20.1 catalogue; percentages, not damage multipliers. */
public final class DamageResistanceTable {
    public static final float LIMIT = 100000;
    public static final String[] TYPES = {
        "in_fire", "lightning_bolt", "on_fire", "lava", "hot_floor", "in_wall", "cramming", "drown",
        "starve", "cactus", "fall", "fly_into_wall", "out_of_world", "generic", "magic", "wither",
        "dragon_breath", "dry_out", "sweet_berry_bush", "freeze", "stalagmite", "falling_block",
        "falling_anvil", "falling_stalactite", "sting", "mob_attack", "mob_attack_no_aggro", "player_attack",
        "arrow", "trident", "mob_projectile", "fireworks", "fireball", "unattributed_fireball", "wither_skull",
        "thrown", "indirect_magic", "thorns", "explosion", "player_explosion", "sonic_boom", "bad_respawn_point",
        "outside_border", "generic_kill"
    };
    private static final Map<Resistances, float[]> VALUES = new WeakHashMap<>();
    private DamageResistanceTable() {}

    public static int index(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !key.getNamespace().equals("minecraft")) throw new IllegalArgumentException("Not a vanilla damage type: " + id);
        for (int i = 0; i < TYPES.length; i++) if (TYPES[i].equals(key.getPath())) return i;
        throw new IllegalArgumentException("Unknown vanilla damage type: " + id);
    }

    public static float validate(float percent) {
        if (!Float.isFinite(percent) || Math.abs(percent) > LIMIT) throw new IllegalArgumentException("Resistance must be -100000..100000 percent");
        return percent;
    }

    public static synchronized float[] values(Resistances resistance) {
        return VALUES.computeIfAbsent(resistance, key -> new float[TYPES.length]);
    }

    public static float get(Resistances resistance, String type) { return values(resistance)[index(type)]; }
    public static void set(Resistances resistance, String type, float percent) { values(resistance)[index(type)] = validate(percent); }

    public static void read(Resistances resistance, CompoundTag tag) {
        float[] result = new float[TYPES.length];
        CompoundTag array = tag.getCompound("CNPCPlusDamageTypes");
        for (int i = 0; i < TYPES.length; i++) {
            String type = TYPES[i];
            float legacy = switch (type) {
                case "generic", "cramming", "falling_anvil", "cactus" -> tag.contains("CNPCPlusGeneric") ? tag.getFloat("CNPCPlusGeneric") : 1;
                case "indirect_magic", "sonic_boom", "outside_border", "thorns", "magic", "lightning_bolt", "generic_kill" -> tag.contains("CNPCPlusMagic") ? tag.getFloat("CNPCPlusMagic") : 1;
                case "arrow", "trident", "mob_projectile", "fireball", "unattributed_fireball", "wither_skull", "thrown" -> resistance.arrow;
                case "mob_attack", "mob_attack_no_aggro", "player_attack" -> resistance.melee;
                case "explosion", "player_explosion" -> resistance.explosion;
                default -> 1;
            };
            float value = tag.contains("CNPCPlusDamageTypes") ? array.getFloat(type) : (legacy - 1) * 100;
            result[i] = Float.isFinite(value) ? Math.max(-LIMIT, Math.min(LIMIT, value)) : 0;
        }
        synchronized (DamageResistanceTable.class) { VALUES.put(resistance, result); }
        float knockback = tag.contains("Knockback") ? tag.getFloat("Knockback") : 1;
        resistance.knockback = Float.isFinite(knockback) ? Math.max(1 - LIMIT / 100, Math.min(1 + LIMIT / 100, knockback)) : 1;
    }

    public static void write(Resistances resistance, CompoundTag tag) {
        CompoundTag array = new CompoundTag();
        float[] values = values(resistance);
        for (int i = 0; i < TYPES.length; i++) array.putFloat(TYPES[i], values[i]);
        tag.put("CNPCPlusDamageTypes", array);
        tag.remove("CNPCPlusGeneric");
        tag.remove("CNPCPlusMagic");
    }
}
