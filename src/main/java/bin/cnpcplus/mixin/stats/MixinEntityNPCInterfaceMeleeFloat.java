package bin.cnpcplus.mixin.stats;

import bin.cnpcplus.accessor.MeleeFloatAccess;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 原版 doHurtTarget 先从 DataMelee 读取整数伤害，再用 i2f 存入局部浮点变量。
 * 不能重定向 int 方法为 float；这里在局部变量写入处替换为 CNPCPlus 的浮点伤害。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCInterfaceMeleeFloat {

    @ModifyVariable(method = "doHurtTarget", at = @At("STORE"), ordinal = 0, require = 1)
    private float cnpcplus$useMeleeStrengthFloat(float originalStrength) {
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        // 保留原版事件、伤害源和命中流程，只替换进入结算的初始伤害精度。
        return ((MeleeFloatAccess) self.stats.melee).cnpcplus$getStrength();
    }
}
