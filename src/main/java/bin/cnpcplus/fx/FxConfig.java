package bin.cnpcplus.fx;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumParticleTypes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 粒子 → 形状树。与 1.20.1 的 ParticleId/Shapes NBT 格式兼容。 */
public final class FxConfig {
    public String particleId;
    public final List<FxShape> shapes = new ArrayList<>();

    public FxConfig(String particleId) { this.particleId = particleId; }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("ParticleId", particleId);
        NBTTagList list = new NBTTagList();
        for (FxShape shape : shapes) list.appendTag(shape.toNBT());
        tag.setTag("Shapes", list);
        return tag;
    }

    public static List<FxConfig> read(NBTTagList tags) {
        Map<String, FxConfig> configs = new LinkedHashMap<>();
        for (int i = 0; i < tags.tagCount() && i < 64; i++) {
            NBTTagCompound tag = tags.getCompoundTagAt(i);
            String id = tag.getString("ParticleId");
            if (id.isEmpty() || id.length() > 128) continue;
            if (!configs.containsKey(id) && configs.size() >= 16) continue;
            FxConfig config = configs.computeIfAbsent(id, FxConfig::new);
            NBTTagList shapes = tag.getTagList("Shapes", 10);
            for (int j = 0; j < shapes.tagCount() && j < 64; j++) {
                FxShape shape = FxShape.fromNBT(shapes.getCompoundTagAt(j));
                if (config.shapes.stream().noneMatch(existing -> existing.type == shape.type)) config.shapes.add(shape);
            }
        }
        List<FxConfig> result = new ArrayList<>(configs.values());
        result.removeIf(config -> config.shapes.isEmpty());
        return result;
    }

    public static NBTTagList write(List<FxConfig> configs) {
        NBTTagList list = new NBTTagList();
        if (configs != null) for (FxConfig config : configs) if (config != null) list.appendTag(config.toNBT());
        return list;
    }

    public static List<FxConfig> copy(List<FxConfig> configs) { return read(write(configs)); }

    public static String id(EnumParticleTypes type) { return "minecraft:" + type.getParticleName(); }

    public static EnumParticleTypes resolve(String id) {
        if (id == null) return null;
        String name = id.startsWith("minecraft:") ? id.substring(10) : id;
        for (EnumParticleTypes type : EnumParticleTypes.values()) {
            if (type.getArgumentCount() == 0 && (type.getParticleName().equals(name)
                    || type.name().toLowerCase(Locale.ROOT).equals(name))) return type;
        }
        switch (name) {
            case "large_smoke": return EnumParticleTypes.SMOKE_LARGE;
            case "happy_villager": return EnumParticleTypes.VILLAGER_HAPPY;
            case "angry_villager": return EnumParticleTypes.VILLAGER_ANGRY;
            case "enchanted_hit": return EnumParticleTypes.CRIT_MAGIC;
            case "enchant": return EnumParticleTypes.ENCHANTMENT_TABLE;
            case "end_rod": return EnumParticleTypes.END_ROD;
            case "poof": return EnumParticleTypes.EXPLOSION_NORMAL;
            default: return null;
        }
    }
}
