package bin.cnpcplus.mixin.text;

import bin.cnpcplus.text.TextStyles;
import bin.cnpcplus.text.client.StyledTextBlock;
import net.minecraft.client.Minecraft;
import noppes.npcs.client.CustomNpcResourceListener;
import noppes.npcs.client.TextBlockClient;
import noppes.npcs.client.gui.player.GuiQuestLog;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.controllers.data.Quest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiQuestLog.class, remap = false)
public abstract class MixinGuiQuestLogStyles {
    @Shadow public Quest selectedQuest;
    @Shadow private int currentPage;
    @Shadow private int maxPages;
    @Shadow TextBlockClient textblock;
    @Redirect(method = "scrollClicked", at = @At(value = "NEW", target = "noppes/npcs/client/TextBlockClient"), require = 1)
    private TextBlockClient cnpcplus$block(String text, int width, boolean mcFont, Object[] context) {
        StyledTextBlock block = new StyledTextBlock(null, TextStyles.get(selectedQuest, TextStyles.LOG, text), width,
                CustomNpcResourceListener.DefaultTextColor, mcFont, context);
        maxPages = Math.max(1, (block.lines.size() + 9) / 10); return block;
    }
    @Inject(method = "drawQuestText", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$draw(CallbackInfo ci) {
        if (!(textblock instanceof StyledTextBlock)) return;
        GuiNPCInterface gui = (GuiNPCInterface) (Object) this; int height = Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT;
        for (int row = 0; row < 10 && currentPage * 10 + row < textblock.lines.size(); row++)
            ((StyledTextBlock) textblock).draw(currentPage * 10 + row, gui.guiLeft + 142, gui.guiTop + 20 + row * height,
                    height, CustomNpcResourceListener.DefaultTextColor);
        ci.cancel();
    }
}
