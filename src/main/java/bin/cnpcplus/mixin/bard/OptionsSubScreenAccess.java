package bin.cnpcplus.mixin.bard;

import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = OptionsSubScreen.class, remap = false)
public interface OptionsSubScreenAccess {

    @Accessor("list")
    OptionsList cnpcplus$getList();
}
