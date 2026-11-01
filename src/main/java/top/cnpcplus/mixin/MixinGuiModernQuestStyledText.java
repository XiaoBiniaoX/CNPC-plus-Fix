package top.cnpcplus.mixin;

import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.client.ClientProxy;
import noppes.npcs.client.gui.player.moderngui.GuiQuestModern;
import noppes.npcs.controllers.data.Quest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.text.StyledText;
import top.cnpcplus.text.TextStyles;
import top.cnpcplus.text.client.StyledTextRender;

@Mixin(value = GuiQuestModern.class, remap = false)
public class MixinGuiModernQuestStyledText {
    @Shadow @Final private Quest quest;

    @Unique
    private StyledText cnpcplus$text(String text) {
        var self = (GuiQuestModern)(Object)this;
        StyledText value = text.equals(quest.logText) ? TextStyles.get(quest, TextStyles.LOG, text) : new StyledText(text, "");
        return StyledTextRender.format(value, self.player, self.npc);
    }

    @Inject(method = "getLineCount", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$count(String text, int width, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(StyledTextRender.lines(cnpcplus$text(text), width, false).size());
    }

    @Inject(method = "drawTextBlock", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$center(GuiGraphics graphics, String text, int x, int y, int width, int color, CallbackInfo ci) {
        cnpcplus$draw(graphics, text, x, y, width, color, true);
        ci.cancel();
    }

    @Inject(method = "drawLeftAllignedTextBlock", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$left(GuiGraphics graphics, String text, int x, int y, int width, int color, CallbackInfo ci) {
        cnpcplus$draw(graphics, text, x, y, width, color, false);
        ci.cancel();
    }

    @Unique
    private void cnpcplus$draw(GuiGraphics graphics, String text, int x, int y, int width, int color, boolean centered) {
        int row = 0;
        for (var line : StyledTextRender.lines(cnpcplus$text(text), width, false)) {
            StyledTextRender.draw(graphics, line.component(), centered ? x + (width - (int)line.width()) / 2 : x,
                    y + row++ * ClientProxy.Font.height(null), color, false);
        }
    }
}
