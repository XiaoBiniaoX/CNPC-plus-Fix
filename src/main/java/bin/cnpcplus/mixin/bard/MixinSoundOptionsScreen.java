package bin.cnpcplus.mixin.bard;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SoundOptionsScreen.class, remap = false)
public class MixinSoundOptionsScreen {

    @Inject(method = "addOptions", at = @At("TAIL"))
    private void cnpcplus$addBardSlider(CallbackInfo ci) {
        ((OptionsSubScreenAccess) (Object) this).cnpcplus$getList().addBig(new OptionInstance<Double>("options.cnpcplus.bardVolume",
                OptionInstance.noTooltip(),
                (component, value) -> component.copy().append(Component.literal(": " + Math.round(value * 100) + "%")),
                OptionInstance.UnitDouble.INSTANCE,
                CnpcPlusConfig.BARD_VOLUME.get(),
                value -> CnpcPlusConfig.BARD_VOLUME.set(value)));
    }
}
