package top.cnpcplus.mixin;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.config.CnpcPlusServerConfig;
import top.cnpcplus.config.ServerConfigAccess;

/**
 * 尸体不再有碰撞箱（连箭都挡不住），且复活后碰撞箱重新居中。
 *
 * <h3>问题 1：尸体仍是个实心障碍物</h3>
 * 原版把尸体缩成 1e-5 的条件是
 * {@code hitboxState == 1 || (isKilled() && stats.hideKilledBody)}
 * （反编译 {@code EntityNPCInterface.m_6972_:1271-1274}）。而默认
 * {@code hideKilledBody = false}（{@code DataStats.java:41}）、{@code hitboxState = 0}
 * （{@code DataDisplay.java:90}），**两条都不满足 → 尸体保持正常宽度**。
 * 更糟的是死亡时 {@code currentAnimation == 2} 会让 {@code :1265-1266} 换成
 * {@code sizeSleep(0.8, 0.4)}，宽度 0.8 比站立的 0.6 **还宽**。
 *
 * <p>所以 {@code refreshDimensions} 在死亡时其实调过了（{@code :618}、{@code :1513}），
 * 不是「没刷新」。默认配置下尸体就是个 0.8 宽的实心障碍：挡路、挡箭、推开玩家。
 *
 * <h3>修法：与原版同一手法，只把 width 压到 1e-5</h3>
 * 在 {@code m_6972_} 的 RETURN 处改写返回的宽度，**保留 height**。
 * 这与原版 {@code :1274} 的 {@code EntityDimensions.scalable(1.0E-5f, size.height)}
 * 完全是同一套写法，也与哈基彬在 1.12.2 上定稿的做法一致（那边只写 width 字段）。
 * 宽度趋零后包围盒退化成一条竖线，实体碰撞、射线命中、投射物全都不再被拦；
 * 保留 height 让原版渲染与 {@code RenderNPCInterface} 的尸体判定不受干扰。
 *
 * <p>另外仍然取消 {@code m_6138_}（pushEntities，{@code :1789-1794}）——
 * 它只看 {@code hitboxState}、**完全不看死亡状态**。宽度虽已趋零，
 * 但推挤是按 AABB 膨胀后取实体列表的，明确 cancel 更干净。
 *
 * <h3>为什么限定「返回起点为开」</h3>
 * 哈基彬要的是「固定点位重生的 NPC」。判据取 {@code ais.returnToStart}
 * （AI 设置里的「返回起点」开关，{@code DataAI.java:53}，NBT 键 {@code ReturnToStart}）：
 * 它为真时 {@code reset()}（{@code :1319-1321}）会把 NPC 传回起点，
 * 也就是「死在哪里都会回到固定点位重生」。这类 NPC 的尸体必然留在与重生点无关的位置，
 * 挡路且无意义。
 *
 * <p>不用 {@code stats.spawnCycle} 做判据：那是「什么时段重生」，与「在哪里重生」无关，
 * 拿它当条件会影响到不该影响的 NPC。
 *
 * <h3>为什么不怕加重复活错位</h3>
 * 宽度从 1e-5 跳回 0.6 属于「变大」，vanilla {@code refreshDimensions} 会按角点重建 AABB
 * 从而偏移 —— 但下面问题 2 的两处 setPos 重居中正好把它抹掉。两件事是配套的。
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
        if (!ServerConfigAccess.bool(CnpcPlusServerConfig.KilledBodyNoPush, true)) return;
        if (((EntityNPCInterface) (Object) this).isKilled()) {
            ci.cancel();
        }
    }

    /**
     * 尸体宽度压到 1e-5，包围盒退化成一条竖线 —— 连箭都挡不住。
     *
     * <p>{@code EntityCustomNpc} 覆写了 {@code m_6972_}，替身分支由
     * {@code MixinEntityCustomNpcDimensions} 一并处理（两个 mixin 注入同一方法 RETURN 时
     * 执行顺序没有保证，所以那边把尸体判定写进了自己的宽度计算里）。
     *
     * <p>config 走 {@code ServerConfigAccess}：本方法在实体构造期就会被调到，
     * 那时 SERVER 配置可能还没 attach（详见 {@code ServerConfigAccess} 的字节码实证）。
     */
    @Inject(method = "m_6972_", at = @At("RETURN"), cancellable = true, remap = false)
    private void cnpcplus$deadBodyNoHitbox(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (!ServerConfigAccess.bool(CnpcPlusServerConfig.KilledBodyNoHitbox, true)) return;
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        // 只对「固定点位重生」（返回起点为开）的 NPC 生效，理由见类注释。
        // ais 判空是必须的：本方法在实体构造期就会被调到，那时 CNPC 数据对象可能还没赋值。
        if (npc.ais == null || !npc.ais.returnToStart) return;
        if (!cnpcplus$isCorpse(npc)) return;

        EntityDimensions size = cir.getReturnValue();
        if (size == null) return;
        if (size.width <= 1.0E-5f) return;
        // 与原版 :1274 同一写法：只压 width，保留 height。
        cir.setReturnValue(EntityDimensions.scalable(1.0E-5f, size.height));
    }

    /**
     * 与原版 {@code m_6972_:1265} 同一套「是不是尸体」的判据。
     *
     * <p>{@code currentAnimation} 2 与 7 是死亡/躺倒动画，{@code deathTime}（SRG f_20919_）
     * 大于 0 表示正在播死亡过程，{@code isKilled()} 覆盖「已死但等待重生」这段。
     * 四者取并集，确保从倒地到复活前全程无碰撞箱。
     *
     * <h3>为什么整段包 try-catch</h3>
     * {@code isKilled()}（反编译 {@code :1666-1668}）读的是 datawatcher
     * （{@code f_19804_.get(IsDead)}），而 vanilla 的 {@code defineSynchedData} 与
     * {@code refreshDimensions} **都在 Entity 构造器里**，谁先执行不由我们决定。
     * 若维度计算先跑，那次 {@code get(IsDead)} 会抛「Data value not registered」。
     * 原版基类 {@code :1272} 自己也调 {@code isKilled()}，所以这条路径原版就在跑；
     * 但我们没必要把风险再放大一次 —— 异常时按「不是尸体」处理，维度走原版结果。
     */
    private static boolean cnpcplus$isCorpse(EntityNPCInterface npc) {
        try {
            return npc.currentAnimation == 2
                    || npc.currentAnimation == 7
                    || npc.deathTime > 0
                    || npc.isKilled();
        } catch (Throwable t) {
            return false;
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
