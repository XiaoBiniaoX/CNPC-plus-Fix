package bin.cnpcplus.mixin.perf;

import noppes.npcs.client.ClientEventHandler;
import noppes.npcs.blocks.tiles.TileBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 性能问题 1：建筑蓝图预览的每帧重建（哈基彬：「主要恶心的是每帧重试渲染，
 * 这个是明确存在的 FPS 影响」）。
 *
 * <h3>先纠正：display list 缓存本来就有（哈基彬已确认）</h3>
 * 哈基彬原描述是「每帧重新遍历、渲染最多 25000 方块」。字节码显示不是：
 * <pre>
 * ClientEventHandler.onRenderTick:
 * 225: getstatic TileBuilder.Compiled
 * 228: ifeq 241                  ← 已编译则跳过重建
 * 231-235: GlStateManager.func_179148_o(displayList)   ← glCallList，直接回放
 * 241起: 否则才 GLAllocation.func_74526_a + GL11.glNewList + 遍历
 * </pre>
 * 而 {@code Compiled} 只在两处被置 false：
 * {@code TileBuilder.SetDrawPos()} offset 5、以及 GUI 的 rotation 按钮
 * （{@code GuiBlockBuilder.func_146284_a} offset 119-120）。
 *
 * 另外 {@code yOffest} 只进 {@code glTranslate}（offset 119-127），
 * **不进 display list 内容** —— 所以改 yOffset 本来就不需要重建，已是最优。
 *
 * <h3>真实缺陷（这才是要修的）</h3>
 * <pre>
 * 566: GL11.glEndList()
 * 569: GL11.glGetError()
 * 572: ifne 626                  ← ★ 有 GL 错误就跳过 Compiled = true
 * 575-576: Compiled = true       ← 只有无错误才标记
 * </pre>
 * {@code glGetError} 非 0 时 {@code Compiled} 保持 false → **下一帧再来一遍完整
 * 遍历 + glNewList**，稳态每帧重建。这就是哈基彬感受到的 FPS 影响。
 *
 * GL 错误在实际环境里并不罕见（其他 mod 的渲染残留错误标志、
 * 驱动的非致命警告都会让 {@code glGetError} 返回非 0，而它是**读取即清除**的
 * 全局状态，未必是本次 glNewList 造成的）。
 *
 * <h3>修法（三件事，对应哈基彬的三点要求）</h3>
 * <ol>
 *   <li><b>消除每帧重建</b>：TAIL 无条件把 {@code Compiled} 置 true。
 *       即使这一帧编译有问题，也不要每帧重试 —— 用户改 XYZ / 旋转 / 换蓝图 /
 *       开关预览时会自然重建（那几处都会调 {@code SetDrawPos}）。</li>
 *   <li><b>显示完整方块</b>：25000 上限提到 65536（哈基彬要求「和 CNPC 原来一样
 *       显示完整方块方便看」）。25000 对 32³=32768 的蓝图就已经截断了。</li>
 *   <li><b>移动 XYZ 时重建</b>：见下方说明 —— 实际已由 SetDrawPos 覆盖。</li>
 * </ol>
 *
 * <h3>关于「移动 XYZ 才更新渲染」</h3>
 * 哈基彬要求「只有移动 XYZ、旋转、选择其他蓝图、开关预览才更新这个渲染」。
 * 核对后确认这四种情况**已经全部覆盖**：
 * <ul>
 *   <li>开关预览：{@code GuiBlockBuilder.func_146284_a} 按钮 3 两个分支
 *       （offset 39 开 / offset 64 关）都调 {@code SetDrawPos} → {@code Compiled = false}；</li>
 *   <li>选择其他蓝图：{@code scrollClicked} offset 26 调 {@code SetDrawPos}；</li>
 *   <li>旋转：按钮 5 offset 119-120 直接 {@code Compiled = false}；</li>
 *   <li>移动 XYZ：XYZ 是 {@code GuiBlockBuilder} 的构造参数（方块坐标本身），
 *       移动建造器方块 = 破坏再放置 = 新的 TileEntity + 新的 GUI，
 *       必然重新走 {@code SetDrawPos}。而蓝图内容与 XYZ 无关
 *       （渲染时才 {@code glTranslate} 到 DrawPos），所以内容无需重建。</li>
 * </ul>
 * 结论：这一诉求原版架构已满足，不需要额外改动。
 *
 * <h3>纯客户端</h3>
 * {@code ClientEventHandler} 是客户端渲染事件处理器，本混入注册 client 侧。
 */
@Mixin(value = ClientEventHandler.class, remap = false)
public class MixinClientEventHandlerBlueprint {

    /**
     * 把方块数上限从 25000 提到 65536，让大蓝图能完整显示。
     *
     * 65536 = 64³ 的四分之一，也是 40³ 的上界，覆盖绝大多数实用蓝图。
     * 不设更大是因为 display list 本身有显存开销，且超大蓝图的预览意义有限。
     */
    @ModifyConstant(method = "onRenderTick",
            constant = @Constant(intValue = 25000), remap = false, require = 1)
    private int cnpcplus$raiseBlockLimit(int original) {
        return 65536;
    }

    /**
     * 无条件标记已编译，消除 glGetError 非 0 时的每帧重建。
     *
     * 用 TAIL 而不是 Redirect 掉 {@code glGetError}：后者会同时影响
     * 那三处 {@code Compiled = true} 的分支（offset 575、601、619，
     * 分别是正常路径、catch 路径、finally 路径），语义更绕。
     * TAIL 一处覆盖，且逻辑直白 ——「这一帧编译过了就不要再试」。
     */
    @Inject(method = "onRenderTick", at = @At("TAIL"), remap = false, require = 1)
    private void cnpcplus$stopPerFrameRebuild(
            net.minecraftforge.client.event.RenderWorldLastEvent event, CallbackInfo ci) {
        // DrawPos 为 null 时原版已提前 return，走不到这里；
        // 走到这里说明预览是开着的，那就认定编译完成。
        if (TileBuilder.DrawPos != null) {
            TileBuilder.Compiled = true;
        }
    }
}
