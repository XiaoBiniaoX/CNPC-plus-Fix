package bin.cnpcplus.mixin.quest;

import noppes.npcs.ServerEventsHandler;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = ServerEventsHandler.class, remap = false)
public abstract class MixinServerEventsHandler {

    @Inject(method = "pickUp", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void cnpcplus$deferQuestCheck(EntityItemPickupEvent event, CallbackInfo ci) {
        ci.cancel();
    }

    /**
     * 访问原版私有的 {@code doQuest}，用来替队友记账。
     *
     * 用 {@code @Invoker} 而不是自己复刻记账逻辑：原版那段
     * （{@code ServerEventsHandler.java:247-260}）包含目标名匹配、
     * 上限判断、{@code extraData} 读写与 {@code updateClient} 标记，
     * 复刻一遍等于多一份会和上游脱节的代码。
     */
    @Invoker("doQuest")
    protected abstract void cnpcplus$doQuest(EntityPlayer player, EntityLivingBase entity, boolean all);

    /**
     * 队伍内共享击杀任务进度。
     *
     * <h3>需求</h3>
     * 哈基彬：任务接取者用指令给自己加了 team，队伍里其他玩家的击杀也要能推进
     * 接取者的击杀任务（A 有击杀任务，B、C 没有，但 B、C 的击杀同样能让 A 完成）。
     *
     * <h3>原版为什么不够</h3>
     * {@code doQuest}（{@code ServerEventsHandler.java:224-262}）是击杀记入任务进度的
     * 唯一位置，它只在 {@code data.quest.type == 4}（区域击杀）时做分享，而且
     * 分享范围是 {@code entity.getEntityBoundingBox().grow(10,10,10)} 这个
     * **硬编码 10 格**，完全不看计分板队伍。普通击杀（type 2，也就是哈基彬举例的那种）
     * 根本没有任何分享。
     *
     * <h3>做法</h3>
     * 在 HEAD 处，当这是「击杀者本人的那一次调用」（{@code all == true}）时，
     * 找出击杀者所在计分板队伍的全部在线成员，各自再走一遍
     * {@code doQuest(teammate, victim, false)}。
     *
     * 几个刻意的取舍：
     * <ul>
     *   <li><b>不判距离、不判维度。</b>需求说的是「在 team 内同步」，
     *       加距离限制就变成了另一个功能。</li>
     *   <li><b>传 {@code all = false}</b>，队友不会再次触发分享，
     *       因此不存在递归爆炸；同时也保持原版 type 4 的 10 格分享逻辑不变。</li>
     *   <li><b>复用原版记账</b>（见 {@code cnpcplus$doQuest} 的说明），
     *       {@code checkQuestCompletion} 与客户端同步都由它自己完成。</li>
     *   <li><b>没有队伍就直接返回</b>，`getTeam()` 是一次字段读取，
     *       每次生物死亡的额外开销可以忽略。</li>
     * </ul>
     *
     * 注意 {@code doQuest} 内部对每个玩家只推进「该玩家自己 activeQuests 里的」任务，
     * 所以给没有该任务的队友调用它是安全的空操作。
     */
    @Inject(method = "doQuest", at = @At("HEAD"), require = 1, remap = false)
    private void cnpcplus$shareKillWithTeam(EntityPlayer player, EntityLivingBase entity,
                                            boolean all, CallbackInfo ci) {
        if (!all || player == null || entity == null) return;
        if (player.world == null || player.world.isRemote) return;

        Team team = player.getTeam();
        if (team == null) return;

        MinecraftServer server = player.world.getMinecraftServer();
        if (server == null || server.getPlayerList() == null) return;

        List<EntityPlayerMP> online = server.getPlayerList().getPlayers();
        if (online == null) return;

        for (EntityPlayerMP mate : online) {
            if (mate == null || mate == player) continue;
            // 同一支队伍才算队友。用 Team 对象比较，避免依赖队伍名字符串。
            if (mate.getTeam() != team) continue;
            this.cnpcplus$doQuest(mate, entity, false);
        }
    }
}
