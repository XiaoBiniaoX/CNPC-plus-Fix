package bin.cnpcplus.mixin.bard;

import bin.cnpcplus.config.BardVolumeSlider;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreenOptionsSounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.lang.reflect.Field;
import java.util.List;

@Mixin(GuiScreenOptionsSounds.class)
public class MixinGuiScreenOptionsSounds {
    private static final Field CNPCPLUS_BUTTONS = cnpcplus$findButtons();

    private static Field cnpcplus$findButtons() {
        try {
            Field field = net.minecraft.client.gui.GuiScreen.class.getDeclaredField("field_146292_n");
            field.setAccessible(true);
            return field;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Inject(method = "initGui", at = @At("TAIL"))
    private void cnpcplus$bardVolumeSlider(CallbackInfo ci) {
        if (CNPCPLUS_BUTTONS == null) return;
        GuiScreenOptionsSounds self = (GuiScreenOptionsSounds) (Object) this;
        try {
            @SuppressWarnings("unchecked")
            List<GuiButton> buttons = (List<GuiButton>) CNPCPLUS_BUTTONS.get(self);
            buttons.add(new BardVolumeSlider(209, self.width / 2 + 5, self.height / 6 + 108));
        } catch (IllegalAccessException ignored) {
        }
    }
}
