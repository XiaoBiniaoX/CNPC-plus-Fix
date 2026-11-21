package top.cnpcplus.mixin;

import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import top.cnpcplus.melee.MeleeFloatValues;

/**
 * 让近战伤害的小数值真正参与战斗结算。
 *
 * <p>背景（字节码实证 EntityNPCInterface.m_7327_ 偏移 0-11）：原版第一件事是
 * {@code float f = this.stats.melee.getStrength();} —— 读的是 int 字段再 i2f。
 * 小数不能只写 NBT；战斗也需要读取同一个覆盖值。
 *
 * <p>做法：@ModifyVariable 改写该局部变量（index 2），把取整值换成小数覆盖值。
 * 选在 STORE 之后改写而不是重写整个方法，理由：
 * 1. 原版随后会把 f 交给 MeleeAttackEvent（脚本可再改），我们的改写发生在事件之前，
 *    脚本拿到的就是真实小数，脚本改回来也照样生效，不打断既有扩展点。
 * 2. 不用 @Overwrite，与其他改近战的模组可共存。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCMeleeFloatDamage {

    @Shadow(remap = false)
    public DataStats stats;

    @ModifyVariable(method = "m_7327_", at = @At("STORE"), ordinal = 0, remap = false)
    private float cnpcplus$usePreciseStrength(float vanilla, Entity target) {
        return ((MeleeFloatValues) this.stats.melee).cnpcplus$getStrengthFloat();
    }
}
