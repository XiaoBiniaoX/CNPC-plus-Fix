package top.cnpcplus.mixin;

import net.minecraft.world.entity.Mob;
import noppes.npcs.ai.target.NpcNearestAttackableTargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 240d 索敌复发修复：CombatHandler.reset() 只调 mob.setTarget(null)（不走 goal.stop()），
 * NpcNearestAttackableTargetGoal 内部 f_26137_ 仍是旧目标 → m_8045_（canContinueToUse）
 * 用该字段兜底返回 true → GoalSelector 认为 goal 仍在运行 → canUse（m_8036_）永不再调 →
 * Mob.getTarget() 恒 null，NPC 不追。修复：m_8045_ HEAD 处若 mob.getTarget()==null
 * 直接 cancel 返回 false → GoalSelector 停止 goal → 下 tick canUse 重新扫描。
 * 仅影响 NpcNearestAttackableTargetGoal；正常运行时 getTarget() 非 null，不受影响。
 */
@Mixin(value = NpcNearestAttackableTargetGoal.class, remap = false)
public abstract class MixinNpcNearestTargetStuck {

    @Inject(method = "m_8045_", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$forceRescanIfNoTarget(CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof TargetGoalAccessor accessor)) return;
        Mob mob = accessor.customnpcsaioptimizer$getMob();
        if (mob != null && mob.getTarget() == null) {
            cir.setReturnValue(false);
        }
    }
}
