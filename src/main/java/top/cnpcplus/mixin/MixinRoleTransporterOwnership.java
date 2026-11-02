package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.data.TransportLocation;
import noppes.npcs.roles.RoleTransporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.transport.TransportOwnership;

@Mixin(value = RoleTransporter.class, remap = false)
public abstract class MixinRoleTransporterOwnership {
    @Inject(method = "load", at = @At("TAIL"), require = 1)
    private void cnpcplus$claimLoaded(CompoundTag tag, CallbackInfo ci) { TransportOwnership.claim((RoleTransporter) (Object) this); }

    @Inject(method = "setTransport", at = @At("TAIL"), require = 1)
    private void cnpcplus$claimAssigned(TransportLocation location, CallbackInfo ci) { TransportOwnership.claim((RoleTransporter) (Object) this); }
}
