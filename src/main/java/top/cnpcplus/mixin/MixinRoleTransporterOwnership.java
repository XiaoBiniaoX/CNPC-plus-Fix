package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import noppes.npcs.controllers.data.TransportLocation;
import noppes.npcs.packets.server.SPacketDimensionTeleport;
import noppes.npcs.roles.RoleTransporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.transport.TransportOwnership;
import top.cnpcplus.transport.TransportLanding;

@Mixin(value = RoleTransporter.class, remap = false)
public abstract class MixinRoleTransporterOwnership {
    @Inject(method = "load", at = @At("TAIL"), require = 1)
    private void cnpcplus$claimLoaded(CompoundTag tag, CallbackInfo ci) {
        TransportOwnership.claim((RoleTransporter) (Object) this);
    }

    @Inject(method = "setTransport", at = @At("TAIL"), require = 1)
    private void cnpcplus$claimAssigned(TransportLocation location, CallbackInfo ci) {
        TransportOwnership.claim((RoleTransporter) (Object) this);
    }

    @Redirect(method = "transport", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/packets/server/SPacketDimensionTeleport;teleportPlayer(Lnet/minecraft/server/level/ServerPlayer;DDDLnet/minecraft/resources/ResourceKey;)V"), require = 1)
    private void cnpcplus$safeTransport(ServerPlayer player, double x, double y, double z,
                                        ResourceKey<Level> dimension) {
        var destination = TransportLanding.find(player, dimension, net.minecraft.core.BlockPos.containing(x, y, z));
        if (destination.isEmpty()) {
            player.sendSystemMessage(Component.translatable("cnpcplus.transporter.destination.unsafe"));
            return;
        }
        var pos = destination.get();
        SPacketDimensionTeleport.teleportPlayer(player, pos.x, pos.y, pos.z, dimension);
    }
}
