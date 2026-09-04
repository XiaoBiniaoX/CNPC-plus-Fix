package bin.cnpcplus.mixin.quest;

import net.minecraft.client.gui.FontRenderer;
import noppes.npcs.client.gui.player.GuiQuestCompletion;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让「任务完成文本」界面把文本限制在 UI 内，并支持滚轮上下查看。
 *
 * 现象（哈基彬）：CNPC 的任务完成文本没有滑块，只要文本超出就必定看不见、
 * 看不清。
 *
 * 根因（证据）：`GuiQuestCompletion.drawQuestText()` 的绘制循环是
 * `while (i < block.lines.size())`，**没有任何上界、没有裁剪**，
 * 行 y 直接按 `guiTop + 16 + i * fontHeight` 一路往下画，
 * 超出底图 (ySize=222) 甚至屏幕之外。该类也完全没有滚动字段
 * （整个类只有 quest 和 resource 两个字段）。
 *
 * 参照 CNPC 自己的正确写法：`GuiQuestLog` 对正文用了
 * 「固定可见行数 + 索引偏移 + 边界检查」（maxLines=10 + currentPage）。
 * 这里照同一思路做，不引入 GuiCustomScroll —— 那是**列表**控件，
 * 行高硬编码 14px、且超宽是截断加省略号而非换行，用于段落文本不合适。
 * 也不用 glScissor：CNPC 全代码零使用，限制绘制行数即等价于裁剪且无 GL 风险。
 *
 * 实现两处注入：
 *  1. drawScreen HEAD 读滚轮，调整 @Unique 的行偏移。
 *     选 drawScreen 而不是 handleMouseInput，是因为该类和其父类
 *     GuiNPCInterface 都没有声明 func_146274_d，Mixin 无法注入不存在的方法；
 *     而 CNPC 自己也正是在 drawScreen 里读 Mouse.getDWheel()（见
 *     GuiNPCInterface 给 GuiCustomScroll 喂滚轮的写法），风格一致。
 *     该界面没有注册任何 scroll，所以不存在滚轮值被抢读的问题。
 *  2. @Redirect 掉正文那次 FontRenderer.drawString，按行号过滤：
 *     只画落在可见窗口内的行，并把 y 上移 rowStart 行。
 *
 * 纯客户端渲染与输入改动，不涉及任务数据、网络包或服务端逻辑。
 */
@Mixin(value = GuiQuestCompletion.class, remap = false)
public class MixinGuiQuestCompletionScroll {

    /**
     * 可见行数。
     *
     * 文本区从 guiTop+16 起，底部「完成」按钮顶边在 guiTop + ySize - 24
     * = guiTop + 198，可用高度 182px；原版行高 9px（该界面 TextBlockClient
     * 传 mcFont=true，走原版 FontRenderer，不是 TrueTypeFont）。
     * 182 / 9 = 20，留一行余量取 19，避免末行贴上按钮。
     */
    @Unique
    private static final int CNPCPLUS_VISIBLE_LINES = 19;

    /** 当前滚动到的首行。0 表示从头看（与对话界面停在末屏的语义不同）。 */
    @Unique
    private int cnpcplus$rowStart = 0;

    /** 上一帧统计到的总行数，用于夹紧滚动上限。 */
    @Unique
    private int cnpcplus$totalLines = 0;

    /** 本帧已经绘制到第几行（drawQuestText 的循环计数无法直接取，自己数）。 */
    @Unique
    private int cnpcplus$drawIndex = 0;

    @Inject(method = "func_73863_a", at = @At("HEAD"), remap = false, require = 1)
    private void cnpcplus$scrollInput(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        this.cnpcplus$drawIndex = 0;
        int wheel = Mouse.getDWheel();
        if (wheel != 0) {
            // 一格滚轮走 3 行，手感与原版列表接近。
            this.cnpcplus$rowStart += wheel > 0 ? -3 : 3;
        }
        int max = this.cnpcplus$totalLines - CNPCPLUS_VISIBLE_LINES;
        if (max < 0) {
            max = 0;
        }
        if (this.cnpcplus$rowStart > max) {
            this.cnpcplus$rowStart = max;
        }
        if (this.cnpcplus$rowStart < 0) {
            this.cnpcplus$rowStart = 0;
        }
    }

    /**
     * 接管正文的每一行绘制：窗口外的行不画，窗口内的行整体上移。
     *
     * 目标是 `FontRenderer.drawString(String,int,int,int)`（MCP 名，
     * reobf 自动转 SRG），即反编译里的 `func_78276_b`。
     * 这里同时把总行数数出来，供上面的滚动上限使用。
     */
    @Redirect(
            method = "drawQuestText",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/FontRenderer;func_78276_b(Ljava/lang/String;III)I"),
            remap = false,
            require = 1)
    private int cnpcplus$clipQuestText(FontRenderer font, String text, int x, int y, int color) {
        int index = this.cnpcplus$drawIndex++;
        this.cnpcplus$totalLines = this.cnpcplus$drawIndex;
        if (index < this.cnpcplus$rowStart
                || index >= this.cnpcplus$rowStart + CNPCPLUS_VISIBLE_LINES) {
            return 0;
        }
        return font.drawString(text, x, y - this.cnpcplus$rowStart * font.FONT_HEIGHT, color);
    }
}
