package bin.cnpcplus.mixin.puppet;

import noppes.npcs.entity.data.DataDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = DataDisplay.class, remap = false)
public class MixinDataDisplaySize {

    @Redirect(method = "setSize", at = @At(value = "INVOKE", target = "Lnoppes/npcs/util/ValueUtil;CorrectInt(III)I"))
    private int cnpcplus$extendSizeLimit(int size, int min, int max) {
        if (size < 1) return 1;
        if (size > 100) return 100;
        return size;
    }

    @Redirect(method = "readToNBT", at = @At(value = "INVOKE", target = "Lnoppes/npcs/util/ValueUtil;CorrectInt(III)I"))
    private int cnpcplus$extendSizeLimitRead(int size, int min, int max) {
        if (size < 1) return 1;
        if (size > 100) return 100;
        return size;
    }
}
