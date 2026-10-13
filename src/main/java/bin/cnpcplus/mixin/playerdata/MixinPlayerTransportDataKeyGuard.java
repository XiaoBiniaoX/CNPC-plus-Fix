package bin.cnpcplus.mixin.playerdata;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.data.PlayerTransportData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PlayerTransportData.loadNBTData 键缺失保护，防止传送点被空集合覆盖。
 */
@Mixin(value = PlayerTransportData.class, remap = false)
public class MixinPlayerTransportDataKeyGuard {

    @Inject(method = "loadNBTData", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$guardMissingKey(CompoundTag compound, CallbackInfo ci) {
        if (compound == null) return;
        if (!compound.contains("TransportData")) {
            ci.cancel();
        }
    }
}
