package top.cnpcplus.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.HitResult;
import noppes.npcs.entity.EntityProjectile;
import noppes.npcs.entity.data.DataRanged;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.drop.fx.DropFxConfig;
import top.cnpcplus.drop.fx.runtime.ProjectileFxTracker;
import bin.cnpcplus.melee.MeleeBuff;
import bin.cnpcplus.melee.MeleeBuffUtil;
import top.cnpcplus.ranged.RangedBuffStore;
import top.cnpcplus.ranged.RangedFxStore;
import top.cnpcplus.ranged.ProjectileExplosionBuffs;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 弹射物发射快照 + 命中施加多 BUFF（234 执行端）。
 *
 * <h3>发射快照（getStatProperties RETURN）</h3>
 * {@code EntityProjectile} 构造（反编译 :159，<b>全项目唯一调用点</b>）里
 * {@code getStatProperties(npc.stats.ranged)} 把 NPC 远程配置拷进实体——
 * 在其 RETURN 把 BUFF 表与特效表<b>深拷贝成实体侧快照</b>：
 * <ul>
 *   <li>语义与原版 int 字段（pEffect/pTrail）发射时拷贝一致——发射后编辑 NPC
 *       不影响在飞的弹射物；</li>
 *   <li>特效快照非空即 {@link ProjectileFxTracker#track} 登记周期尾迹
 *       （track 内部判 ServerLevel，纯客户端路径天然跳过）。</li>
 * </ul>
 *
 * 原生和附加效果都要求本次命中实际扣除生命值或吸收值。
 */
@Mixin(value = EntityProjectile.class, remap = false)
public abstract class MixinEntityProjectileBuffs {

    @Unique
    private Map<String, int[]> cnpcplus$buffs = Collections.emptyMap();

    @Unique
    private List<DropFxConfig> cnpcplus$fxs = Collections.emptyList();

    @Unique private Set<LivingEntity> cnpcplus$splashVictims = Set.of();

    @Inject(method = "getStatProperties", at = @At("RETURN"), remap = false)
    private void cnpcplus$snapshotOnLaunch(DataRanged stats, CallbackInfo ci) {
        if (stats == null) return;

        Map<String, int[]> buffs = RangedBuffStore.peek(stats);
        if (buffs == null || buffs.isEmpty()) {
            this.cnpcplus$buffs = Collections.emptyMap();
        } else {
            // 深拷贝（含 int[] 克隆）：快照独立于 store，后续编辑不影响在飞实体。
            Map<String, int[]> copy = new LinkedHashMap<>();
            for (Map.Entry<String, int[]> e : buffs.entrySet()) {
                int[] v = e.getValue();
                // Arrays.copyOf 而非 clone()：Mixin 处理 [I::clone() 会 NPE 导致本 mixin 静默挂不上（234 实测日志）
                copy.put(e.getKey(), v == null ? new int[0] : Arrays.copyOf(v, v.length));
            }
            this.cnpcplus$buffs = copy;
        }

        List<DropFxConfig> fxs = RangedFxStore.peek(stats);
        if (fxs == null || fxs.isEmpty()) {
            this.cnpcplus$fxs = Collections.emptyList();
        } else {
            List<DropFxConfig> copy = new ArrayList<>(fxs.size());
            for (DropFxConfig c : fxs) copy.add(c.copy());
            this.cnpcplus$fxs = copy;
            ProjectileFxTracker.track((EntityProjectile) (Object) this, copy, RangedFxStore.follows(stats));
        }
    }

    @Redirect(method = "onHit", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"), require = 1)
    private boolean cnpcplus$applyBuffsOnHit(Entity target, DamageSource source, float damage) {
        if (target instanceof LivingEntity victim && victim.level() instanceof ServerLevel) {
            if (!ProjectileExplosionBuffs.didDamage(victim, source, damage)) return false;
            cnpcplus$applyBuffs(victim);
            return true;
        }
        return target.hurt(source, damage);
    }

    @Unique private void cnpcplus$applyBuffs(LivingEntity victim) {
        for (Map.Entry<String, int[]> entry : this.cnpcplus$buffs.entrySet()) {
            int[] value = entry.getValue();
            if (value == null || value.length < 2) continue;
            // 238-4 哨兵「火」：命中点燃（等级忽略，秒=条目秒值），原版 effect=666 同语义。
            if (MeleeBuff.FIRE.equals(entry.getKey())) {
                victim.igniteForSeconds(MeleeBuff.clampSeconds(value[1]));
                continue;
            }
            Holder<MobEffect> effect = MeleeBuffUtil.holderOf(entry.getKey());
            if (effect == null) continue; // 对应 mod 已被移除：跳过而不是崩
            victim.addEffect(new MobEffectInstance(effect,
                    MeleeBuff.clampSeconds(value[1]) * 20,
                    MeleeBuff.clampAmp(value[0])));
        }
    }

    @Redirect(method = "onHit", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;)Lnet/minecraft/world/level/Explosion;"), require = 1)
    private Explosion cnpcplus$explode(Level level, Entity source, double x, double y, double z,
                                      float radius, boolean fire, Level.ExplosionInteraction interaction) {
        cnpcplus$splashVictims = Set.of();
        if (!(level instanceof ServerLevel)) return level.explode(source, x, y, z, radius, fire, interaction);
        var previous = ProjectileExplosionBuffs.ACTIVE.get();
        var impact = new ProjectileExplosionBuffs.Impact();
        ProjectileExplosionBuffs.ACTIVE.set(impact);
        Explosion explosion;
        try {
            explosion = level.explode(source, x, y, z, radius, fire, interaction);
        } finally {
            if (previous == null) ProjectileExplosionBuffs.ACTIVE.remove();
            else ProjectileExplosionBuffs.ACTIVE.set(previous);
        }
        if (explosion == impact.explosion) {
            cnpcplus$splashVictims = impact.damaged;
            for (LivingEntity victim : impact.damaged) cnpcplus$applyBuffs(victim);
        }
        return explosion;
    }

    @Redirect(method = "onHit", at = @At(value = "INVOKE", ordinal = 1, target =
            "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z"), require = 1)
    private boolean cnpcplus$applyStockSplash(LivingEntity victim, MobEffectInstance effect) {
        return cnpcplus$splashVictims.contains(victim) && victim.addEffect(effect);
    }

    @Redirect(method = "onHit", at = @At(value = "INVOKE", ordinal = 1, target =
            "Lnet/minecraft/world/entity/LivingEntity;setRemainingFireTicks(I)V"), require = 1)
    private void cnpcplus$applyStockSplashFire(LivingEntity victim, int ticks) {
        if (cnpcplus$splashVictims.contains(victim)) victim.setRemainingFireTicks(ticks);
    }

    @Inject(method = "onHit", at = @At("RETURN"), require = 1)
    private void cnpcplus$clearSplashVictims(HitResult hit, CallbackInfo ci) {
        cnpcplus$splashVictims = Set.of();
    }
}
