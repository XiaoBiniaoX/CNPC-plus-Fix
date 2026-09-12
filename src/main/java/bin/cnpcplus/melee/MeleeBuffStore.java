package bin.cnpcplus.melee;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import noppes.npcs.entity.data.DataMelee;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 近战附加 BUFF 列表的挂载点（哈基彬需求 B-3）。
 *
 * <h3>为什么需要它</h3>
 * 原版 {@code DataMelee} 只能配**一个**效果，而且只能从 8 种硬编码效果里选
 * （{@code PotionEffectType.getMCType} 的 switch 只覆盖 POISON..WITHER，
 * FIRE 还是走点燃不是药水）。需求要的是「从 Minecraft 已注册的全部药水效果
 * （含 mods）里选多个，各自设等级和时长」。
 *
 * <h3>为什么用注册名字符串做键，而不是数字 id</h3>
 * {@code Potion.getIdFromPotion} 返回的是**运行期注册顺序**决定的整数 id。
 * mods 增减或加载顺序变化都会让同一个 id 指向不同效果，存档就会串味。
 * 注册名（{@code Potion.REGISTRY.getNameForObject}）是稳定标识，
 * 这也是原版药水存档自己用的方式。
 *
 * <h3>数据形态</h3>
 * {@code LinkedHashMap<String 注册名, int[]{等级, 秒数}}，保序以便界面稳定。
 * 等级存的是 amplifier（0 = I 级），与原版 {@code PotionEffect} 一致。
 * 时长存**秒**，与原版近战那个「时间」输入框同单位（施加时才 ×20）。
 *
 * <h3>为什么是 WeakHashMap 外挂而不是 mixin @Unique 字段</h3>
 * GUI（客户端）、命中逻辑（服务端）与 NBT 读写三处要访问同一份数据。
 * 跨 mixin 访问 {@code @Unique} 成员会让 Mixin 转换器无法解析
 * （findings 阶段 23 的崩溃教训）。与既有的 {@code MeleeLineStore}、
 * {@code SongListStore} 同一套模式，且刻意放在 mixin 包外
 * （阶段 22 的 {@code IllegalClassLoadError} 教训）。
 */
public final class MeleeBuffStore {

    /** NBT 键。挂在 DataMelee 自己的复合标签里，随 MainmenuStatsSave 全量同步。 */
    public static final String NBT_KEY = "CNPCPlusMeleeBuffs";

    /** 单项上限，防止误操作把整表 200+ 效果全加上导致每次命中刷屏。 */
    public static final int MAX_ENTRIES = 32;

    public static final int MAX_AMPLIFIER = 255;
    public static final int MAX_SECONDS = 99999;

    private static final Map<DataMelee, Map<String, int[]>> BUFFS =
            new WeakHashMap<DataMelee, Map<String, int[]>>();
    private static final Object LOCK = new Object();

    private MeleeBuffStore() {
    }

    /** 取得（必要时创建）某个 NPC 的 BUFF 表。返回的是实表，可直接改。 */
    public static Map<String, int[]> get(DataMelee melee) {
        if (melee == null) return new LinkedHashMap<String, int[]>();
        synchronized (LOCK) {
            Map<String, int[]> map = BUFFS.get(melee);
            if (map == null) {
                map = new LinkedHashMap<String, int[]>();
                BUFFS.put(melee, map);
            }
            return map;
        }
    }

    /** 只读，不创建。写 NBT 时用，避免给没配过的 NPC 凭空建条目。 */
    public static Map<String, int[]> peek(DataMelee melee) {
        if (melee == null) return null;
        synchronized (LOCK) {
            return BUFFS.get(melee);
        }
    }

    public static boolean isEmpty(DataMelee melee) {
        Map<String, int[]> map = peek(melee);
        return map == null || map.isEmpty();
    }

    public static int size(DataMelee melee) {
        Map<String, int[]> map = peek(melee);
        return map == null ? 0 : map.size();
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
        return value > MAX_AMPLIFIER ? MAX_AMPLIFIER : value;
    }

    public static int clampSeconds(int value) {
        if (value < 1) return 1;
        return value > MAX_SECONDS ? MAX_SECONDS : value;
    }

    /**
     * 注册名 → Potion 实例。查不到（对应 mod 被移除）返回 null，调用方跳过即可。
     */
    public static Potion resolve(String registryName) {
        if (registryName == null || registryName.isEmpty()) return null;
        try {
            return Potion.REGISTRY.getObject(new ResourceLocation(registryName));
        } catch (Exception e) {
            return null;
        }
    }

    /** Potion 实例 → 注册名。 */
    public static String nameOf(Potion potion) {
        if (potion == null) return null;
        ResourceLocation key = Potion.REGISTRY.getNameForObject(potion);
        return key == null ? null : key.toString();
    }

    // ---------------------------------------------------------------- NBT

    /**
     * 写入。挂在 DataMelee 的复合标签上，因此自动随
     * {@code MainmenuStatsSave} / {@code MainmenuStatsGet} 两端同步，
     * 也自动进存档，无需新增网络包。
     */
    public static void write(DataMelee melee, NBTTagCompound compound) {
        if (compound == null) return;
        Map<String, int[]> map = peek(melee);
        if (map == null || map.isEmpty()) {
            // 明确移除而不是留空列表：让「清空了 BUFF」这件事能正确落盘。
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

    /**
     * 读取。
     *
     * 注意「键不存在」与「键存在但是空列表」要区别对待：
     * 前者是旧存档（不该动内存里已有的表），后者是玩家真的清空了。
     * 但这里统一按「以 NBT 为准」处理 —— 因为 DataMelee 的 readFromNBT 是
     * 全量覆盖语义（原版 7 个字段全部无条件覆写），保持一致更不容易出意外。
     */
    public static void read(DataMelee melee, NBTTagCompound compound) {
        if (melee == null || compound == null) return;
        Map<String, int[]> map = get(melee);
        synchronized (LOCK) {
            map.clear();
            if (!compound.hasKey(NBT_KEY, 9)) return;   // 9 = TAG_List
            NBTTagList list = compound.getTagList(NBT_KEY, 10);   // 10 = TAG_Compound
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

    /** 界面用：按注册名排序后的键列表快照。 */
    public static List<String> keys(DataMelee melee) {
        Map<String, int[]> map = peek(melee);
        List<String> out = new ArrayList<String>();
        if (map == null) return out;
        synchronized (LOCK) {
            out.addAll(map.keySet());
        }
        return out;
    }
}
