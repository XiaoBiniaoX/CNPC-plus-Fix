package bin.cnpcplus.mixin.transport;

import bin.cnpcplus.transport.TransportOwnership;
import noppes.npcs.controllers.TransportController;
import noppes.npcs.controllers.data.TransportLocation;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.RoleTransporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinEntityNPCTransportDelete {
    @Inject(method = "delete", at = @At("HEAD"), require = 1)
    private void cnpcplus$deletePoint(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (npc.world.isRemote || !(npc.roleInterface instanceof RoleTransporter)) return;
        TransportLocation location = ((RoleTransporter) npc.roleInterface).getLocation();
        if (location != null && npc.getUniqueID().equals(TransportOwnership.owner(location)))
            TransportController.getInstance().removeLocation(location.id);
    }
}
