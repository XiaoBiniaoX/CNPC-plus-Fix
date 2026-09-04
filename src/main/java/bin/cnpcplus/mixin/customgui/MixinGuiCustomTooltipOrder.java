package bin.cnpcplus.mixin.customgui;

import net.minecraft.client.gui.inventory.GuiContainer;
import noppes.npcs.client.gui.custom.GuiCustom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.List;

/**
 * 修复脚本自定义 GUI 里物品槽压住 Hover Tooltip 的问题。
 *
 * 现象（哈基彬原话）：用 CNPC 脚本创建 UI 与槽位后，ItemSlot 的渲染层级会
 * 盖在 Hover Tooltip 之上。
 *
 * 根因（证据）：不是 zLevel 问题，是绘制顺序倒置。
 * {@code GuiCustom.drawScreen} 的实际顺序是：
 *   清 hoverText → 暗背景 → 自定义底图 → 遍历组件 onRender
 *   → **画 hoverText** → super.drawScreen
 * 而槽位里的物品是在 {@code super.drawScreen}（原版 GuiContainer）内部画的
 * （背景层 → GuiScreen.drawScreen → drawSlot 循环 → 前景层 → 拖拽物品）。
 * 两段绘制都处于 {@code disableDepth} 状态（GuiContainer 自己关，Forge 的
 * GuiUtils.drawHoveringText 也成对 disable/enable），所以 tooltip 的 z=300
 * 不产生遮挡优先级 —— 唯一决定覆盖关系的就是先后顺序，先画的 tooltip 必然
 * 被后画的槽位物品盖掉。
 *
 * 修法（与上游 CNPC 新版方向一致，新版已把 renderTooltip 移到 super 之后）：
 *  1. @Redirect 掉原来那次过早的 drawHoveringText，只把参数记下来，不绘制。
 *  2. drawScreen 的 TAIL（即 super 调用之后）再画。此时槽位已经画完，
 *     tooltip 成为最后写入帧缓冲的内容，正确压在最上层。
 *
 * 之所以不采用「保留原绘制 + TAIL 再画一次」：那会重复一次半透明背景叠加，
 * 视觉上会加深，也多一次无谓的 GL 状态切换。
 *
 * GL 状态安全：super 返回前已 enableDepth，而 GuiUtils.drawHoveringText
 * 自身成对处理 disable/enable，所以不会泄漏状态。
 *
 * 这是纯客户端渲染顺序修正，不触碰容器、槽位数据或网络包。
 */
@Mixin(value = GuiCustom.class, remap = false)
public abstract class MixinGuiCustomTooltipOrder {

    /** CNPC 自己声明的 public 字段，由各组件在 onRender 中写入。 */
    @Shadow(remap = false)
    public String[] hoverText;

    /** 本帧被推迟的 tooltip 文本；null 表示本帧无需绘制。 */
    @Unique
    private String[] cnpcplus$pendingHover;

    /** 本帧鼠标位置，供 TAIL 补画时使用。 */
    @Unique
    private int cnpcplus$hoverX;

    @Unique
    private int cnpcplus$hoverY;

    /**
     * 拦下 super 之前那次 drawHoveringText，只记录不绘制。
     *
     * 目标所有者以字节码为准：javap 显示 offset 95 处是
     * {@code invokevirtual func_146283_a:(Ljava/util/List;II)V} 且未标注类名，
     * 即所有者就是 GuiCustom 自身（对比 offset 102 明确标了 GuiContainer）。
     * 按本项目既有约定，CNPC 目标类一律写 SRG 名 + remap = false。
     */
    @Redirect(
            method = "func_73863_a",
            at = @At(
                    value = "INVOKE",
                    target = "Lnoppes/npcs/client/gui/custom/GuiCustom;func_146283_a(Ljava/util/List;II)V"),
            remap = false,
            require = 1)
    private void cnpcplus$deferHover(GuiCustom screen, List<String> text, int mouseX, int mouseY) {
        this.cnpcplus$pendingHover = this.hoverText;
        this.cnpcplus$hoverX = mouseX;
        this.cnpcplus$hoverY = mouseY;
    }

    /**
     * super.drawScreen 之后再画 tooltip，使其位于槽位物品之上。
     */
    @Inject(method = "func_73863_a", at = @At("TAIL"), remap = false, require = 1)
    private void cnpcplus$drawHoverOnTop(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        String[] pending = this.cnpcplus$pendingHover;
        this.cnpcplus$pendingHover = null;
        if (pending == null || pending.length == 0) {
            return;
        }
        ((GuiContainer) (Object) this)
                .drawHoveringText(Arrays.asList(pending), this.cnpcplus$hoverX, this.cnpcplus$hoverY);
    }
}
