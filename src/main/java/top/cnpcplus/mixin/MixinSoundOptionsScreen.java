package top.cnpcplus.mixin;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.SoundOptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.config.CnpcPlusConfigData;

@Mixin(value = SoundOptionsScreen.class, remap = true)
public class MixinSoundOptionsScreen {

    @Shadow
    private OptionsList list;

    @Inject(method = "init", at = @At("TAIL"))
    private void cnpcplus$addBardSlider(CallbackInfo ci) {
        this.list.addBig(new OptionInstance<Double>("options.cnpcplus.bardVolume",
                OptionInstance.noTooltip(),
                (component, value) -> component.copy().append(Component.literal(": " + Math.round(value * 100) + "%")),
                OptionInstance.UnitDouble.INSTANCE,
                CnpcPlusConfigData.BardVolume.get(),
                value -> CnpcPlusConfigData.BardVolume.set(value)));
    }
}
