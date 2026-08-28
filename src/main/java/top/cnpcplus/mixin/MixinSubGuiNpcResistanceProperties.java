package top.cnpcplus.mixin;

import net.minecraft.client.gui.screens.Screen;
import noppes.npcs.Resistances;
import noppes.npcs.client.gui.SubGuiNpcResistanceProperties;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiSliderNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.data.ExtraDataStorage;

@Mixin(value = SubGuiNpcResistanceProperties.class, remap = false)
public class MixinSubGuiNpcResistanceProperties {

    @Shadow(remap = false)
    private Resistances resistances;

    @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
    private void cnpcplus$addSliders(CallbackInfo ci) {
        SubGuiNpcResistanceProperties self = (SubGuiNpcResistanceProperties) (Object) this;
        float generic = ExtraDataStorage.getFloat(resistances, 5);
        if (generic < 0.0f) generic = 1.0f;
        float magic = ExtraDataStorage.getFloat(resistances, 6);
        if (magic < 0.0f) magic = 1.0f;
        self.addLabel(new GuiLabel(4, "cnpcplus.resist.generic", self.guiLeft + 4, self.guiTop + 103, "cnpcplus.resist.generic.hint"));
        self.addSlider(new GuiSliderNop((Screen) self, 4, self.guiLeft + 94, self.guiTop + 98, (int) (generic * 100.0f - 100.0f) + "%", generic / 2.0f));
        self.addLabel(new GuiLabel(5, "cnpcplus.resist.magic", self.guiLeft + 4, self.guiTop + 125, "cnpcplus.resist.magic.hint"));
        self.addSlider(new GuiSliderNop((Screen) self, 5, self.guiLeft + 94, self.guiTop + 120, (int) (magic * 100.0f - 100.0f) + "%", magic / 2.0f));
    }

    @Inject(method = "mouseDragged", at = @At("TAIL"), remap = false)
    private void cnpcplus$onDrag(GuiSliderNop slider, CallbackInfo ci) {
        if (slider.id == 4 || slider.id == 5) {
            slider.setString((int) (slider.sliderValue * 200.0f - 100.0f) + "%");
        }
    }

    @Inject(method = "mouseReleased", at = @At("TAIL"), remap = false)
    private void cnpcplus$onRelease(GuiSliderNop slider, CallbackInfo ci) {
        if (slider.id == 4) {
            ExtraDataStorage.setFloat(resistances, 5, slider.sliderValue * 2.0f);
        } else if (slider.id == 5) {
            ExtraDataStorage.setFloat(resistances, 6, slider.sliderValue * 2.0f);
        }
    }
}