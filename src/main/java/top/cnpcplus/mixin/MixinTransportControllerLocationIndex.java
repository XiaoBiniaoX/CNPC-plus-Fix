package top.cnpcplus.mixin;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import noppes.npcs.controllers.TransportController;
import noppes.npcs.controllers.data.TransportCategory;
import noppes.npcs.controllers.data.TransportLocation;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.RoleTransporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.transport.TransportLocationIndex;
import top.cnpcplus.transport.TransportOwnership;
import top.cnpcplus.transport.TransportVisualMeta;
import top.cnpcplus.transport.network.PacketTransportMetaResponse;

@Mixin(value = TransportController.class, remap = false)
public abstract class MixinTransportControllerLocationIndex {
    @Shadow private HashMap<Integer, TransportLocation> locations;
    @Shadow public HashMap<Integer, TransportCategory> categories;
    @Shadow public abstract void saveCategories();
    @Shadow public abstract boolean containsLocationName(String name);
    @Shadow private int getUniqueIdLocation() { throw new AssertionError(); }

    @Inject(method = "setLocation", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$setLocation(TransportLocation location, CallbackInfo ci) {
        TransportLocationIndex.put(locations, categories, location);
        ci.cancel();
    }

    @Inject(method = "saveLocation", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$saveLocation(int categoryId, CompoundTag tag, ServerPlayer player, EntityNPCInterface npc,
                                      CallbackInfoReturnable<TransportLocation> cir) {
        TransportCategory category = categories.get(categoryId);
        if (category == null || tag == null || npc == null || !(npc.role instanceof RoleTransporter role)) {
            cir.setReturnValue(null);
            return;
        }
        TransportLocation previous = role.getLocation();
        UUID owner = previous == null ? null : TransportOwnership.owner(previous);
        TransportLocation location = new TransportLocation();
        location.readNBT(tag);
        location.category = category;
        // The role, not a stale GUI's ID/Owner, decides whether this is an edit or a clone fork.
        location.id = previous != null && (owner == null || owner.equals(npc.getUUID())) ? previous.id : -1;
        if (location.id < 0 || !previous.name.equals(location.name)) {
            while (containsLocationName(location.name)) location.name += "_";
        }
        if (location.id < 0) location.id = getUniqueIdLocation();
        ((TransportVisualMeta) location).cnpcplus$setOwner(npc.getUUID());
        TransportLocationIndex.put(locations, categories, location);
        saveCategories();
        cir.setReturnValue(location);
    }

    @Inject(method = "removeLocation", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$removeEveryIndex(int id, CallbackInfoReturnable<TransportLocation> cir) {
        TransportLocation removed = TransportLocationIndex.remove(locations, categories, id);
        if (removed != null) saveCategories();
        cnpcplus$notifyDeleted(new int[]{id});
        cir.setReturnValue(removed);
    }

    @Inject(method = "removeCategory", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$removeCategory(int id, CallbackInfo ci) {
        ci.cancel();
        TransportCategory category = categories.get(id);
        if (categories.size() == 1 || category == null) return;
        var ids = new LinkedHashSet<>(category.locations.keySet());
        for (TransportLocation location : locations.values()) {
            if (location.category == category) ids.add(location.id);
        }
        for (int location : ids) TransportLocationIndex.remove(locations, categories, location);
        categories.remove(id);
        saveCategories();
        cnpcplus$notifyDeleted(ids.stream().mapToInt(Integer::intValue).toArray());
    }

    @Unique private void cnpcplus$notifyDeleted(int[] ids) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || ids.length == 0) return;
        CompoundTag update = new CompoundTag();
        update.putIntArray("Deleted", ids);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (NetworkRegistry.hasChannel(player.connection, PacketTransportMetaResponse.TYPE.id())) {
                PacketDistributor.sendToPlayer(player, new PacketTransportMetaResponse(update, true));
            }
        }
    }
}
