package top.cnpcplus.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.drop.fx.DropFxConfig;
import top.cnpcplus.drop.fx.runtime.DropFxEngine;
import top.cnpcplus.stats.DeathFxStore;

import java.util.List;

/**
 * 238-3：NPC 死亡粒子触发端。
 *
 * <h3>注入点</h3>
 * {@code EntityNPCInterface.m_6667_}（die(DamageSource)，反编译 :1348-1368）RETURN ——
 * 只此一处，防双发（其他死亡路径不另挂）。服务端判据用 {@code npc.level()} 是
 * {@code ServerLevel}（纯 MC 类型，规避 CNPC jar 混链 SRG 风险，234 先例）。
 *
 * <h3>行为</h3>
 * 单次 burst：{@code DropFxEngine.spawn(level, x, y, z, list)}（签名 :53-54 实证，
 * 234 尾迹已实证该锚点与掉落物解耦）。peek 空（没配过）直接早退，不给未配置 NPC 建条目。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCDieFx {

    @Inject(method = "m_6667_", at = @At("RETURN"), remap = false)
    private void cnpcplus$deathFx(DamageSource source, CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (!(npc.level() instanceof ServerLevel level)) return;
        if (npc.stats == null) return;
        List<DropFxConfig> fxs = DeathFxStore.peek(npc.stats);
        if (fxs == null || fxs.isEmpty()) return;
        DropFxEngine.spawn(level, npc.getX(), npc.getY(), npc.getZ(), fxs);
    }
}
