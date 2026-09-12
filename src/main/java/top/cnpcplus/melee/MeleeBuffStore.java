package top.cnpcplus.melee;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import noppes.npcs.entity.data.DataMelee;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 近战附加 BUFF 列表的挂载点（哈基彬需求 A3）。
 *
 * <h3>为什么需要它</h3>
 * 原版 {@code DataMelee} 只能配**一个**效果，而且候选表是
 * {@code SubGuiNpcMeleeProperties} 静态块（反编译 111-124 行）里硬编码的 34 项
 * （{@code gui.none} + {@code PotionEffectType.getMCType(1..32)} + 点燃），
 * **取不到任何其他 mod 注册的效果**。需求要的是「从 Minecraft 已注册的全部药水效果
 * （包括 mods）里选多个，各自设等级和时长」。
 *
 * <h3>为什么用注册名字符串做键，而不是数字 id</h3>
 * 注册表的整数 id 由**运行期注册顺序**决定，mod 增减或加载顺序变化都会让同一个 id
 * 指向不同效果，存档就会串味。注册名是稳定标识，也是原版药水存档自己用的方式。
 *
 * <p>顺带一句：CNPC 自己的「药水效果职业」{@code JobHealer} 用的就是数字 id
 * （{@code JobHealer.java:42,52} 的 {@code HashMap<Integer,Integer>} + {@code BeaconEffects}），
 * 那是个已经存在的隐患，我们不跟随。
 *
 * <h3>数据形态</h3>
 * {@code LinkedHashMap<String 注册名, int[]{等级, 秒数}>}，保序以便界面稳定。
 * 等级存 amplifier（0 = I 级），与原版 {@code MobEffectInstance} 一致。
 * 时长存**秒**，与原版近战那个「时间」输入框同单位（施加时才 ×20）。
 *
 * <h3>为什么是 WeakHashMap 外挂而不是 mixin @Unique 字段</h3>
 * GUI（客户端）、命中逻辑（服务端）与 NBT 读写三处要访问同一份数据，
 * 而跨 mixin 访问 {@code @Unique} 成员会让 Mixin 转换器无法解析。
 * 与既有的 {@code MeleeLinesStorage}、{@code SongListStore} 同一套模式，
 * 且刻意放在 mixin 包外（mixin 包内的类不能被外部直接引用）。
 *
 * <p>单人模式下客户端线程与内置服务端线程共享这张静态表，所有写操作加锁。
 */
public final class MeleeBuffStore {

    /** NBT 键。挂在 DataMelee 自己的复合标签里，随 STATS 菜单包全量同步。 */
    public static final String NBT_KEY = "CNPCPlusMeleeBuffs";

    /** 单项上限，防止误操作把整表 200+ 效果全加上导致每次命中刷屏。 */
    public static final int MAX_ENTRIES = 32;

    public static final int MAX_AMPLIFIER = 255;
    public static final int MAX_SECONDS = 99999;

    private static final Map<DataMelee, Map<String, int[]>> BUFFS = new WeakHashMap<>();
    private static final Object LOCK = new Object();

    private MeleeBuffStore() {
    }

    /** 取得（必要时创建）某个 NPC 的 BUFF 表。返回的是实表，可直接改。 */
    public static Map<String, int[]> get(DataMelee melee) {
        if (melee == null) return new LinkedHashMap<>();
        synchronized (LOCK) {
            return BUFFS.computeIfAbsent(melee, k -> new LinkedHashMap<>());
        }
    }

    /** 只读，不创建。写 NBT 时用，避免给没配过的 NPC 凭空建条目。 */
    public static Map<String, int[]> peek(DataMelee melee) {
        if (melee == null) return null;
        synchronized (LOCK) {
            return BUFFS.get(melee);
        }
    }

    public static int size(DataMelee melee) {
        Map<String, int[]> map = peek(melee);
        synchronized (LOCK) {
            return map == null ? 0 : map.size();
        }
    }

