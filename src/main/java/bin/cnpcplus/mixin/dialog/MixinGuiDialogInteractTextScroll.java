package bin.cnpcplus.mixin.dialog;

import noppes.npcs.client.ClientProxy;
import noppes.npcs.client.gui.player.GuiDialogInteract;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让对话文本限制在对话框区域内，并支持滚轮上下查看历史发言。
 *
 * 现象（哈基彬）：CNPC 的对话框对话文本没有滑块，文本超出就必定看不见。
 *
 * 根因（证据）：`GuiDialogInteract` 其实已经有行偏移机制 —— 私有字段
 * `rowStart`，在 `drawString` 里以 `height = count - rowStart` 参与 y 计算。
 * 但它缺两样东西：
 *  1. `rowStart` 只在 `calculateRowHeight` 里被**被动**算成
 *     `rowTotal - dialogHeight / fontHeight`，即永远钉死在「最后一屏」，
 *     没有任何输入能改它 —— 玩家只能看到末屏，之前的发言被顶到
 *     guiTop 之上（负 y）飘出屏幕。
 *  2. 绘制期完全没有边界检查：`drawScreen` 的两层循环无条件全画，
 *     `dialogHeight` 从不参与绘制裁剪，所以既会往上溢出，
 *     也可能让末行压到 `drawLinedOptions` 的选项区（选项从
 *     `guiTop + dialogHeight` 起画）。
 *
 * 因此这里复用 CNPC 已有的 `rowStart`，只补上「输入」和「上下边界」：
 *  1. drawScreen HEAD 读滚轮改 `rowStart`，夹在
 *     [0, max(0, rowTotal - dialogHeight/fontHeight)]。
 *     上限表达式与 `calculateRowHeight` 的原始算式一致，
 *     所以默认值天然等于滚动上限 —— 语义正好是「默认停在最新一屏，可往上翻」。
 *  2. `drawString` HEAD 判断该行是否落在可视窗口，窗口外直接 cancel。
 *     这是唯一的文本出口（说话人名与正文都走它），一处即可同时管住上下沿。
 *
 * 与轮盘不冲突：`showWheel` 的轮盘用的是 `Mouse.getDX/getDY`（抓取指针后的
 * 相对位移），不是 `getDWheel`。两种模式下滚轮值原本都无人消费。
 * 本混入只读 DWheel，绝不碰 DX/DY，因此不会吃掉轮盘输入。
 *
 * 与既有 `MixinGuiDialogInteractOptionHeight` 不冲突：那个只 Redirect
 * `calculateRowHeight` 内的 `HashMap.size()`，不碰 `rowStart` 与绘制；
 * 本混入不动 `calculateRowHeight`。但因为它会改变 `dialogHeight`，
 * 所以滚动上限必须运行期现算，不能缓存。
 *
 * 行高必须用 `ClientProxy.Font.height(null)`（自定义 TrueTypeFont，
 * 随 FontSize 配置变），不能硬编码 9 —— 那是任务完成界面用的原版字体行高。
 *
 * 纯客户端渲染与输入改动，不涉及对话数据、网络包或服务端逻辑。
 */
@Mixin(value = GuiDialogInteract.class, remap = false)
public class MixinGuiDialogInteractTextScroll {

    @Shadow(remap = false)
    private int rowStart;

    @Shadow(remap = false)
    private int rowTotal;

    @Shadow(remap = false)
    private int dialogHeight;

    @Inject(method = "func_73863_a", at = @At("HEAD"), remap = false, require = 1)
    private void cnpcplus$scrollText(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        int wheel = Mouse.getDWheel();
        if (wheel == 0) {
            return;
        }
        int fontHeight = ClientProxy.Font.height(null);
        if (fontHeight <= 0) {
            return;
        }
        // 一格滚轮走 3 行。
        this.rowStart += wheel > 0 ? -3 : 3;
        int max = this.rowTotal - this.dialogHeight / fontHeight;
        if (max < 0) {
            max = 0;
        }
        if (this.rowStart > max) {
            this.rowStart = max;
        }
        if (this.rowStart < 0) {
            this.rowStart = 0;
        }
    }

    /**
     * 可视窗口之外的行不绘制。
     *
     * `drawString(String,int,int,int)` 是 CNPC 自己的私有方法，
     * 是全部对话文本（说话人名 + 正文）的唯一出口，因此一处注入即可
     * 同时挡住上沿溢出（height < 0）与下沿压到选项区（超过 dialogHeight）。
     * 必须写精确描述符，避免与父类 `func_73731_b` 混淆。
     */
    @Inject(method = "drawString(Ljava/lang/String;III)V", at = @At("HEAD"),
            cancellable = true, remap = false, require = 1)
    private void cnpcplus$clipDialogText(String text, int left, int color, int count,
                                         CallbackInfo ci) {
        int height = count - this.rowStart;
        if (height < 0) {
            ci.cancel();
            return;
        }
        int fontHeight = ClientProxy.Font.height(null);
        if (fontHeight > 0 && height * fontHeight >= this.dialogHeight) {
            ci.cancel();
        }
    }
}
