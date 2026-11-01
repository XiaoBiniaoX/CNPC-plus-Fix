package top.cnpcplus.mixin;

import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.client.ClientProxy;
import noppes.npcs.client.gui.player.moderngui.GuiDialogModern;
import noppes.npcs.controllers.data.Dialog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.text.TextStyles;
import top.cnpcplus.text.StyledText;
import top.cnpcplus.text.client.StyledTextRender;

@Mixin(value = GuiDialogModern.class, remap = false)
public class MixinGuiModernStyledText {
    @Shadow private Dialog dialog;

    @Inject(method = "getLineCount", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$count(String text, int width, CallbackInfoReturnable<Integer> cir) {
        var self = (GuiDialogModern)(Object)this;
        cir.setReturnValue(StyledTextRender.lines(StyledTextRender.format(text.equals(dialog.text) ? TextStyles.get(dialog, TextStyles.DIALOG, text) : new StyledText(text, ""), self.player, self.npc), width, false).size());
    }

    @Inject(method = "drawTextBlock", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$draw(GuiGraphics graphics, String text, int x, int y, int width, CallbackInfo ci) {
        var self = (GuiDialogModern)(Object)this;
        int row = 0;
        for (var line : StyledTextRender.lines(StyledTextRender.format(text.equals(dialog.text) ? TextStyles.get(dialog, TextStyles.DIALOG, text) : new StyledText(text, ""), self.player, self.npc), width, false)) {
            StyledTextRender.draw(graphics, line.component(), x + (width - (int)line.width()) / 2, y + row++ * ClientProxy.Font.height(null), -1, false);
        }
        ci.cancel();
    }
}
