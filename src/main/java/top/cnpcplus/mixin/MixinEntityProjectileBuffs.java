package top.cnpcplus.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import noppes.npcs.entity.EntityProjectile;
import noppes.npcs.entity.data.DataRanged;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.drop.fx.DropFxConfig;
import top.cnpcplus.drop.fx.runtime.ProjectileFxTracker;
import bin.cnpcplus.melee.MeleeBuff;
import bin.cnpcplus.melee.MeleeBuffUtil;
import top.cnpcplus.ranged.RangedBuffStore;
import top.cnpcplus.ranged.RangedFxStore;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
 * <h3>命中施加（onHit HEAD）</h3>
 * 参数是 {@code HitResult}（非 EntityHitResult——方法体内 :378 才判 Type.ENTITY），
 * 所以 HEAD 先 instanceof 判实体命中，目标必须是 {@link LivingEntity}，
 * 再用<b>目标的</b> {@code lv.level()} 判服务端（不碰 EntityProjectile 自身的
 * level 方法，规避 CNPC jar SRG/mojmap 混链的编译风险——lv 是纯 MC 类型）。
 *
 * <p><b>与近战 doHurtTarget RETURN 的语义差（规划已拍板接受）</b>：
 * HEAD 在伤害结算之前，目标处于无敌帧时 BUFF 也会挂上（近战版伤害真实生效才挂）；
 * 弹射物命中必结算，差异只在边缘无敌帧，无害。
 *
 * <p><b>与原版单效果的关系：叠加不替换</b>——不动 :418-425 的
 * {@code effect/666} 段，旧存档单效果（含点燃）继续生效；
 * 新配置走 BUFF 表（注册名字符串键，查不到的 mod 效果跳过不崩）。
 * 爆炸范围同样应用快照，沿用来源的范围判据。
 */
@Mixin(value = EntityProjectile.class, remap = false)
public abstract class MixinEntityProjectileBuffs {

    @Unique
    private Map<String, int[]> cnpcplus$buffs = Collections.emptyMap();

    @Unique
    private List<DropFxConfig> cnpcplus$fxs = Collections.emptyList();

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

    @Inject(method = "onHit", at = @At("HEAD"), remap = false)
    private void cnpcplus$applyBuffsOnHit(HitResult result, CallbackInfo ci) {
        Map<String, int[]> buffs = this.cnpcplus$buffs;
        if (buffs == null || buffs.isEmpty()) return;
        if (!(result instanceof EntityHitResult ehr)) return;
        if (!(ehr.getEntity() instanceof LivingEntity victim)) return;
        if (!(victim.level() instanceof ServerLevel)) return; // 客户端不施加

        for (Map.Entry<String, int[]> entry : buffs.entrySet()) {
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

    /**
     * 238-4：爆炸段（{@code onHit} 内 {@code explosiveRadius > 0} 分支）
     * 也应用 BUFF 表快照——爆炸弹配了表时，波及范围内生物同样吃（含哨兵火）。
     *
     * <p>1.21.1字节码：remove的owner为EntityProjectile，偏移302/1224/1255；
     * 爆炸分支为1224（ordinal=1），普通命中路径不执行该调用。范围取法与原版爆炸
     * 单效果段（:528）同款：{@code getBoundingBox().inflate(explosiveRadius * 2)}。
     */
    @Inject(method = "onHit", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityProjectile;remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
            ordinal = 1), remap = false)
    private void cnpcplus$applyBuffsOnExplosion(CallbackInfo ci) {
        EntityProjectile self = (EntityProjectile) (Object) this;
        if (self.explosiveRadius <= 0) return;
        Map<String, int[]> buffs = this.cnpcplus$buffs;
        if (buffs == null || buffs.isEmpty()) return;
        if (!(self.level() instanceof ServerLevel)) return;

        List<LivingEntity> victims = self.level().getEntitiesOfClass(LivingEntity.class,
                self.getBoundingBox().inflate(self.explosiveRadius * 2));
        for (LivingEntity victim : victims) {
            for (Map.Entry<String, int[]> entry : buffs.entrySet()) {
                int[] value = entry.getValue();
                if (value == null || value.length < 2) continue;
                if (MeleeBuff.FIRE.equals(entry.getKey())) {
                    victim.igniteForSeconds(MeleeBuff.clampSeconds(value[1]));
                    continue;
                }
                Holder<MobEffect> effect = MeleeBuffUtil.holderOf(entry.getKey());
                if (effect == null) continue;
                victim.addEffect(new MobEffectInstance(effect,
                        MeleeBuff.clampSeconds(value[1]) * 20,
                        MeleeBuff.clampAmp(value[0])));
            }
        }
    }
}
