package top.cnpcplus.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.CommandSourceStack;
import java.util.List;
import noppes.npcs.client.TextBlockClient;
import noppes.npcs.client.gui.player.GuiDialogInteract;
import noppes.npcs.controllers.data.Dialog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.text.TextStyles;
import top.cnpcplus.text.client.StyledTextBlock;
import top.cnpcplus.text.client.StyledTextRender;

@Mixin(value = GuiDialogInteract.class, remap = false)
public abstract class MixinGuiDialogStyledText extends noppes.npcs.client.gui.util.GuiNPCInterface {
    @Shadow private Dialog dialog;
    @Shadow private List<TextBlockClient> lines;
    @Shadow private int rowStart;

    @Redirect(method = "appendDialog", at = @At(value = "NEW", target = "(Lnet/minecraft/commands/CommandSourceStack;Ljava/lang/String;II[Ljava/lang/Object;)Lnoppes/npcs/client/TextBlockClient;"), require = 1)
    private TextBlockClient cnpcplus$block(CommandSourceStack sender, String text, int width, int color, Object[] obs) {
        return new StyledTextBlock(sender, TextStyles.get(dialog, TextStyles.DIALOG, text), width, color, obs);
    }

    // The existing scroll redirect invokes this method after clipping. Do not compete with it.
    @Inject(method = "drawString", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$draw(GuiGraphics graphics, String text, int left, int color, int count, CallbackInfo ci) {
        if (left != 0) return; // Native speaker name.
        int index = 0;
        for (TextBlockClient block : lines) {
            if (count < index + block.lines.size()) {
                Component line = block.lines.get(count - index);
                StyledTextRender.draw(graphics, line, guiLeft,
                        guiTop + (count - rowStart) * noppes.npcs.client.ClientProxy.Font.height(null), color, false);
                ci.cancel();
                return;
            }
            index += block.lines.size() + 1;
        }
    }

}
