package bin.cnpcplus.mixin.text;

import bin.cnpcplus.text.StyledText;
import bin.cnpcplus.text.client.StyledChatMessages;
import bin.cnpcplus.text.client.StyledTextBlock;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import noppes.npcs.client.RenderChatMessages;
import noppes.npcs.client.TextBlockClient;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native bubble lifetime, duplicate suppression, background and depth passes are untouched. */
@Mixin(value = RenderChatMessages.class, remap = false)
public abstract class MixinChatBubbleStyles implements StyledChatMessages {
    @Shadow private Map<Long, TextBlockClient> messages;
    @Unique private StyledText cnpcplus$pending;
    @Unique private int cnpcplus$row;
    @Override public void cnpcplus$addStyled(StyledText value, EntityNPCInterface npc) {
        cnpcplus$pending = value;
        ((RenderChatMessages) (Object) this).addMessage(value.text, npc);
        cnpcplus$pending = null;
    }
    @Redirect(method = "addMessage", at = @At(value = "NEW", target = "noppes/npcs/client/TextBlockClient"), require = 1)
    private TextBlockClient cnpcplus$block(String text, int width, boolean mcFont, Object[] context) {
        return new StyledTextBlock(null, cnpcplus$pending == null ? new StyledText(text, "") : cnpcplus$pending,
                width, 0, true, context);
    }
    @Inject(method = "render(DDDFZ)V", at = @At("HEAD"), require = 1)
    private void cnpcplus$startRows(double x, double y, double z, float scale, boolean depth, CallbackInfo ci) { cnpcplus$row = 0; }
    @Redirect(method = "render(DDDFZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;func_78276_b(Ljava/lang/String;III)I"), require = 1)
    private int cnpcplus$draw(FontRenderer font, String text, int x, int y, int color) {
        int row = cnpcplus$row++;
        for (TextBlockClient block : messages.values()) {
            if (row < block.lines.size()) {
                if (block instanceof StyledTextBlock) {
                    ((StyledTextBlock) block).draw(row, x, y, font.FONT_HEIGHT, color);
                    return x + font.getStringWidth(text);
                }
                break;
            }
            row -= block.lines.size();
        }
        return font.drawString(text, x, y, color);
    }
}
