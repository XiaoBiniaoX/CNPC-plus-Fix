package top.cnpcplus.mixin;

import net.minecraft.server.level.ServerPlayer;
import noppes.npcs.roles.RoleTransporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.transport.TransportFare;

@Mixin(value = RoleTransporter.class, remap = false)
public abstract class MixinTransportFareGuard {
    @Inject(method = "transport", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$requireConfirmedFare(ServerPlayer player, String name, CallbackInfo ci) {
        var source = ((RoleTransporter) (Object) this).getLocation();
        if (source != null && TransportFare.of(source).getBoolean("Enabled")) ci.cancel();
    }
}
