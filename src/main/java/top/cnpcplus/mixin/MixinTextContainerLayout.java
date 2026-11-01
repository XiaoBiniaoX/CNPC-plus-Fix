package top.cnpcplus.mixin;

import java.util.List;
import noppes.npcs.shared.client.gui.components.TextContainer;
import noppes.npcs.shared.client.util.TrueTypeFont;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.text.client.NativeTextLayout;

@Mixin(value = TextContainer.class, remap = false)
public class MixinTextContainerLayout {
    @Shadow private TrueTypeFont font;
    @Shadow public List lines;
    @Shadow public int lineHeight;
    @Shadow public int linesCount;
    @Shadow public int totalHeight;
    @Shadow public int visibleLines;

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$wrap(TrueTypeFont font, int width, int height, CallbackInfo ci) {
        this.font = font;
        TextContainer self = (TextContainer)(Object)this;
        lineHeight = Math.max(1, font.height(self.text));
        lines = NativeTextLayout.lines(self, font, width);
        linesCount = lines.size();
        totalHeight = linesCount * lineHeight;
        visibleLines = Math.max(1, height / lineHeight);
        ci.cancel();
    }
}
