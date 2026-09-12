package top.cnpcplus.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.registries.ForgeRegistries;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.config.CnpcPlusServerConfig;
import top.cnpcplus.config.ServerConfigAccess;

/**
 * 修复「模型/替身」换成其他 mod 生物后，碰撞箱与视觉分离（NPC 摸不到、跑很远还被打到）。
 *
 * <h3>根因（反编译实证）</h3>
 * {@code EntityCustomNpc.m_6972_}（反编译 111-140 行）算维度时对替身分了两支：
 * <ul>
 *   <li>{@code :123-124} 替身是 CNPC 自家实体 → {@code size.scale(display.getSize() * 0.2f)}，
 *       与服务端基类 {@code EntityNPCInterface.m_6972_:1270} 的表达式**完全一致**；</li>
 *   <li>{@code :126-127} 替身是外部 mod 生物 → {@code size / 5 * display.getSize()}，
 *       与基类的 {@code baseSize(0.6, 1.8)} 基准**彻底脱钩**。</li>
 * </ul>
 * 这正是「我们的修复只对 CustomNPC 有效，换成 SCP / 凋零风暴的模型就复现」的原因。
 *
 * <p>而 {@code m_6972_} 里取替身的方式是 {@code modelData.getEntity(npc)}，它在
 * {@code ModelData.getEntity:56} 用 {@code EntityType.create(level)} **真的造一个实体**，
 * 还要复制装备、读 maxHealth。服务端在维度计算这条每 tick 都会走的路径上造实体是不可接受的，
 * 所以实际表现取决于该实体能否成功创建 —— 双端于是可能落在不同分支，碰撞箱各算各的。
 *
 * <h3>修复</h3>
 * 完全不碰替身实体，改从注册表按 {@code entityName} 取 {@code EntityType.getDimensions()}
 * （静态默认维度，双端一定相同），再走与原版外部模型分支相同的 {@code /5*getSize()} 公式。
 * 于是两端必然得到同一个碰撞箱。
 *
 * <p>哈基彬已明确：不用管「维度随状态变化的生物」（Boss 类的可变维度），
 * CNPC 本来就不适合多维度环境。所以这里只取静态维度，不做任何 pose 相关处理。
 *
 * <h3>为什么用 RETURN 覆盖而不是 @Redirect getEntity</h3>
 * {@code getEntity} 在 {@code m_6972_} 里只调一次，但同一个方法还有 {@code m_6048_}（骑乘高度）
 * 和 {@code m_6210_} 在用它，{@code @Redirect} 会牵连别处或需要多份精确定位。
 * 在 RETURN 处按同一套输入重算一次更直接，且原版分支逻辑保持原样不动 ——
 * 没有替身、或替身是 CNPC 自家实体时，一律放行原版返回值。
 *
 * <h3>顺带修掉的次生问题</h3>
 * 原版 {@code :137-139} 在宽度超过 {@code maxEntityRadius} 时无上限地
 * {@code increaseMaxEntityRadius}。那是个全世界共享的值，会放大**所有**实体的 AABB 查询范围，
 * 一个配错的替身就能拖慢整个存档。这里夹到 16 格。
 */
@Mixin(value = EntityCustomNpc.class, remap = false)
public abstract class MixinEntityCustomNpcDimensions {

    /**
     * 世界实体查询半径上限（格）。原版无上限，见类注释。
     * 16 是留足余量的值：原版 maxEntityRadius 默认 2，正常 NPC 放大 5 倍也才 3 格。
     */
    private static final double MAX_RADIUS = 16.0;

    @Inject(method = "m_6972_", at = @At("RETURN"), cancellable = true, remap = false)
    private void cnpcplus$consistentDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        EntityCustomNpc self = (EntityCustomNpc) (Object) this;
        if (self.modelData == null || !self.modelData.hasEntity()) return;

