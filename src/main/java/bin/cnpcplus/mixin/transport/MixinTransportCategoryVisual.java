package bin.cnpcplus.mixin.transport;

import bin.cnpcplus.transport.TransportData;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.controllers.data.TransportCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TransportCategory.class, remap = false)
public abstract class MixinTransportCategoryVisual {
    @Inject(method = "readNBT", at = @At("TAIL"), require = 1)
    private void cnpcplus$readTransportPanel(NBTTagCompound tag, CallbackInfo ci) { TransportData.setPanel((TransportCategory) (Object) this, tag.getCompoundTag("cnpcplusPanel")); }
    @Inject(method = "writeNBT", at = @At("TAIL"), require = 1)
    private void cnpcplus$writeTransportPanel(NBTTagCompound tag, CallbackInfo ci) { tag.setTag("cnpcplusPanel", TransportData.panel((TransportCategory) (Object) this)); }
}