    public static void put(DataMelee melee, String registryName, int amplifier, int seconds) {
        if (melee == null || registryName == null || registryName.isEmpty()) return;
        Map<String, int[]> map = get(melee);
        synchronized (LOCK) {
            if (!map.containsKey(registryName) && map.size() >= MAX_ENTRIES) return;
            map.put(registryName, new int[]{clampAmplifier(amplifier), clampSeconds(seconds)});
        }
    }

    public static void remove(DataMelee melee, String registryName) {
        Map<String, int[]> map = peek(melee);
        if (map == null || registryName == null) return;
        synchronized (LOCK) {
            map.remove(registryName);
        }
    }

    public static void clear(DataMelee melee) {
        Map<String, int[]> map = peek(melee);
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

    /** 注册名 → MobEffect。查不到（对应 mod 被移除）返回 null，调用方跳过即可。 */
    public static MobEffect resolve(String registryName) {
        if (registryName == null || registryName.isEmpty()) return null;
        try {
            return BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation(registryName));
        } catch (Exception e) {
            return null;
        }
    }

    /** MobEffect → 注册名。 */
    public static String nameOf(MobEffect effect) {
        if (effect == null) return null;
        ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(effect);
        return key == null ? null : key.toString();
    }

    // ---------------------------------------------------------------- NBT

    /**
     * 写入。挂在 DataMelee 的复合标签上，因此自动随
     * {@code SPacketMenuSave(STATS)} / {@code SPacketMenuGet(STATS)} 两端同步，
     * 也自动进存档，无需新增网络包。
     *
     * <p>依据：{@code DataStats.save/readToNBT}（反编译 79-80 / 99-100 行）
     * 直接把 {@code melee} 展开到 DataStats 自己的顶层标签上，没有子标签包裹。
     */
    public static void write(DataMelee melee, CompoundTag compound) {
        if (compound == null) return;
        Map<String, int[]> map = peek(melee);
        synchronized (LOCK) {
            if (map == null || map.isEmpty()) {
                // 明确移除而不是留空列表：让「清空了 BUFF」这件事能正确落盘。
                compound.remove(NBT_KEY);
                return;
            }
            ListTag list = new ListTag();
            for (Map.Entry<String, int[]> entry : map.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                CompoundTag tag = new CompoundTag();
                tag.putString("Effect", entry.getKey());
                tag.putInt("Amp", clampAmplifier(entry.getValue()[0]));
                tag.putInt("Seconds", clampSeconds(entry.getValue()[1]));
                list.add(tag);
            }
            compound.put(NBT_KEY, list);
        }
    }

    /**
     * 读取。
     *
     * <p>「键不存在」与「键存在但是空列表」这里统一按「以 NBT 为准」处理 ——
     * 因为原版 {@code DataMelee.load} 是全量覆盖语义（7 个字段全部无条件覆写），
     * 保持一致更不容易出意外。
     */
    public static void read(DataMelee melee, CompoundTag compound) {
        if (melee == null || compound == null) return;
        Map<String, int[]> map = get(melee);
        synchronized (LOCK) {
            map.clear();
            if (!compound.contains(NBT_KEY, Tag.TAG_LIST)) return;
            ListTag list = compound.getList(NBT_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size() && map.size() < MAX_ENTRIES; i++) {
                CompoundTag tag = list.getCompound(i);
                String name = tag.getString("Effect");
                if (name == null || name.isEmpty()) continue;
                map.put(name, new int[]{
                        clampAmplifier(tag.getInt("Amp")),
                        clampSeconds(tag.getInt("Seconds"))
                });
            }
        }
    }

    /** 界面用：键列表快照（保持插入顺序）。 */
    public static List<String> keys(DataMelee melee) {
        Map<String, int[]> map = peek(melee);
        List<String> out = new ArrayList<>();
        if (map == null) return out;
        synchronized (LOCK) {
            out.addAll(map.keySet());
        }
        return out;
    }
}
