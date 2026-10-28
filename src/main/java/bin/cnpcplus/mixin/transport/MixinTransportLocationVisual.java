package bin.cnpcplus.mixin.transport;

import bin.cnpcplus.transport.TransportData;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.controllers.data.TransportLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TransportLocation.class, remap = false)
public abstract class MixinTransportLocationVisual {
    @Inject(method = "readNBT", at = @At("TAIL"), require = 1)
    private void cnpcplus$readTransportVisual(NBTTagCompound tag, CallbackInfo ci) { TransportData.setLocation((TransportLocation) (Object) this, tag); }
    @Inject(method = "writeNBT()Lnet/minecraft/nbt/NBTTagCompound;", at = @At("RETURN"), require = 1)
    private void cnpcplus$writeTransportVisual(CallbackInfoReturnable<NBTTagCompound> cir) {
        cir.getReturnValue().merge(TransportData.location((TransportLocation) (Object) this));
    }
}
