package bin.cnpcplus.mixin.text;

import bin.cnpcplus.text.client.StyledTextArea;
import noppes.npcs.client.gui.util.GuiTextArea;
import noppes.npcs.config.TrueTypeFont;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = GuiTextArea.class, remap = false)
public abstract class MixinGuiTextAreaStyles {
    @Redirect(method = "drawScreen", at = @At(value = "INVOKE", target = "Lnoppes/npcs/config/TrueTypeFont;draw(Ljava/lang/String;FFI)V"), require = 1)
    private void cnpcplus$paint(TrueTypeFont font, String text, float x, float y, int color) {
        if ((Object) this instanceof StyledTextArea) ((StyledTextArea) (Object) this).drawNativeRow(font, (int) x, (int) y, color);
        else font.draw(text, x, y, color);
    }
}
