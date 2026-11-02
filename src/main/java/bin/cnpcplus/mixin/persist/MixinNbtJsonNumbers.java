package bin.cnpcplus.mixin.persist;

import bin.cnpcplus.data.NbtJsonNumbers;
import noppes.npcs.util.NBTJsonUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = NBTJsonUtil.class, remap = false)
public abstract class MixinNbtJsonNumbers {
    @Redirect(method = "ReadValue", at = @At(value = "INVOKE", target = "Ljava/lang/Double;parseDouble(Ljava/lang/String;)D"), require = 2)
    private static double cnpcplus$double(String token) { return NbtJsonNumbers.parseDouble(token); }
    @Redirect(method = "ReadValue", at = @At(value = "INVOKE", target = "Ljava/lang/Float;parseFloat(Ljava/lang/String;)F"), require = 1)
    private static float cnpcplus$float(String token) { return NbtJsonNumbers.parseFloat(token); }
}
