package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.client.Minecraft;
import noppes.npcs.client.layer.LayerBody;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 性能问题 17：LayerBody 裙摆每帧固定 20 个 draw call。
 *
 * <h3>根因（javap 字节码实证，哈基彬的数字准确）</h3>
 * {@code LayerBody.renderSkirt(float)}：
 * <pre>
 *  0-15: getPartData(EnumParts.SKIRT) == null → return   ← 未启用裙摆则零开销
 * 21: GlStateManager.func_179094_E()          (pushMatrix)
 * 24-30: scale(1.7f, 1.04f, 1.6f)
 * 33-34: i = 0
 * 36: bipush 10                                ← ★ 循环上界，硬编码 10 段
 * 38: if_icmpge 63
 * 41: ldc 36.0f  (= 360/10)                    ← 每段旋转角，同样硬编码
 * 46: GlStateManager.func_179114_b(36°, 0, 1, 0)   (rotate)
 * 54: ModelPlaneRenderer.func_78785_a(scale)       (render)
 * 57: iinc 3, 1
 * 63: GlStateManager.func_179121_F()          (popMatrix)
 * </pre>
 * {@code skirt} 由 {@code createParts} offset 741-813 构造：自身 1 个 plane +
 * 1 个子 {@code ModelPlaneRenderer}（也 1 个 plane，yaw -90°）。
 * vanilla {@code ModelRenderer.func_78785_a} 对自身与每个 child 各提交一次，
 * 所以每次迭代 2 个 draw call → **10 × 2 = 20 个 quad 提交 + 10 次 GL rotate**。
 *
 * 循环体内**没有任何距离、视锥或 LOD 判断**（offset 35-60 全是算术与绘制）。
 * 唯一的剔除是二值的：部件未启用（offset 15 提前 return）或实体对玩家不可见
 * （{@code LayerInterface.func_177141_a} offset 18-24 的 {@code func_98034_c}）。
 *
 * <h3>修法：按距离降段数</h3>
 * {@code @ModifyConstant} 把 {@code bipush 10} 换成距离相关值。
 * NPC 引用来自父类 {@code LayerInterface.npc}（protected，可 @Shadow）。
 *
 * <h3>为什么必须同时改旋转角</h3>
 * 每段旋转 {@code 36.0f} 是硬编码的（offset 41）。若只降段数不改角度，
 * 裙摆就只覆盖 {@code 段数 × 36°} 而不是完整 360° —— 4 段时只有 144°，
 * 会看到明显的缺口。所以两个常量必须**成对修改**，
 * 保持 {@code 段数 × 每段角度 = 360°}。
 *
 * 两个 ModifyConstant 各自独立求值，为保证一致性，段数计算抽成
 * {@link #cnpcplus$segments()}，两处都调它 —— 同一帧内 NPC 距离不变，
 * 所以两次调用结果必然一致。
 *
 * <h3>我曾推荐 B 但哈基彬选 A</h3>
 * 方案 B（10 段合并进一个 ModelRenderer 的 cubeList，20 → 2 draw call）
 * 效果更好且无视觉损失，但要重写 {@code createParts}。哈基彬选 A，按 A 实现。
 *
 * <h3>可撤回</h3>
 * cfg 的 {@code skirtDistanceLod}，默认开。关掉即恒用 10 段 / 36°（原版行为）。
 *
 * <h3>纯客户端</h3>
 * {@code LayerBody} 是渲染层。本混入注册 client 侧。
 */
@Mixin(value = LayerBody.class, remap = false)
public class MixinLayerBodySkirtLod {

    /**
     * 段数上界：近处保持原版 10 段，远处递减。
     *
     * 阈值用距离平方避免开方。12² = 144，24² = 576。
     *
     * <b>为什么经 {@link ILayerNpcAccessor} 而不是直接读 npc</b>：
     * {@code npc} 是 protected 且声明在父类 {@code LayerInterface}，
     * {@code @Shadow} 对继承成员在本项目环境下解析失败（findings 记过的坑），
     * 直接 cast 又因跨包访问 protected 而编译不过。故用 {@code @Accessor}。
     */
    private int cnpcplus$segments() {
        if (!CnpcPlusConfig.isSkirtDistanceLod()) return 10;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null) return 10;

        EntityCustomNpc target = ((ILayerNpcAccessor) this).cnpcplus$getNpc();
        if (target == null) return 10;

        double distSq = target.getDistanceSq(mc.player);
        if (distSq <= 144.0D) return 10;   // ≤ 12 格：原样
        if (distSq <= 576.0D) return 6;    // ≤ 24 格：draw call 20 → 12
        return 4;                          // 更远：draw call 20 → 8
    }

    @ModifyConstant(method = "renderSkirt",
            constant = @Constant(intValue = 10), remap = false, require = 1)
    private int cnpcplus$skirtSegments(int original) {
        return this.cnpcplus$segments();
    }

    /**
     * 旋转角同步调整，保持 360° 完整覆盖。
     *
     * 必须与段数成对：段数 6 → 每段 60°，段数 4 → 每段 90°。
     */
    @ModifyConstant(method = "renderSkirt",
            constant = @Constant(floatValue = 36.0F), remap = false, require = 1)
    private float cnpcplus$skirtAngle(float original) {
        int segments = this.cnpcplus$segments();
        if (segments <= 0) return original;
        return 360.0F / segments;
    }
}
