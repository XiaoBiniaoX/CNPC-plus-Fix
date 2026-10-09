package bin.cnpcplus.leakfix;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.mixin.leakfix.MarkDataAccessor;
import bin.cnpcplus.mixin.leakfix.PlayerDataAdapter;
import noppes.npcs.api.wrapper.WrapperNpcAPI;
import noppes.npcs.api.wrapper.WorldWrapper;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataScenes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Map;

/**
 * CNPC 1.21.1 静态滞留清理（内存泄露修复）。
 *
 * <h2>根因（javap 实证，均为 CNPC 本体缺陷）</h2>
 * CNPC 的 {@code CustomNpcs.stopped(ServerStoppedEvent)} 只做了两件事：
 * {@code ServerCloneController.Instance = null} 与 {@code Server = null}，
 * 下面这些「按会话本应重置」的静态状态<b>全部无人清理</b>：
 * <ol>
 *   <li>{@code EntityNPCInterface.ChatEventPlayer/CommandPlayer/GenericPlayer} ——
 *       {@code ServerStartingEvent} 时 new 的三个 FakePlayer，各持一个
 *       {@code ServerLevel} 引用。单人退出存档后客户端 JVM 仍存活，
 *       整个旧世界对象图（区块/实体/方块实体）被钉住不释放。</li>
 *   <li>{@code WrapperNpcAPI.worldCache} ——
 *       {@code Map<DimensionType,WorldWrapper>}，{@code WorldWrapper.level}
 *       持 {@code ServerLevel}。CNPC 自己在下一次 {@code ServerAboutToStart}
 *       才 clearCache，导致「退回主菜单到再进世界」期间旧世界一直被引用。</li>
 *   <li>{@code WorldWrapper.tempData} ——
 *       脚本用的静态临时表，可放入任意对象（含实体），跨世界不清。</li>
 *   <li>{@code DataScenes.ScenesToRun / StartedScenes} ——
 *       {@code SceneContainer} 经内部类引用链
 *       （SceneContainer→DataScenes→npc/owner）钉住
 *       {@code EntityNPCInterface} 与 {@code LivingEntity}。</li>
 *   <li>{@code PlayerData.dataMap} ——
 *       持 {@code Player}/{@code editingNpc}/{@code mounted}/{@code activeCompanion}，
 *       无任何移除逻辑（见 {@code PlayerDataAdapter}）。</li>
 *   <li>{@code MarkData.dataMap} ——
 *       持 {@code LivingEntity}，同上（见 {@code MarkDataAccessor}）。</li>
 * </ol>
 *
 * <h2>为什么清空是安全的</h2>
 * 这些都是「运行期按需重建」的缓存/会话状态：FakePlayer 在下次
 * ServerStarting 重建；worldCache 在 getIWorld 时重建；tempData/场景状态
 * 属于当前世界会话；dataMap 在 {@code get(Player/LivingEntity)} 时重建。
 * 清空只丢弃「上一个世界」的滞留引用，不影响任何持久化数据
 * （磁盘上的 playerdata JSON / NPC 存档一概不碰）。
 *
 * <p>时机选 {@code ServerStoppedEvent}：单人退出存档、局域网关闭、
 * 专用服停服都会触发；专用服随后 JVM 退出（清理只是顺手），
 * 单人客户端 JVM 继续存活 —— 这正是泄露生效的场景。
 */
@EventBusSubscriber(modid = CnpcPlus.MODID)
public final class CnpcStaticsCleanup {

    private CnpcStaticsCleanup() {
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        // 1. FakePlayer 持 ServerLevel；下次 ServerStartingEvent 会重建。
        EntityNPCInterface.ChatEventPlayer = null;
        EntityNPCInterface.CommandPlayer = null;
        EntityNPCInterface.GenericPlayer = null;

        // 2. 立即释放 worldCache（CNPC 原本要等下次世界启动才清）。
        WrapperNpcAPI.clearCache();

        // 3. 脚本静态临时表（public static，类未加载时会触发初始化，无害）。
        if (WorldWrapper.tempData != null) {
            WorldWrapper.tempData.clear();
        }

        // 4. 场景运行时状态（静态，钉住 NPC/实体引用链）。
        if (DataScenes.ScenesToRun != null) {
            DataScenes.ScenesToRun.clear();
        }
        if (DataScenes.StartedScenes != null) {
            DataScenes.StartedScenes.clear();
        }

        // 5. 玩家/标记数据静态表（经 Mixin 静态访问器拿到私有 map）。
        Map<?, ?> playerData = PlayerDataAdapter.cnpcplus$dataMap();
        if (playerData != null) {
            playerData.clear();
        }
        Map<?, ?> markData = MarkDataAccessor.cnpcplus$dataMap();
        if (markData != null) {
            markData.clear();
        }
    }
}
