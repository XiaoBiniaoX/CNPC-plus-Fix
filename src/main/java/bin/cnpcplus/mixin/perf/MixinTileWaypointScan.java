package bin.cnpcplus.mixin.perf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import noppes.npcs.blocks.tiles.TileWaypoint;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.PlayerQuestData;
import noppes.npcs.controllers.data.QuestData;
import noppes.npcs.quests.QuestLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 优化 #12：{@code TileWaypoint.tick} 每 10 tick 两次 AABB 查询 + 实体强引用泄漏。
 *
 * <h2>原版做了什么</h2>
 * {@code TileWaypoint.tick}（反编译源码 50-86 行）每 10 tick 跑一次：
 * <pre>
 * tile.toCheck = tile.getPlayerList(tile.range, tile.range, tile.range);        // AABB 查询 #1
 * tile.toCheck.removeAll(tile.recentlyChecked);                                 // O(n×m)
 * List&lt;Player&gt; listMax = tile.getPlayerList(tile.range + 10, ...);              // AABB 查询 #2
 * tile.recentlyChecked.retainAll(listMax);                                      // O(n×m)
 * tile.recentlyChecked.addAll(tile.toCheck);
 * if (tile.toCheck.isEmpty()) return;
 * for (Player player : tile.toCheck) { ... 检查任务完成 ... }
 * </pre>
 *
 * {@code getPlayerList}（88-90 行）每次都
 * {@code new AABB(...).inflate(x, y, z)} 再
 * {@code level.getEntitiesOfClass(Player.class, aabb)}。
 *
 * <h2>三个问题</h2>
 * <ol>
 *   <li><b>两次 AABB 查询，第二次完全包含第一次。</b>
 *       内圈（{@code range}）的结果必然是外圈（{@code range+10}）结果的子集。
 *       做一次外圈查询再按距离在内存里分区，完全等价。</li>
 *   <li><b>{@code removeAll} / {@code retainAll} 在 {@code ArrayList} 上是 O(n×m)。</b>
 *       两者内部都对每个元素做一次 {@code contains} 线性扫描。
 *       传送点建在主城这种玩家密集处时，这不是个小数字。</li>
 *   <li><b>{@code recentlyChecked} 持有 {@code Player} 实体的强引用。</b>
 *       玩家下线 / 换维度 / 实体对象被替换时，如果 {@code retainAll} 没能把它剔掉
 *       （新实体与旧实体 {@code equals} 对不上），这个引用会一直挂着 ——
 *       实体、背包、任务数据全都无法被 GC。这是一个真实的内存泄漏。</li>
 * </ol>
 *
 * <h2>本类怎么做</h2>
 * <ul>
 *   <li>只做<b>一次</b> AABB 查询（用 {@code range+10} 的大范围）。</li>
 *   <li>在内存里按碰撞箱相交判断分出内圈，判据与
 *       {@code getEntitiesOfClass} 用的完全一致（见 {@link #cnpcplus$inside}）。</li>
 *   <li>{@code recentlyChecked} 换成 {@code Set<UUID>}：
 *       查/删/保留从 O(n) 变 O(1)，且<b>不再持有实体强引用</b>，泄漏修掉。</li>
 * </ul>
 *
 * <h2>行为等价性</h2>
 * 原版的「首次进入内圈才触发」语义由两层集合维护：
 * <ul>
 *   <li>{@code recentlyChecked}：最近在外圈里见过的玩家</li>
 *   <li>{@code toCheck}：这次在内圈、但不在 {@code recentlyChecked} 里的玩家 ——
 *       也就是「刚从外圈走进内圈」的人</li>
 * </ul>
 * 换成 UUID Set 之后逐条对应：
 * <ul>
 *   <li>{@code recentlySeen ∩ outer} ≡ {@code recentlyChecked.retainAll(listMax)}</li>
 *   <li>「在内圈且 {@code add} 返回 true」≡ {@code toCheck.removeAll(recentlyChecked)}
 *       之后剩下的那些（{@code Set.add} 返回 false 表示已存在，等价于被 removeAll 剔掉）</li>
 *   <li>{@code add} 成功即同时完成了原版的 {@code recentlyChecked.addAll(toCheck)}</li>
 * </ul>
 * 任务完成检查的循环体一字不改（见 {@link #cnpcplus$tryComplete}）。
 *
 * <p>UUID 比实体引用更稳：玩家重登后 UUID 不变。
 * 原版用实体引用会把重登当成「新人」再触发一次（新实体 {@code equals} 不上旧实体）——
 * 那其实是原版的一个边角 bug。用 UUID 顺带修掉它：
 * 重登后若玩家仍在范围内，不会重复触发。这是<b>更正确</b>的行为，不是行为变更。
 *
 * <h2>为什么不改 {@code recentlyChecked} 字段本身的类型</h2>
 * 它是 {@code private List<Player>}，只被 {@code tick} 内部使用，且不参与 NBT 序列化
 * （重启后自然清空）。所以干脆<b>不用原字段</b>，
 * 用一个 {@code @Unique Set<UUID>} 完全接管 ——
 * 原字段会一直是空列表，除了被我们 {@code cancel()} 掉的原方法体没人读它。
 *
 * <h2>1.21.1 适配说明</h2>
 * 1.21.1 原版 {@code getPlayerList} 的字节码构造 AABB 的方式已变为
 * {@code new AABB(pos.getCenter(), pos.offset(1,1,1).getCenter())}
 * （中心点连成的盒子），而 <b>1.20.1 反编译源码是 {@code new AABB(pos, pos.offset(1,1,1))}</b>。
 * 本类按 1.21.1 的 getCenter 方式构造内/外圈盒子（{@link #cnpcplus$boxes}），
 * 以与<b>当前版本原版</b>的触发边界一致，而不是与 1.20.1 注释一致。
 * <b>这是本次移植相对 1.20.1 源码的必要适配。</b>
 *
 * <p><b>1.21.1 已核签名一致</b>（javap）：
 * {@code public static tick(Level, BlockPos, BlockState, TileWaypoint)}、
 * {@code public String name}、{@code public int range}、
 * {@code private int ticks}、{@code private List&lt;Player&gt; recentlyChecked/toCheck}、
 * {@code private List&lt;Player&gt; getPlayerList(int,int,int)} 全部存在；
 * tick 方法体字节码仍按「ticks-- → 归零时设 10 → 两次 getPlayerList →
 * removeAll/retainAll/addAll → toCheck 非空时遍历触发任务检查」的次序执行。
 */
@Mixin(value = TileWaypoint.class, remap = false)
public abstract class MixinTileWaypointScan {

    @Shadow public String name;
    @Shadow public int range;
    @Shadow private int ticks;

    /**
     * 最近在外圈见过的玩家 UUID。取代原版的 {@code List<Player> recentlyChecked}。
     *
     * <p><b>刻意不用字段初始化器</b>（{@code = new HashSet<>()}）：
     * Mixin 实现实例字段初始化器的方式是把初始化代码合并进目标类的构造函数，
     * 这在目标有多个构造函数、或构造函数已被其他 mixin 改动时容易出问题。
     * 改成懒初始化（{@link #cnpcplus$recent()}）后完全不依赖构造函数注入。
     */
    @Unique private Set<UUID> cnpcplus$recent;

    @Unique
    private Set<UUID> cnpcplus$recent() {
        Set<UUID> set = this.cnpcplus$recent;
        if (set == null) {
            set = new HashSet<>();
            this.cnpcplus$recent = set;
        }
        return set;
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private static void cnpcplus$singleQuery(Level level, BlockPos pos, BlockState state,
                                             TileWaypoint tile, CallbackInfo ci) {
        // 与原版 51-54 行一致。
        if (level.isClientSide || tile.name.isEmpty()) {
            ci.cancel();
            return;
        }

        MixinTileWaypointScan self = (MixinTileWaypointScan) (Object) tile;

        // 与原版 55-59 行一致：每 10 tick 跑一次。
        self.ticks--;
        if (self.ticks > 0) {
            ci.cancel();
            return;
        }
        self.ticks = 10;

        int range = self.range;
        // 1.21.1 原版 getPlayerList 的 AABB 构造方式（javap 字节码）：
        //   new AABB(pos.getCenter(), pos.offset(1,1,1).getCenter()).inflate(r, r, r)
        // 1.20.1 反编译源码用的是 new AABB(pos, pos.offset(1,1,1))，边界不同。
        // 这里按 1.21.1 对齐，见类注释「1.21.1 适配说明」。
        AABB base = new AABB(pos.getCenter(), pos.offset(1, 1, 1).getCenter());
        AABB innerBox = base.inflate(range, range, range);
        AABB outerBox = base.inflate(range + 10, range + 10, range + 10);

        Set<UUID> recent = self.cnpcplus$recent();

        // 一次查询拿外圈全部玩家。内圈是外圈的子集，在内存里过滤即可。
        List<Player> outer = level.getEntitiesOfClass(Player.class, outerBox);
        if (outer.isEmpty()) {
            recent.clear();
            ci.cancel();
            return;
        }

        Set<UUID> stillHere = new HashSet<>(outer.size() * 2);
        for (int i = 0; i < outer.size(); i++) {
            stillHere.add(outer.get(i).getUUID());
        }
        // 等价于原版 recentlyChecked.retainAll(listMax)：只留还在外圈的。
        recent.retainAll(stillHere);

        for (int i = 0; i < outer.size(); i++) {
            Player player = outer.get(i);
            // 不在内圈 → 只是在外圈徘徊，不触发。
            if (!cnpcplus$inside(player, innerBox)) {
                continue;
            }
            // add 返回 false 说明已在 recentlySeen 里，不是「刚走进来」的。
            // 这一句同时完成了原版的 removeAll(recentlyChecked) 与 addAll(toCheck)。
            if (!recent.add(player.getUUID())) {
                continue;
            }
            cnpcplus$tryComplete(player, tile);
        }
        ci.cancel();
    }

    /**
     * 玩家是否在膨胀后的 AABB 内。
     *
     * <p>{@code getEntitiesOfClass} 用的判据是实体<b>碰撞箱</b>与 AABB 相交，
     * 不是实体脚点在 AABB 内。所以内圈过滤必须用同一个判据，
     * 否则会出现「原版能触发、本类不能」或反过来的边界偏差。
     * {@code AABB.intersects(AABB)} 正是 vanilla 实体查询内部用的那个方法。
     */
    @Unique
    private static boolean cnpcplus$inside(Player player, AABB box) {
        return player.getBoundingBox().intersects(box);
    }

    /**
     * 任务完成检查。与原版 70-84 行的循环体逐语句对应。
     *
     * <p>原版用了带标签的 continue（CFR 反编译成 {@code block0: while}），
     * 语义是「这个玩家的全部活跃任务都过一遍」。这里用普通 while 表达同样的意思。
     */
    @Unique
    private static void cnpcplus$tryComplete(Player player, TileWaypoint tile) {
        PlayerData pdata = PlayerData.get(player);
        PlayerQuestData playerdata = pdata.questData;
        Iterator<QuestData> iterator = playerdata.activeQuests.values().iterator();
        while (iterator.hasNext()) {
            QuestData data = iterator.next();
            if (data.quest.type != 3) {
                continue;
            }
            QuestLocation quest = (QuestLocation) data.quest.questInterface;
            if (!quest.setFound(data, tile.name)) {
                continue;
            }
            player.sendSystemMessage(Component.translatable(tile.name)
                    .append(" ")
                    .append(Component.translatable("quest.found")));
            playerdata.checkQuestCompletion(player, 3);
            pdata.updateClient = true;
        }
    }
}
