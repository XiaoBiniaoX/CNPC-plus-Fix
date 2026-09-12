package bin.cnpcplus.mixin.melee;

import bin.cnpcplus.melee.MeleeBuffStore;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * 近战命中时施加 BUFF 列表（哈基彬需求 B-3 的执行端）。
 *
 * <h3>注入点</h3>
 * {@code EntityNPCInterface.func_70652_k}（attackEntityAsMob，反编译 463-496 行）
 * 的 RETURN。该方法返回 boolean，{@code var4 = target.attackEntityFrom(...)}，
 * **true 才代表伤害真实生效**（未被无敌帧、护甲判定或事件取消）。
 * 阶段 24 的近战打击台词也是用这个判据，保持一致：不命中不上 BUFF。
 *
 * 返回值方法的 handler 必须用 {@code CallbackInfoReturnable}
 * （findings 阶段 23 崩溃3 / 阶段 25 的教训）。
 *
 * <h3>与原版单效果字段的关系</h3>
 * 不动原版那套 {@code potionType/potionDuration/potionAmp}，也不动
 * {@code :489-494} 那段原有施加逻辑。理由：
 * <ul>
 *   <li>投射物（{@code EntityProjectile}）复用同名 NBT 键，需求没要求改远程，
 *       动数据层会牵连它；</li>
 *   <li>旧存档里配过单效果的 NPC 行为保持不变；</li>
 *   <li>界面上原控件已被隐藏，玩家不会再产生新的单效果配置，
 *       所以两套并存不会造成困惑。</li>
 * </ul>
 * 于是新 BUFF 列表是**叠加**在原有单效果之上，而非替换。
 *
 * <h3>为什么不判 EntityLivingBase 之前就返回</h3>
 * {@code attackEntityAsMob} 的参数是 {@code Entity}，可能是矿车、盔甲架之类
 * 非生物实体。原版 {@code :491} 直接强转 {@code (EntityLivingBase)} 是有
 * ClassCastException 风险的（只是恰好被 {@code getEffectType()==0} 挡住了），
 * 这里老实做 instanceof。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCInterfaceMeleeBuffs {

    @Inject(method = "func_70652_k", at = @At("RETURN"), remap = false, require = 1)
    private void cnpcplus$applyMeleeBuffs(Entity target, CallbackInfoReturnable<Boolean> cir) {
        // 只在伤害真实生效时施加，与近战打击台词同一判据。
        if (cir.getReturnValue() == null || !cir.getReturnValue().booleanValue()) return;
        if (!(target instanceof EntityLivingBase)) return;

        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (npc.stats == null || npc.stats.melee == null) return;

        Map<String, int[]> buffs = MeleeBuffStore.peek(npc.stats.melee);
        if (buffs == null || buffs.isEmpty()) return;

        EntityLivingBase victim = (EntityLivingBase) target;
        for (Map.Entry<String, int[]> entry : buffs.entrySet()) {
            int[] value = entry.getValue();
            if (value == null || value.length < 2) continue;
            // 注册名查不到 = 对应 mod 已被移除，跳过而不是崩。
            Potion potion = MeleeBuffStore.resolve(entry.getKey());
            if (potion == null) continue;
            // 秒 → ticks，与原版 :491 的 getEffectTime()*20 同一换算。
            victim.addPotionEffect(new PotionEffect(potion,
                    MeleeBuffStore.clampSeconds(value[1]) * 20,
                    MeleeBuffStore.clampAmplifier(value[0])));
        }
    }
}
