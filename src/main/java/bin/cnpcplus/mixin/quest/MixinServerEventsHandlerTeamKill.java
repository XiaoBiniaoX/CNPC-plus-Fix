package bin.cnpcplus.mixin.quest;

import bin.cnpcplus.quest.TeamKillShare;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.ServerEventsHandler;
import org.spongepowered.asm.mixin.Mixin;
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
public class MixinServerEventsHandlerTeamKill {

    @Inject(method = "doQuest", at = @At("HEAD"), require = 1)
    private void cnpcplus$shareToTeam(Player player, LivingEntity entity, boolean all, CallbackInfo ci) {
        // all == false 是 AREA_KILL 分支的递归调用（:202）。那里已经在给周围玩家逐个记账，
        // 若在递归里再扩散一次，就变成「范围内 N 人 × 队伍 M 人」的叠乘重复计数。
        if (!all) return;
        if (player == null || entity == null) return;
        if (player.level() == null || player.level().isClientSide) return;

        String entityName;
        if (entity instanceof Player) {
            entityName = "Player";
        } else {
            var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (key == null) return;
            entityName = key.toString();
        }

        TeamKillShare.share(player, entity, entityName);
    }
}
