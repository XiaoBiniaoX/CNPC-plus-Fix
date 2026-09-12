package bin.cnpcplus.mixin.melee;

import bin.cnpcplus.melee.MeleeBuff;
import bin.cnpcplus.melee.MeleeBuffUtil;
import bin.cnpcplus.melee.MeleeBuffsAccess;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 近战命中后施加多个附加 BUFF。
 *
 * <p>原版只在 {@code doHurtTarget} 末尾（反编译 :535-540）施加单个效果。
 * 这里在 RETURN 处施加 BUFF 表里的条目。
 *
 * <p>不与原版那段重复：新界面在打开时会把原版的单效果字段清成 0（见
 * MixinSubGuiNpcMeleePropertiesBuffs），此时原版那段的 {@code getEffectType() == 0}
 * 直接短路返回，只有我们这里施加效果。反过来，玩家如果只用脚本 API 设了旧的单效果、
 * 没碰新界面，BUFF 表为空，这里什么都不做，原版行为保持原样。
 * 旧存档迁移那一刻两者会同时存在，故带一次「与原版单效果等价则跳过」的去重。
 *
 * <p>与原版一致：不论伤害是否真正命中都施加（原版那段就在 {@code if (var4)} 块之外），
 * 保持既有行为不动。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCInterfaceMeleeBuffs {

    @Inject(method = "doHurtTarget", at = @At("RETURN"), require = 1)
    private void cnpcplus$applyBuffs(Entity target, CallbackInfoReturnable<Boolean> cir) {
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        if (self.level() == null || self.level().isClientSide) return;
        if (target == null) return;
        if (self.stats == null || self.stats.melee == null) return;

        List<MeleeBuff> buffs = ((MeleeBuffsAccess) self.stats.melee).cnpcplus$getMeleeBuffs();
        if (buffs == null || buffs.isEmpty()) return;

        // 旧存档迁移场景：表里那一条就是原版单效果的副本，而原版那段已经施加过一次。
        // 跳过与原版当前单效果等价的第一条，避免同一效果被施加两遍。
        // 玩家一旦打开新界面，原版字段会被清零，此处就不再跳过任何条目。
        int vanillaType = self.stats.melee.getEffectType();
        String vanillaName = vanillaType == 0 ? null : MeleeBuffUtil.nameOfNumeric(vanillaType);
        boolean skipDuplicate = vanillaName != null;

        for (MeleeBuff buff : buffs) {
            if (buff == null || buff.effectId == null) continue;
            if (skipDuplicate
                    && buff.effectId.equals(vanillaName)
                    && buff.amp == self.stats.melee.getEffectStrength()
                    && buff.seconds == self.stats.melee.getEffectTime()) {
                skipDuplicate = false;
                continue;
            }
            if (MeleeBuff.FIRE.equals(buff.effectId)) {
                target.setRemainingFireTicks(buff.seconds * 20);
                continue;
            }
            if (!(target instanceof LivingEntity living)) continue;
            Holder<MobEffect> holder = MeleeBuffUtil.holderOf(buff.effectId);
            // 对应 mod 已被移除时 holder 为 null，跳过而不是崩掉。
            if (holder == null) continue;
            living.addEffect(new MobEffectInstance(holder, buff.seconds * 20, buff.amp));
        }
    }
}
