package bin.cnpcplus.mixin.leakfix;

import noppes.npcs.controllers.data.PlayerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * {@code PlayerData.dataMap} 静态访问器（内存泄露修复）。
 *
 * <h2>泄露机制（1.21.1 字节码实证）</h2>
 * {@code PlayerData.dataMap} 是 {@code private static Map<Integer,PlayerData>}，
 * 只在 {@code PlayerData.get(Player)} 里 computeIfAbsent 填充，
 * 全类以及 {@code PlayerDataController} 都<b>没有任何移除逻辑</b>
 * （玩家登出、世界卸载、服务端停止都不清）。而 {@code PlayerData} 持有
 * {@code public Player player}、{@code EntityNPCInterface editingNpc}、
 * {@code Entity mounted}、{@code activeCompanion} 等世界绑定引用 ——
 * 单人退出存档后整个旧玩家对象与 NPC 引用链被静态 map 永久钉住。
 *
 * <p>用静态 {@code @Accessor} 拿到 map，由
 * {@code bin.cnpcplus.leakfix.CnpcStaticsCleanup} 在 ServerStopped 时 clear。
 * 下个世界 {@code PlayerData.get()} 会按需重建，语义不变。
 * （静态字段配静态访问器是 Mixin 官方支持的写法，本工程
 * {@code MassBlockControllerQueueAccess} 已有成功先例。）
 */
@Mixin(value = PlayerData.class, remap = false)
public interface PlayerDataAdapter {

    @Accessor("dataMap")
    static Map<Integer, PlayerData> cnpcplus$dataMap() {
        throw new AssertionError("mixin");
    }
}
