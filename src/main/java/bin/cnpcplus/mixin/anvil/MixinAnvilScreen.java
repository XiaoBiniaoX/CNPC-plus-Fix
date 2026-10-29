package bin.cnpcplus.mixin.anvil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 阶段231b：原版铁砧等级文本两条用户反馈（javap AnvilScreen.renderLabels 实证结构）。
 *
 * <p>原逻辑：{@code if (cost > 0) { color=绿; if (cost>=40 && !创造) → TOO_EXPENSIVE_TEXT+橙红;
 * else if (结果空) → 不画; else → "需要cost级"+ (可取 ? 绿 : 红) } cost<=0 → 直接 return}。
 *
 * <ol>
 *   <li><b>敲掉「过于昂贵」</b>：{@code cost>=40} 判定改 {@code >= Integer.MAX_VALUE}（恒假）
 *       → 一律走常规分支：自定义配方 cost=40+ 照常显示「需要X级」（等级够绿/不够红），
 *       结果空不画。原版该档位从红字「过于昂贵!」变无提示（用户要求敲掉；等级不足的
 *       红「需要X级」由常规分支原生给出）。</li>
 *   <li><b>负数经验显示</b>：原版 cost<=0 直接 return（不画）——TAIL 追加同款黑底条+
 *       绿字「本次锻造给予你X级」（X=-cost），结果槽空时不画。</li>
 * </ol>
 */
@Mixin(value = AnvilScreen.class, remap = false)
public class MixinAnvilScreen {

    /**
     * GUI 宽度。原 {@code @Shadow protected int imageWidth} 被日志实证拒绝
     * （本环境 @Shadow 只在目标类自身找字段，imageWidth 声明于父类
     * AbstractContainerScreen → InvalidMixinException 整个 mixin 被丢弃）。
     * vanilla AbstractContainerScreen 构造恒 {@code sipush 176; putfield imageWidth}
     * （javap 实证），AnvilScreen/ItemCombinerScreen 均无覆盖 → 恒 176。
     */
    @Unique
    private static final int CNPCPLUS_GUI_WIDTH = 176;

    /** 敲掉「过于昂贵」阈值：renderLabels 内 40 仅此一处（字节码 bipush 40 实证）。 */
    @ModifyConstant(method = "renderLabels", constant = @Constant(intValue = 40), require = 1, remap = false)
    private int cnpcplus$skipTooExpensive(int original) {
        return Integer.MAX_VALUE;
    }

    /** 负数经验追加显示（cost<0 时原版什么都不画）。 */
    @Inject(method = "renderLabels", at = @At("TAIL"), require = 1, remap = false)
    private void cnpcplus$drawNegativeCost(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        AnvilScreen self = (AnvilScreen) (Object) this;
        AnvilMenu menu = self.getMenu(); // AbstractContainerScreen.getMenu() public，AnvilScreen 的 T=AnvilMenu
        int cost = menu.getCost();
        if (cost >= 0) return;
        if (!menu.getSlot(AnvilMenu.RESULT_SLOT).hasItem()) return;
        Font font = Minecraft.getInstance().font;
        Component text = Component.translatable("cnpcplus.anvil.give", -cost);
        // 坐标/黑底完全复刻 vanilla cost 文本：x=imageWidth-8-text宽-2, fill(x-2,67,-8,79), y=69
        int x = CNPCPLUS_GUI_WIDTH - 8 - font.width(text) - 2;
        graphics.fill(x - 2, 67, CNPCPLUS_GUI_WIDTH - 8, 79, 0x4F000000);
        graphics.drawString(font, text, x, 69, 0x80FF20);
    }
}
