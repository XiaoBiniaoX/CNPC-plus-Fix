package bin.cnpcplus.mixin.stats;

import bin.cnpcplus.common.IResistanceExtendedAccess;
import net.minecraft.client.gui.GuiScreen;
import noppes.npcs.Resistances;
import noppes.npcs.client.gui.SubGuiNpcResistanceProperties;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.client.gui.util.GuiNpcSlider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubGuiNpcResistanceProperties.class, remap = false)
public abstract class MixinSubGuiNpcResistancePropertiesExtended {

    @Shadow(remap = false)
    private Resistances resistances;

    @Inject(method = "func_73866_w_", at = @At("TAIL"))
    private void cnpcplus$onInitGui(CallbackInfo ci) {
        SubGuiNpcResistanceProperties self = (SubGuiNpcResistanceProperties) (Object) this;
        IResistanceExtendedAccess ext = (IResistanceExtendedAccess) this.resistances;
        int yBase = 4 + 22 * 4;
        self.addLabel(new GuiNpcLabel(4, "cnpcplus.resistance.generic", self.guiLeft + 4, self.guiTop + 15 + yBase));
        self.addSlider(new GuiNpcSlider((GuiScreen) self, 4, self.guiLeft + 94, self.guiTop + 10 + yBase,
                (int) (ext.cnpcplus$getGenericDamage() * 100.0f - 100.0f) + "%", ext.cnpcplus$getGenericDamage() / 2.0f));
        self.addLabel(new GuiNpcLabel(5, "cnpcplus.resistance.magic", self.guiLeft + 4, self.guiTop + 37 + yBase));
        self.addSlider(new GuiNpcSlider((GuiScreen) self, 5, self.guiLeft + 94, self.guiTop + 32 + yBase,
                (int) (ext.cnpcplus$getMagicDamage() * 100.0f - 100.0f) + "%", ext.cnpcplus$getMagicDamage() / 2.0f));
    }

    @Inject(method = "mouseDragged", at = @At("TAIL"))
    private void cnpcplus$onMouseDragged(GuiNpcSlider slider, CallbackInfo ci) {
        if (slider.field_146127_k == 4 || slider.field_146127_k == 5) {
            slider.displayString = (int) (slider.sliderValue * 200.0f - 100.0f) + "%";
        }
    }

    @Inject(method = "mouseReleased", at = @At("TAIL"))
    private void cnpcplus$onMouseReleased(GuiNpcSlider slider, CallbackInfo ci) {
        IResistanceExtendedAccess ext = (IResistanceExtendedAccess) this.resistances;
        if (slider.field_146127_k == 4) {
            ext.cnpcplus$setGenericDamage(slider.sliderValue * 2.0f);
        }
        if (slider.field_146127_k == 5) {
            ext.cnpcplus$setMagicDamage(slider.sliderValue * 2.0f);
        }
    }
}
