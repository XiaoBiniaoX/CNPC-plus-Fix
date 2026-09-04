package bin.cnpcplus.mixin.lines;

import bin.cnpcplus.lines.MeleeHitLinesAccess;
import noppes.npcs.client.NoppesUtil;
import noppes.npcs.client.gui.GuiNPCLinesEdit;
import noppes.npcs.client.gui.advanced.GuiNPCLinesMenu;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在高级台词菜单中新增"近战打击"台词按钮，并响应点击事件打开编辑界面。
 */
@Mixin(value = GuiNPCLinesMenu.class, remap = false)
public class MixinGuiNPCLinesMenuMeleeHit {

    /**
     * 在 init 尾部新增近战打击按钮（id=17），其它按钮下移以避免重叠。
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void cnpcplus$addMeleeHitButton(CallbackInfo ci) {
        GuiNPCLinesMenu self = (GuiNPCLinesMenu) (Object) this;

        // 将现有 random 标签（id=16）与按钮（id=16）下移 24px，避免与新按钮重叠。
        net.minecraft.client.gui.components.AbstractWidget labelWidget = self.getLabel(16);
        if (labelWidget != null) {
            labelWidget.setY(labelWidget.getY() + 24);
        }
        GuiButtonNop randomButton = self.getButton(16);
        if (randomButton != null) {
            ((net.minecraft.client.gui.components.AbstractWidget) randomButton).setY(
                    ((net.minecraft.client.gui.components.AbstractWidget) randomButton).getY() + 24
            );
        }

        // 新增 id=17 近战打击按钮。
        self.addButton(
                new GuiButtonNop(
                        (IGuiInterface) self,
                        17,
                        self.guiLeft + 85,
                        self.guiTop + 156,
                        "cnpcplus.lines.meleeHit"
                )
        );
    }

    /**
     * 拦截 buttonEvent，处理 id=17（近战打击台词按钮）点击。
     */
    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$openMeleeHitEditor(GuiButtonNop button, CallbackInfo ci) {
        if (button.id == 17) {
            GuiNPCLinesMenu self = (GuiNPCLinesMenu) (Object) this;
            // 打开近战打击台词编辑界面。
            NoppesUtil.openGUI(
                    self.player,
                    new GuiNPCLinesEdit(
                            self.npc,
                            ((MeleeHitLinesAccess) self.npc.advanced).cnpcplus$getMeleeHitLines()
                    )
            );
            ci.cancel();
        }
    }
}
