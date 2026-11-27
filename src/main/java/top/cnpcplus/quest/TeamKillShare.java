package top.cnpcplus.quest;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 「一次击杀对每个玩家只推进 1 点」的去重记录（哈基彬指定）。
 *
 * <h3>为什么需要</h3>
 * 加了队伍共享之后，一个玩家可能被**两条**分享路径同时命中：
 * <ul>
 *   <li>原版的区域击杀距离分享 —— {@code ServerEventsHandler:218-223}，
 *       条件是 {@code data.quest.type == 4 && all}，范围 {@code CustomNpcs.AreaKillRange}；</li>
 *   <li>本项目新增的计分板队伍分享 —— {@code MixinServerEventsHandlerTeamKill}。</li>
 * </ul>
 * 同队且恰好站在尸体附近的队友就会走到两次记账。原版 {@code :231} 的上限检查
 * （{@code killed >= target} 就跳过）虽然是幂等的，但队友若还差 2 个以上目标，
 * 一次死亡就会 +2 —— 语义不干净。
 *
 * <h3>作用域</h3>
 * 以「一次击杀事件」为作用域。{@code begin} 由击杀者本人那次
 * （{@code all == true}）调用重置，随后同一事件内的所有分享共用这一份记录。
 *
 * <h3>为什么用受害者实体 id 当键而不是每次新建集合</h3>
 * 分享是同步递归发生的，理论上一个集合就够。但 CNPC 的 {@code doQuest} 也被
 * {@code QuestKill$QuestKillObjective.setProgress} 与 {@code CmdQuest} 之类的路径
 * 间接触及，用受害者 id 作键可以避免不同来源互相踩到对方的记录。
 *
 * <p>ponytail: 单槽记录（只保留最近一次击杀事件），足够覆盖同步递归的分享场景。
 * 若将来出现真正并发的多个击杀事件同时记账，再换成按实体 id 分表的 Map。
 *
 * <h3>线程</h3>
 * 只在服务端主线程的 {@code LivingDeathEvent} 链上被访问（{@code doQuest} 全程同步），
 * 所以不需要加锁。刻意放在 mixin 包外：mixin 包内的类不能被外部直接引用。
 */
public final class TeamKillShare {

    /** 当前记账作用域对应的受害者实体 id。-1 表示没有活动作用域。 */
    private static int currentVictimId = -1;

    /** 本次击杀事件里已经记过账的玩家。 */
    private static final Set<UUID> HANDLED = new HashSet<>();

    private TeamKillShare() {
    }

    /** 开启一个新的记账作用域（由击杀者本人那次调用触发）。 */
    public static void begin(LivingEntity victim) {
        if (victim == null) return;
        currentVictimId = victim.getId();
        HANDLED.clear();
    }

    /** 登记某玩家在本次击杀事件里已经记过账。 */
    public static void markHandled(LivingEntity victim, Player player) {
        if (victim == null || player == null) return;
        if (victim.getId() != currentVictimId) return;
        HANDLED.add(player.getUUID());
    }

    /** 该玩家在本次击杀事件里是否已经记过账。 */
    public static boolean alreadyHandled(LivingEntity victim, Player player) {
        if (victim == null || player == null) return false;
        if (victim.getId() != currentVictimId) return false;
        return HANDLED.contains(player.getUUID());
    }
}
