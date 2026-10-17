package top.cnpcplus.mixin;

import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.EntityProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.cnpcplus.ranged.RangedSpinStore;

/**
 * 238-9：弹射物自旋速度系数消费端（238c：X+Y 双轴翻滚 + 客户端不本地旋转）。
 *
 * <h3>原版行为</h3>
 * {@code EntityProjectile.tick}（反编译 :293-296）：{@code isRotating && !isArrow}
 * 时每 tick {@code setXRot(getXRot() - (isBlock?10:20) * getSpeed())} —— 系数硬编码，
 * 只转 X 轴（映射链实证：setXRot=m_146926_；自旋段=tick 内第 3 次 m_146926_，ordinal=2；
 * tick 开头的运动对准段仅在 xRotO/yRotO 均为 0 的首帧执行，不会逐帧抹掉偏航累积）。
 *
 * <h3>修法（238c）</h3>
 * <ul>
 *   <li>系数从 owner NPC 的 {@link RangedSpinStore} 每 tick 现读；=0 回落原公式，
 *       负数=反旋（哈基彬拍板可输负数）。</li>
 *   <li>系数非 0 时 X/Y 两轴同速反向推进（X 减 Y 加），观感为双轴翻滚
 *       （哈基彬拍板：保持三轴乱翻的观感取 X+Y 方案）。</li>
 *   <li>owner 改读 {@code npc} 字段（{@link Shadow}）——反编译实证：该字段在发射主构造
 *       flag 分支赋值，而 {@code Projectile.getOwner()} 依赖的 ownerUUID 全工程从未
 *       setOwner，旧版 getOwner() 恒返回 null，系数因此双端全失效（238c 根因）。</li>
 *   <li>客户端 {@code npc} 未同步为 null：直接不写旋转，以服务端每 tick 同步的绝对
 *       旋转为准——否则本地会把原版 20*speed 增量叠在服务端自定义值上，速度显示错。</li>
 * </ul>
 *
 * <p>{@code require = 1}：自旋核心路径，注入失败必须崩出来。
 */
@Mixin(value = EntityProjectile.class, remap = false)
public class MixinEntityProjectileSpin {

    @Shadow(remap = false)
    private EntityNPCInterface npc;

    @Redirect(method = "m_8119_", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityProjectile;m_146926_(F)V",
            ordinal = 2), require = 1, remap = false)
    private void cnpcplus$customSpin(EntityProjectile self, float yaw) {
        if (this.npc == null) return;
        float factor = this.npc.stats != null ? RangedSpinStore.get(this.npc.stats.ranged) : 0.0f;
        if (factor == 0.0f) {
            self.setXRot(yaw);
            return;
        }
        float step = factor * self.getSpeed();
        self.setXRot(self.getXRot() - step);
        self.setYRot(self.getYRot() + step);
    }
}
