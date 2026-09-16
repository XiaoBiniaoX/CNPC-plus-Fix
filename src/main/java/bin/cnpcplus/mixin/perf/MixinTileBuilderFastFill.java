package bin.cnpcplus.mixin.perf;

import noppes.npcs.blocks.tiles.TileBuilder;
import noppes.npcs.schematics.SchematicWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.Stack;

/**
 * 性能问题 3：建造器设置大型 schematic 时的 O(n²)（哈基彬报告：会导致服务器长期冻结）。
 *
 * <h3>根因（javap 字节码实证）</h3>
 * {@code TileBuilder.setSchematic} 用四段循环按象限枚举方块索引，每个索引都通过
 * <pre>
 * offset  99: invokevirtual java/util/Stack.add:(ILjava/lang/Object;)V
 * offset 174: 同上
 * offset 249: 同上
 * offset 332: 同上
 * </pre>
 * 插入，且第一个参数恒为 {@code iconst_0}（索引 0）。
 * {@code java.util.Stack} 继承 {@code Vector}，{@code add(0, x)} 每次都要
 * {@code System.arraycopy} 把已有元素整体后移一格。
 *
 * 于是总代价是 0+1+2+…+(n-1) = n(n-1)/2 次元素移动：
 * <ul>
 *   <li>32³ = 32768 方块  → 约 5.4×10⁸ 次</li>
 *   <li>64³ = 262144 方块 → 约 3.4×10¹⁰ 次</li>
 * </ul>
 * 这正是「不是普通掉 TPS，而是服务器长期冻结」的量级 —— 单个 `setSchematic`
 * 调用就能占住主线程几十秒到几分钟。
 *
 * <h3>修法</h3>
 * 把四处头插换成尾部追加（{@code push}，均摊 O(1)），整段填完后
 * 在 TAIL 一次性 {@code Collections.reverse}。
 *
 * 复杂度 O(n²) → O(n)，而**出栈顺序与原版完全一致**：
 * 原版「依次头插 a,b,c」得到 [c,b,a]；本方案「依次尾插」得到 [a,b,c]，
 * 反转后同样是 [c,b,a]。{@code getBlock()} 用 {@code pop()} 从尾部取，
 * 因此建造顺序（先地基后上层的象限顺序）逐块不变。
 *
 * <h3>为什么不动 getBlock 里那两处 add(0,x)</h3>
 * 那两处（offset 308、439）的循环上界是 {@code min(width*length/4, 30)}，
 * 即单次最多 30 个元素，O(30²) 可忽略。按最小改动原则不碰。
 *
 * <h3>服务端安全</h3>
 * {@code TileBuilder} 是两端共有的 TileEntity，但 {@code setSchematic} 的
 * 调用链在服务端（{@code SchematicsSet} 包处理）。本混入注册在 common 侧，
 * 只用 {@code java.util} 与 noppes 自有类型，无客户端引用。
 */
@Mixin(value = TileBuilder.class, remap = false)
public class MixinTileBuilderFastFill {

    /**
     * {@code positions} 是 TileBuilder 自有的 private 字段（非继承），
     * {@code @Shadow} 可正常解析。findings 里「@Shadow 对 noppes 类不可靠」
     * 指的是**继承来的**成员，不适用于本类自有字段。
     */
    @Shadow private Stack<Integer> positions;

    /**
     * 把 {@code positions.add(0, index)} 换成尾部追加。
     *
     * {@code @Redirect} 会拦下 {@code setSchematic} 内**全部四处**同签名调用，
     * 这正是我们想要的（四个象限的填充逻辑相同）。
     *
     * 注意 target 指向的是 {@code java.util.Stack} 的成员，它属于 JDK 而非 MC，
     * 因此不涉及 SRG 重映射，写原名即可。
     */
    @Redirect(method = "setSchematic",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/Stack;add(ILjava/lang/Object;)V"),
            remap = false, require = 4)
    private void cnpcplus$appendInsteadOfShift(Stack<Integer> stack, int index, Object value) {
        // 忽略传入的 index（原版恒为 0），直接尾插。
        stack.add(value == null ? null : (Integer) value);
    }

    /**
     * 四段循环全部填完后反转一次，恢复原版的出栈顺序。
     *
     * 用 TAIL 而不是每段结束后各反转一次：原版四个象限是**连续头插进同一个
     * Stack**，等价于「整体反转」，逐段反转会打乱段间顺序。
     *
     * 注意原版 {@code setSchematic} 在 {@code schematic == null} 时会提前
     * {@code return}（offset 5-23），此时 TAIL 不会执行，也不需要反转。
     */
    @Inject(method = "setSchematic", at = @At("TAIL"), remap = false, require = 1)
    private void cnpcplus$restoreOrder(SchematicWrapper wrapper, CallbackInfo ci) {
        if (wrapper == null || this.positions == null) return;
        Collections.reverse(this.positions);
    }
}
