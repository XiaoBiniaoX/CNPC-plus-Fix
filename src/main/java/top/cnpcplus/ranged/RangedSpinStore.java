package top.cnpcplus.ranged;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import noppes.npcs.entity.data.DataRanged;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 238-9：弹射物自旋速度系数挂载点。
 *
 * <h3>语义</h3>
 * 原版每 tick 硬编码 {@code (isBlock ? 10 : 20) * getSpeed()} 度（反编译
 * {@code EntityProjectile.tick :293-296}）。本系数<b>替换 10/20 那个系数</b>
 * （仍 ×getSpeed，原版同单位）；0（未配置）= 回落原公式，零变化。
 *
 * <h3>NBT</h3>
 * 键 {@code CNPCPlusSpinSpeed}（float）挂 DataRanged 复合标签，随 STATS 菜单包双向同步
 * （见 {@code MixinDataRangedExtras}）。
 */
public final class RangedSpinStore {

    public static final String NBT_KEY = "CNPCPlusSpinSpeed";

    /** 上限：防误输入把自旋拉到每 tick 几百度造成眩晕。 */
    public static final float MAX_SPIN = 1000.0f;

    private static final Map<DataRanged, Float> SPINS = new WeakHashMap<>();
    private static final Object LOCK = new Object();

    private RangedSpinStore() {
    }

    /** 未配置返回 0（消费端回落原公式 10/20）。 */
    public static float get(DataRanged ranged) {
        if (ranged == null) return 0.0f;
        synchronized (LOCK) {
            return SPINS.getOrDefault(ranged, 0.0f);
        }
    }

    /** 输入框回写。非有限数按 0（=未配置）；区间 [-MAX_SPIN, MAX_SPIN]，负数=反旋。 */
    public static void put(DataRanged ranged, float factor) {
        if (ranged == null) return;
        if (!Float.isFinite(factor)) factor = 0.0f;
        if (factor > MAX_SPIN) factor = MAX_SPIN;
        if (factor < -MAX_SPIN) factor = -MAX_SPIN;
        synchronized (LOCK) {
            if (factor == 0.0f) {
                SPINS.remove(ranged);
            } else {
                SPINS.put(ranged, factor);
            }
        }
    }

    public static void clear(DataRanged ranged) {
        if (ranged == null) return;
        synchronized (LOCK) {
            SPINS.remove(ranged);
        }
    }

    /** 写入。0 不落键（旧档零变化）；负数（反旋）照写。 */
    public static void write(DataRanged ranged, CompoundTag compound) {
        if (compound == null) return;
        float v = get(ranged);
        synchronized (LOCK) {
            if (v == 0.0f) {
                compound.remove(NBT_KEY);
            } else {
                compound.putFloat(NBT_KEY, v);
            }
        }
    }

    /** 读取。缺键=清配置（回落 0=原公式）。 */
    public static void read(DataRanged ranged, CompoundTag compound) {
        if (ranged == null || compound == null) return;
        if (compound.contains(NBT_KEY, Tag.TAG_ANY_NUMERIC)) {
            put(ranged, compound.getFloat(NBT_KEY));
        } else {
            clear(ranged);
        }
    }
}
