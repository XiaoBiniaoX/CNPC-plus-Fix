package top.cnpcplus.mixin;

import noppes.npcs.controllers.TransportController;
import noppes.npcs.controllers.data.TransportLocation;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.RoleTransporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.transport.TransportOwnership;

@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinEntityNPCTransportDelete {
    // Explicit deletion only; chunk unload and respawnable death retain their location.
    @Inject(method = "delete", at = @At("HEAD"), require = 1)
    private void cnpcplus$deleteOwnedTransport(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (npc.isClientSide() || !(npc.role instanceof RoleTransporter role)) return;
        TransportOwnership.claim(role);
        TransportLocation location = role.getLocation();
        if (location != null && TransportOwnership.owns(TransportOwnership.owner(location), npc.getUUID())) {
            TransportController.getInstance().removeLocation(location.id);
        }
    }
}
