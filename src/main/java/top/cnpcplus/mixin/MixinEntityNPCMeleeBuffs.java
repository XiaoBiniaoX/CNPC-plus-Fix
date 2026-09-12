package top.cnpcplus.mixin;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.melee.MeleeBuffStore;

import java.util.Map;

/**
 * 近战命中时施加 BUFF 列表（需求 A3 的执行端）。
 *
 * <h3>注入点</h3>
 * {@code EntityNPCInterface.m_7327_}（doHurtTarget，反编译 536-569 行）的 RETURN。
 * 该方法返回 boolean，值来自 {@code :550} 的 {@code par1Entity.m_6469_(...)}，
 * **true 才代表伤害真实生效**（未被无敌帧、护甲判定或事件取消）。
 * 与本项目已有的「近战打击台词」用同一判据，保持一致：不命中不上 BUFF。
 *
 * <p>返回值方法的 handler 必须用 {@code CallbackInfoReturnable}。
 *
 * <h3>与原版单效果字段的关系：叠加，不替换</h3>
 * 不动原版那套 {@code potionType/potionDuration/potionAmp}，也不动
 * {@code :562-567} 那段原有施加逻辑。理由：
 * <ul>
 *   <li>投射物 {@code EntityProjectile:681-683} 复用同名 NBT 键，
 *       需求没要求改远程，动数据层会牵连它；</li>
 *   <li>旧存档里配过单效果的 NPC 行为保持不变；</li>
 *   <li>界面上原控件已被隐藏，玩家不会再产生新的单效果配置，
 *       所以两套并存不会造成困惑。</li>
 * </ul>
 *
 * <h3>为什么老实做 instanceof</h3>
 * {@code m_7327_} 的参数是 {@code Entity}，可能是矿车、盔甲架之类非生物实体。
 * 原版 {@code :564} 直接强转 {@code (LivingEntity)} 是有 ClassCastException 风险的
 * （只是恰好被前一行 {@code getEffectType() == 0} 挡住了）。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCMeleeBuffs {

    @Inject(method = "m_7327_", at = @At("RETURN"), remap = false)
    private void cnpcplus$applyMeleeBuffs(Entity target, CallbackInfoReturnable<Boolean> cir) {
        // 只在伤害真实生效时施加。
        Boolean hit = cir.getReturnValue();
        if (hit == null || !hit) return;
        if (!(target instanceof LivingEntity victim)) return;

        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (npc.stats == null || npc.stats.melee == null) return;

        Map<String, int[]> buffs = MeleeBuffStore.peek(npc.stats.melee);
        if (buffs == null || buffs.isEmpty()) return;

        for (Map.Entry<String, int[]> entry : buffs.entrySet()) {
            int[] value = entry.getValue();
            if (value == null || value.length < 2) continue;
            // 注册名查不到 = 对应 mod 已被移除，跳过而不是崩。
            MobEffect effect = MeleeBuffStore.resolve(entry.getKey());
            if (effect == null) continue;
            // 秒 → ticks，与原版 :564 的 getEffectTime()*20 同一换算。
            victim.addEffect(new MobEffectInstance(effect,
                    MeleeBuffStore.clampSeconds(value[1]) * 20,
                    MeleeBuffStore.clampAmplifier(value[0])));
        }
    }
}
