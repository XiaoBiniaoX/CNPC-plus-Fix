package bin.cnpcplus.mixin.text;

import bin.cnpcplus.text.client.SubGuiStyledTextEditor;
import noppes.npcs.client.gui.SubGuiNpcTextArea;
import noppes.npcs.client.gui.util.SubGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubGuiNpcTextArea.class, remap = false)
public abstract class MixinSubGuiStyledTextArea extends SubGuiInterface {
    @Inject(method = "func_73866_w_", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$area(CallbackInfo ci) {
        if (!((Object) this instanceof SubGuiStyledTextEditor)) return;
        super.initGui();
        ((SubGuiStyledTextEditor) (Object) this).buildEditor(); ci.cancel();
    }
}
