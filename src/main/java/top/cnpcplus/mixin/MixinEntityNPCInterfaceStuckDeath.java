package top.cnpcplus.mixin;

import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 脚本在受伤事件里回血时，NPC 会卡在 IsDead=true 但 health>0 的非法状态：
 * aiStep 已把 IsDead 置 true 并 updateTasks() 清了 AI，但回血后 health>0 →
 * isDeadOrDying() 为 false → tickDeath() 永不执行 → 不播死亡动画、不设 killedtime、不重生。
 *
 * 修法：把血量压回 0，让 CNPC 自己的死亡流程正常跑：
 * tickDeath() → remove(KILLED)（设 health=-1、死亡动画、killedtime=respawnTime）→ 计时到点 reset()。
 * 不能直接 reset()，那会跳过重生计时导致立即复活。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCInterfaceStuckDeath {

    @Inject(method = "m_8107_", at = @At("HEAD"))
    private void cnpcplus$recoverStuckDeath(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (npc.level().isClientSide) return;
        if (npc.isRemoved()) return;
        // isKilled() == isRemoved() || IsDead；上面已排除 isRemoved，这里即 IsDead 标记残留
        if (!npc.isKilled()) return;
        if (npc.getHealth() <= 0.0F) return;
        // 重新落回死亡态，交回 CNPC 原生 tickDeath / killedtime / reset 流程
        npc.setHealth(0.0F);
    }
}
