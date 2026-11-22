package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import noppes.npcs.controllers.TransportController;
import noppes.npcs.controllers.data.TransportLocation;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.RoleTransporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.transport.TransportFare;

@Mixin(value = TransportController.class, remap = false)
public abstract class MixinTransportLocationFareSave {
    @Inject(method = "saveLocation", at = @At("HEAD"), require = 1)
    private void cnpcplus$keepServerFare(int category, CompoundTag data, ServerPlayer player,
                                         EntityNPCInterface npc, CallbackInfoReturnable<TransportLocation> cir) {
        // The native role page replaces the location using a snapshot from when it opened.
        var location = npc.role instanceof RoleTransporter role ? role.getLocation() : null;
        if (location == null) data.remove(TransportFare.KEY);
        else data.put(TransportFare.KEY, TransportFare.of(location).copy());
    }
}
