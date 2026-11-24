package bin.cnpcplus.mixin.transport;

import bin.cnpcplus.transport.TransportOwnership;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.controllers.data.TransportLocation;
import noppes.npcs.roles.RoleTransporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RoleTransporter.class, remap = false)
public abstract class MixinRoleTransporterOwnership {
    @Inject(method = "transport", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$requireConfirmation(net.minecraft.entity.player.EntityPlayerMP player, String location, CallbackInfo ci) {
        TransportLocation own = ((RoleTransporter)(Object)this).getLocation();
        // Legacy transport packets have no confirmed fare; reopen the custom menu instead.
        if (own != null && bin.cnpcplus.transport.TransportData.location(own).getBoolean("TradeEnabled")) {
            ((RoleTransporter)(Object)this).interact(player);
            ci.cancel();
        }
    }
    @Inject(method = "readFromNBT", at = @At("RETURN"), require = 1)
    private void cnpcplus$loaded(NBTTagCompound tag, CallbackInfo ci) { TransportOwnership.claim((RoleTransporter) (Object) this); }
    @Inject(method = "setTransport", at = @At("RETURN"), require = 1)
    private void cnpcplus$assigned(TransportLocation location, CallbackInfo ci) { TransportOwnership.claim((RoleTransporter) (Object) this); }
}
