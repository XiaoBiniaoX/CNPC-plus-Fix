package top.cnpcplus.mixin;

import noppes.npcs.shared.client.gui.components.GuiTextArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 暴露 GuiTextArea 私有方法 getSelectionPos(double,double)，供拖选使用。 */
@Mixin(value = GuiTextArea.class, remap = false)
public interface GuiTextAreaInvoker {
    @Invoker("getSelectionPos")
    int cnpcplus$getSelectionPos(double mouseX, double mouseY);
}