        // display / stats / level 都必须判空：本方法在**实体构造期**就会被 vanilla 的
        // Entity 构造器通过 refreshDimensions 调到，那时 CNPC 的这些数据对象可能还没赋值。
        // 原版自己也防了这一手 —— :112 的 `if (this.modelData == null)` 就是同一个理由。
        // 缺了任何一个都会在实体创建时 NPE，专用服务器上表现为生成 NPC 即崩。
        if (self.display == null || self.stats == null || self.level() == null) return;

        ResourceLocation name = self.modelData.getEntityName();
        if (name == null) return;

        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(name);
        // 对应 mod 已被移除：放行原版返回值（原版此时 getEntity 也拿不到实体，会走人形分支）。
        if (type == null) return;

        // 替身是 CNPC 自家 NPC 时原版走 :124 的 scale(getSize()*0.2)，与服务端基类一致，
        // 本来就没有分裂问题，放行原版结果，避免把已经正确的行为改坏。
        //
        // 用命名空间判断而不是 `getEntity() instanceof EntityNPCInterface`：后者会触发
        // ModelData.getEntity:56 的 EntityType.create(level) **真的造一个实体**，
        // 而本方法在双端每 tick 都会被调用。CNPC 自家实体全部注册在 customnpcs 命名空间下
        // （CustomEntities.java:100-105、124、140 实证），判前缀即可，零开销且双端一致。
        if ("customnpcs".equals(name.getNamespace())) return;

        EntityDimensions base = type.getDimensions();
        if (base == null) return;

        // 与原版外部模型分支（:126-133）完全相同的公式与下限，只是维度来源换成注册表。
        float size = self.display.getSize();
        float width = base.width / 5.0f * size;
        float height = base.height / 5.0f * size;
        if (width < 0.1f) width = 0.1f;
        if (height < 0.1f) height = 0.1f;

        // 与原版 :134-136 一致：石像模式或「隐藏尸体」的尸体不占碰撞体积。
        //
        // isKilled() 读 datawatcher（反编译 EntityNPCInterface:1666-1668 的
        // f_19804_.get(IsDead)），而 defineSynchedData 与 refreshDimensions 都在
        // Entity 构造器里、顺序不由我们决定；维度先算时那次 get 会抛。
        // 异常时按「不是尸体」处理，维度照常算。
        boolean killed = cnpcplus$isKilledSafe(self);
        if (self.display.getHitboxState() == 1 || (killed && self.stats.hideKilledBody)) {
            width = 1.0E-5f;
        }

        // 「固定点位重生」的 NPC 尸体连箭都不该挡（哈基彬需求）。
        //
        // 这一段刻意写在本 mixin 里而不是 MixinEntityNPCKilledBody：
        // EntityCustomNpc 覆写了 m_6972_，替身分支根本不会走到基类实现；
        // 而两个 mixin 都注入同一方法的 RETURN 时执行顺序没有保证，
        // 分开写会让「谁的 setReturnValue 生效」变成运气。合并到这里，顺序就是代码顺序。
        // 走 ServerConfigAccess 而不是裸 get()：本方法在**实体构造期**就会被
        // vanilla Entity 构造器通过 refreshDimensions 调到，那时 SERVER 配置可能还没 attach，
        // 开发环境会抛 IllegalStateException 把实体创建炸掉（字节码实证见 ServerConfigAccess）。
        if (ServerConfigAccess.bool(CnpcPlusServerConfig.KilledBodyNoHitbox, true)
                && self.ais != null && self.ais.returnToStart
                && (self.currentAnimation == 2 || self.currentAnimation == 7
                    || self.deathTime > 0 || killed)) {
            width = 1.0E-5f;
        }

        double half = width / 2.0f;
        if (half > self.level().getMaxEntityRadius()) {
            self.level().increaseMaxEntityRadius(Math.min(half, MAX_RADIUS));
        }
        cir.setReturnValue(new EntityDimensions(width, height, false));
    }

    /** {@code isKilled()} 的安全包装，理由见调用处注释。 */
    private static boolean cnpcplus$isKilledSafe(EntityCustomNpc npc) {
        try {
            return npc.isKilled();
        } catch (Throwable t) {
            return false;
        }
    }
}
