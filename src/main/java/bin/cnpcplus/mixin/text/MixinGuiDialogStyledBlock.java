package bin.cnpcplus.mixin.text;

import bin.cnpcplus.text.TextStyles;
import bin.cnpcplus.text.client.StyledTextBlock;
import net.minecraft.command.ICommandSender;
import noppes.npcs.client.TextBlockClient;
import noppes.npcs.client.gui.player.GuiDialogInteract;
import noppes.npcs.controllers.data.Dialog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = GuiDialogInteract.class, remap = false)
public abstract class MixinGuiDialogStyledBlock {
    @Shadow private Dialog dialog;
    @Redirect(method = "appendDialog", at = @At(value = "NEW", target = "noppes/npcs/client/TextBlockClient"), require = 1)
    private TextBlockClient cnpcplus$block(ICommandSender speaker, String text, int width, int color, Object[] context) {
        return new StyledTextBlock(speaker, TextStyles.get(dialog, TextStyles.DIALOG, text), width, color, false, context);
    }
}
