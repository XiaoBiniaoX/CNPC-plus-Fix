package bin.cnpcplus.mixin.quest;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import top.cnpcplus.quest.TeamKillShare;
import top.cnpcplus.quest.QuestAreaKillContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.ServerEventsHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 队伍内击杀任务进度同步的挂钩点。
 *
 * <p>挂在 {@code ServerEventsHandler.doQuest} 的 HEAD 而不是自己订阅 LivingDeathEvent，原因有二：
 * <ul>
 *   <li>doQuest 之前 CNPC 已经做完了伤害源解包与归属判定（弹射物 owner、NPC owner，
 *       见 ServerEventsHandler:150-165），自己订阅事件就得把那一整套重写一遍；</li>
 *   <li>两个独立监听器的相对顺序不受控，而 CNPC 在 doQuest 末尾才做完成检查
 *       （:192-193）。注入 HEAD 能保证队友进度在同一次死亡内就被算进去。</li>
 * </ul>
 *
 * <p>自定义 NPC 与原版生物走的是同一条 {@code LivingDeathEvent → doQuest} 路径
 * （EntityNPCInterface.die 末尾调 super.die），所以一个挂钩点即可覆盖两者。
 */
@Mixin(value = ServerEventsHandler.class, remap = false)
public abstract class MixinServerEventsHandlerTeamKill {

    @Invoker("doQuest")
    protected abstract void cnpcplus$doQuest(Player player, LivingEntity entity, boolean all);

    @WrapMethod(method = "doQuest")
    private void cnpcplus$shareToTeam(Player player, LivingEntity entity, boolean all, Operation<Void> original) {
        if (!all && TeamKillShare.alreadyHandled(entity, player)) return;
        if (all) TeamKillShare.begin(entity);
        TeamKillShare.markHandled(entity, player);
        QuestAreaKillContext.push(entity);
        try {
            if (all && !player.level().isClientSide && player.getTeam() != null) {
                for (var mate : player.getServer().getPlayerList().getPlayers()) {
                    if (mate != player && mate.getTeam() == player.getTeam()) cnpcplus$doQuest(mate, entity, false);
                }
            }
            original.call(player, entity, all);
        } finally {
            QuestAreaKillContext.pop();
        }
    }
}
