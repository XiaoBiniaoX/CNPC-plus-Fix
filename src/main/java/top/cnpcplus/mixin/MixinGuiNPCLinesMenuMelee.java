package top.cnpcplus.mixin;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.client.NoppesUtil;
import noppes.npcs.client.gui.GuiNPCLinesEdit;
import noppes.npcs.client.gui.advanced.GuiNPCLinesMenu;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.lines.MeleeLinesStorage;

/**
 * 在台词菜单里加一个「近战打击」入口，界面与其他六类台词完全一致。
 *
 * <p>按钮 id 用 3：原版这个菜单的 id 是 0/1/2/5/6/7，**3 和 4 是空洞**
 * （原版源码实证），所以占用 3 不与任何原版分支冲突，也不需要改动原版按钮。
 *
 * <p>布局：原版最后一个按钮（npcinteract）在 guiTop+135，随机开关标签在 +157、
 * 按钮在 +152。直接插在 +135 之后会压到随机开关上，所以本按钮放在 +158，
 * 并把随机开关的标签与按钮各下移 23px（与原版 23px 行距一致），保持视觉节奏。
 * 按钮最终放在 +156：比原始插入位置 +158 上移 2px，避免贴近随机开关。
 *
 * <p>点击后打开原版的 {@code GuiNPCLinesEdit}，传入我们旁挂的 Lines 对象 ——
 * 编辑器本身完全复用，8 槽文本 + 音效选择 + 保存链路全部沿用原版实现。
 * 保存走原版 {@code save()} 里的 {@code SPacketMenuSave(ADVANCED, advanced.save(...))}，
 * 而 {@code MixinDataAdvancedMeleeLines} 已在 save 里补写了 NBT 键，所以自动落盘。
 */
@Mixin(value = GuiNPCLinesMenu.class, remap = false)
public class MixinGuiNPCLinesMenuMelee {

    private static final int BTN_MELEE = 3;

    @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
    private void cnpcplus$addMeleeButton(CallbackInfo ci) {
        GuiNPCLinesMenu self = (GuiNPCLinesMenu) (Object) this;

        // 把随机开关整体下移一行，给新按钮腾出位置。
        // getLabel/getButton 取的是原版刚创建的对象，直接改坐标比重建更稳。
        // 走 AbstractWidget.setY 而不是访问字段：GuiLabel/GuiButtonNop 都继承
        // AbstractWidget，坐标字段是 private，这是项目里既有的做法（见 MixinGuiNpcBard）。
        GuiLabel randomLabel = self.getLabel(16);
        if (randomLabel != null) {
            AbstractWidget w = randomLabel;
            w.setY(w.getY() + 23);
        }
        GuiButtonNop random = self.getButton(16);
        if (random != null) {
            AbstractWidget w = random;
            w.setY(w.getY() + 23);
        }

        // 比原始插入坐标 +158 上移 2px，和下方随机开关留出更舒适的间距。
        self.addButton(new GuiButtonNop((IGuiInterface) self, BTN_MELEE,
                self.guiLeft + 85, self.guiTop + 156, "cnpcplus.lines.melee"));
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), remap = false)
    private void cnpcplus$openMeleeEditor(GuiButtonNop guibutton, CallbackInfo ci) {
        if (guibutton.id != BTN_MELEE) return;
        GuiNPCLinesMenu self = (GuiNPCLinesMenu) (Object) this;
        NoppesUtil.openGUI((Player) self.player,
                new GuiNPCLinesEdit(self.npc, MeleeLinesStorage.get(self.npc.advanced)));
    }
}
