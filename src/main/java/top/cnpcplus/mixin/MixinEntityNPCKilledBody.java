package top.cnpcplus.mixin;

import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.config.CnpcPlusServerConfig;

/**
 * 尸体不再推挤玩家，且复活后碰撞箱重新居中。
 *
 * <h3>问题 1：尸体仍有碰撞箱</h3>
 * 原版把尸体缩成 1e-5 的条件是
 * {@code hitboxState == 1 || (isKilled() && stats.hideKilledBody)}
 * （反编译 {@code EntityNPCInterface.m_6972_:1271-1274}）。而默认
 * {@code hideKilledBody = false}（{@code DataStats.java:41}）、{@code hitboxState = 0}
 * （{@code DataDisplay.java:90}），**两条都不满足 → 尸体保持正常宽度**。
 * 更糟的是死亡时 {@code currentAnimation == 2} 会让 {@code :1265-1266} 换成
 * {@code sizeSleep(0.8, 0.4)}，宽度 0.8 比站立的 0.6 **还宽**。
 *
 * <p>所以 {@code refreshDimensions} 在死亡时其实调过了（{@code :618}、{@code :1513}），
 * 不是「没刷新」。玩家真正能感觉到的碰撞来自
 * {@code m_6138_}（pushEntities，{@code :1789-1794}）—— 它只看 {@code hitboxState}，
 * **完全不看死亡状态**，于是尸体每 tick 继续把附近玩家推开。
 *
 * <p>这里在 {@code isKilled()} 时取消推挤。刻意**不动维度**：改宽度会引入 AABB 位移，
 * 反而加重下面问题 2 的错位；而推挤是玩家唯一能感知到的那部分。
 *
 * <h3>问题 2：复活后模型与碰撞箱不一致</h3>
 * {@code reset()} 里先 {@code m_7678_} 移动位置（{@code :1319}）、后
 * {@code m_6210_()} 刷新维度（{@code :1330}），但之后**没有**重新 setPos。
 * vanilla {@code refreshDimensions} 在宽度**变大**时是按 {@code minX/minZ} 角点重建 AABB
 * （只有变小才按中心重建），所以从尸体的 0.8（或 1e-5）回到站立的 0.6 这一跳，
 * AABB 会相对实体中心偏出最多半个宽度 —— 这正是「偶尔」错位的来源，
 * 是否触发取决于死前 hitboxState / hideKilledBody 的组合。
 *
 * <p>CNPC 自己在别处是知道要补这一步的：{@code EntityAIAnimation.java:92-96} 的
 * {@code setAnimation} 就是「setCurrentAnimation → refreshDimensions → setPos 重新居中」。
 * 这里照抄那个做法，在 {@code reset()} 与客户端 {@code readSpawnData} 之后各补一次。
 *
 * <p>{@code readSpawnData} 也要补：客户端走
 * {@code readSpawnData → modelData.load(:1745) → display.readToNBT(:1747) → m_6210_(:1748)}，
 * 其中 {@code ModelDataShared.load:100} 的 {@code setEntity} 会 {@code clearEntity()}，
 * 使替身缓存失效、下一次 {@code m_6972_} 重新懒加载，同样存在宽度跳变。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinEntityNPCKilledBody {

    /** 尸体不再推挤实体。EntityCustomNpc 覆写了同名方法，另有一份 mixin 处理。 */
    @Inject(method = "m_6138_", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$deadBodyNoPush(CallbackInfo ci) {
        if (!CnpcPlusServerConfig.KilledBodyNoPush.get()) return;
        if (((EntityNPCInterface) (Object) this).isKilled()) {
            ci.cancel();
        }
    }

    /**
     * 复活后把 AABB 重新居中到实体坐标。
     *
     * <p>用当前坐标原地 setPos：vanilla {@code Entity.setPos} 会
     * {@code setBoundingBox(dimensions.makeBoundingBox(x, y, z))}，即以实体位置为中心重建，
     * 从而抹掉 refreshDimensions 按角点扩张造成的偏移。位置本身不变，无副作用。
     */
    @Inject(method = "reset", at = @At("RETURN"), remap = false)
    private void cnpcplus$recenterAfterRespawn(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        npc.setPos(npc.getX(), npc.getY(), npc.getZ());
    }

    @Inject(method = "readSpawnData(Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At("RETURN"), remap = false)
    private void cnpcplus$recenterAfterSpawnData(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        npc.setPos(npc.getX(), npc.getY(), npc.getZ());
    }
}
