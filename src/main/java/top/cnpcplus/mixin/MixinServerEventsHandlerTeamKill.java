package top.cnpcplus.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;
import noppes.npcs.ServerEventsHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.config.CnpcPlusServerConfig;
import top.cnpcplus.config.ServerConfigAccess;
import top.cnpcplus.quest.TeamKillShare;

import java.util.List;

/**
 * 击杀任务在原版计分板队伍（{@code /team}）内共享进度（哈基彬需求 A1）。
 *
 * <h3>需求</h3>
 * 「如果任务接取者用指令为自己添加了 team，并且其他玩家也在 team 内，
 * 则玩家的击杀任务要求在队伍内同步。假如 A B C 三人在 team 内，A 有一个任务要求击杀 aNPC，
 * 而 B、C 没有此任务，但 B、C 击杀了 aNPC，则 A 的任务一样可以完成。」
 *
 * <h3>原版为什么不够（反编译实证）</h3>
 * {@code ServerEventsHandler.doQuest}（{@code :202-240}）是击杀记入任务进度的**唯一**位置。
 * 它只在 {@code data.quest.type == 4}（区域击杀）时做分享（{@code :218-223}），
 * 范围是 {@code entity.getBoundingBox().inflate(CustomNpcs.AreaKillRange, ...)}，
 * 只看距离、完全不看计分板队伍。普通击杀（type 2，也就是哈基彬举例的那种）
 * 没有任何分享机制。
 *
 * <h3>做法</h3>
 * 在 HEAD 处，当这是「击杀者本人的那一次调用」（{@code all == true}）时，
 * 找出击杀者所在计分板队伍的全部**在线**成员，各自再走一遍
 * {@code doQuest(teammate, victim, false)}。
 *
 * <p>复用原版记账而不是自己复刻：{@code :225-238} 那段包含目标名匹配
 * （显示名 / 实体类型 id 双通道）、上限判断、{@code extraData} 读写与
 * {@code updateClient} 标记，末尾 {@code :212-213} 还有两次
 * {@code checkQuestCompletion}。复刻一遍等于多一份会和上游脱节的代码。
 * 而且 {@code QuestKill.isCompleted}（{@code :56}）要求
 * {@code killed.size() == targets.size()}，进度**必须真实写进队友的 killed 表**，
 * 不能只在完成判定处旁路检查 —— 走原版记账天然满足这一点。
 *
 * <h3>客户端同步是白拿的</h3>
 * {@code doQuest} 末尾会置 {@code pdata.updateClient = true}（{@code :238}），
 * 而 {@code ServerTickHandler:83-87} 在每个玩家自己的 tick 事件里消费这个标记并发
 * {@code PacketSync(8, ...)}。所以给队友记账后**不需要新增任何网络包**，
 * 下一 tick 他那边就刷新了。
 *
 * <h3>为什么传 all = false</h3>
 * 这是原版自己的递归哨兵（{@code :218} 的 {@code type == 4 && all}）。
 * 传 false 使队友不会再次触发分享，既不会递归爆炸，也保持原版 type 4 的距离分享逻辑不变。
 *
 * <h3>去重（哈基彬指定）</h3>
 * type 4 任务会被原版距离分享与本处队伍分享**各命中一次**。虽然 {@code :231} 的
 * 上限检查是幂等的，但队友若差 2 个以上目标，一次死亡就会 +2。
 * 所以用 {@link TeamKillShare} 记录本次死亡已处理过的玩家，同一队友一次死亡只推进 1 点。
 *
 * <h3>不判距离、不判维度</h3>
 * 需求说的是「在 team 内同步」，加距离限制就变成了另一个功能。
 *
 * <h3>只覆盖在线玩家</h3>
 * {@code PlayerDataController.getDataFromUsername} 对离线玩家返回的是**临时游离对象**
 * （{@code :95-96} 的 {@code new PlayerData()} + {@code loadPlayerData}），
 * {@code data.player == null}，改它必须手动 {@code save(false)}；
 * 而该玩家随后登录时 {@code PlayerData.get} 会从磁盘重读（{@code :272-273}），
 * 存盘顺序错了就丢进度。所以只处理在线玩家，config 注释里已写明。
 */
@Mixin(value = ServerEventsHandler.class, remap = false)
public abstract class MixinServerEventsHandlerTeamKill {

    /** 访问原版私有的 {@code doQuest}，用来替队友记账。 */
    @Invoker("doQuest")
    protected abstract void cnpcplus$doQuest(Player player, LivingEntity entity, boolean all);

    /**
     * 注意注入点顺序：本 handler 在 HEAD，而原版的距离分享在方法体内的 {@code :218-223}，
     * 也就是**晚于**本处。所以去重不能靠「队伍分享时检查距离分享的记录」，
     * 顺序上根本还没发生。
     *
     * <p>正确做法是反过来：由每一次派生调用（{@code all == false}，无论来自队伍分享
     * 还是原版距离分享）在自己的 HEAD 处判重，重复的直接 {@code ci.cancel()}。
     * 队伍分享先跑并登记，随后原版距离分享若命中同一个人就会在这里被拦掉。
     */
    @Inject(method = "doQuest", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$shareKillWithTeam(Player player, LivingEntity entity,
                                            boolean all, CallbackInfo ci) {
        if (player == null || entity == null) return;
        if (player.level() == null || player.level().isClientSide()) return;

        if (!all) {
            // 派生调用：同一次击杀里已经给这个玩家记过账就取消，保证只推进 1 点。
            if (TeamKillShare.alreadyHandled(entity, player)) {
                ci.cancel();
                return;
            }
            TeamKillShare.markHandled(entity, player);
            return;
        }

        // 击杀者本人这一次：开一个新的去重作用域，并把自己登记进去。
        TeamKillShare.begin(entity);
        TeamKillShare.markHandled(entity, player);

        if (!ServerConfigAccess.bool(CnpcPlusServerConfig.TeamShareKillQuest, false)) return;

        Team team = player.getTeam();
        if (team == null) return;

        MinecraftServer server = player.getServer();
        if (server == null || server.getPlayerList() == null) return;

        List<ServerPlayer> online = server.getPlayerList().getPlayers();
        if (online == null) return;

        for (ServerPlayer mate : online) {
            if (mate == null || mate == player) continue;
            // 同一支队伍才算队友。用 Team 对象比较，避免依赖队伍名字符串。
            if (mate.getTeam() != team) continue;
            cnpcplus$doQuest(mate, entity, false);
        }
    }
}
