package bin.cnpcplus.mixin.leakfix;

import noppes.npcs.controllers.data.MarkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * {@code MarkData.dataMap} 静态访问器（内存泄露修复）。
 *
 * <h2>泄露机制（1.21.1 字节码实证）</h2>
 * 与 {@code PlayerData.dataMap} 同构：{@code private static Map<Integer,MarkData>}，
 * 只在 {@code MarkData.get(LivingEntity)} 里填充，无任何移除逻辑；
 * 每个条目持 {@code private LivingEntity entity} —— 实体删除/世界卸载后
 * 引用链被永久钉住。
 *
 * <p>由 {@code bin.cnpcplus.leakfix.CnpcStaticsCleanup} 在 ServerStopped 时 clear。
 */
@Mixin(value = MarkData.class, remap = false)
public interface MarkDataAccessor {

    @Accessor("dataMap")
    static Map<Integer, MarkData> cnpcplus$dataMap() {
        throw new AssertionError("mixin");
    }
}
