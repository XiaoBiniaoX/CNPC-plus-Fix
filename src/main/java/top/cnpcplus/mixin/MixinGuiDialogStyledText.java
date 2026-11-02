package top.cnpcplus.mixin;

import net.minecraft.commands.CommandSourceStack;
import noppes.npcs.client.TextBlockClient;
import noppes.npcs.client.gui.player.GuiDialogInteract;
import noppes.npcs.controllers.data.Dialog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.cnpcplus.text.TextStyles;
import top.cnpcplus.text.client.StyledTextBlock;

@Mixin(value = GuiDialogInteract.class, remap = false)
public abstract class MixinGuiDialogStyledText {
    @Shadow private Dialog dialog;

    @Redirect(method = "appendDialog", at = @At(value = "NEW", target = "(Lnet/minecraft/commands/CommandSourceStack;Ljava/lang/String;II[Ljava/lang/Object;)Lnoppes/npcs/client/TextBlockClient;"), require = 1)
    private TextBlockClient cnpcplus$block(CommandSourceStack sender, String text, int width, int color, Object[] obs) {
        var value = TextStyles.get(dialog, TextStyles.DIALOG, text);
        if (value.spans().isEmpty()) value = new top.cnpcplus.text.StyledText(
                bin.cnpcplus.util.FormatUtil.applyDefault(text, bin.cnpcplus.config.CnpcPlusConfig.DIALOG_TEXT_FORMAT.get()), "");
        return new StyledTextBlock(sender, value, width, color, obs);
    }
}
