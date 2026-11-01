package top.cnpcplus.mixin;

import java.util.HashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.controllers.TransportController;
import noppes.npcs.controllers.data.TransportCategory;
import noppes.npcs.controllers.data.TransportLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.transport.TransportLocationIndex;
import top.cnpcplus.transport.TransportOwnership;
import top.cnpcplus.transport.TransportVisualMeta;

@Mixin(value = TransportController.class, remap = false)
public abstract class MixinTransportControllerLocationIndex {
    @Shadow private HashMap<Integer, TransportLocation> locations;
    @Shadow public HashMap<Integer, TransportCategory> categories;
    @Shadow public abstract void saveCategories();

    @Inject(method = "setLocation", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$setLocation(TransportLocation location, CallbackInfo ci) {
        TransportLocationIndex.put(locations, categories, location);
        ci.cancel();
    }

    // Both native put calls now take the same path as setLocation, before saveCategories runs.
    @Redirect(method = "saveLocation", at = @At(value = "INVOKE",
            target = "Ljava/util/HashMap;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"), require = 2)
    private Object cnpcplus$saveLocation(HashMap<Integer, TransportLocation> map, Object key, Object value) {
        Object previous = map.get(key);
        TransportLocationIndex.put(locations, categories, (TransportLocation) value);
        return previous;
    }

    // A stale GUI/clone may carry an ID already removed; native code dereferences global.get(id).name.
    @ModifyVariable(method = "saveLocation", at = @At("HEAD"), argsOnly = true, require = 1)
    private CompoundTag cnpcplus$missingLocationId(CompoundTag data) {
        if (data == null) return data;
        if (data.contains("Id") && locations.containsKey(data.getInt("Id"))) return data;
        CompoundTag copy = data.copy();
        copy.putInt("Id", -1);
        return copy;
    }

    @Inject(method = "saveLocation", at = @At("RETURN"), require = 1)
    private void cnpcplus$assignOwner(int category, CompoundTag tag, ServerPlayer player, EntityNPCInterface npc,
                                     CallbackInfoReturnable<TransportLocation> cir) {
        TransportLocation location = cir.getReturnValue();
        if (location != null && npc != null && TransportOwnership.owner(location) == null) {
            ((TransportVisualMeta) location).cnpcplus$setOwner(npc.getUUID());
            saveCategories();
        }
    }

    @Inject(method = "removeLocation", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$removeEveryIndex(int id, CallbackInfoReturnable<TransportLocation> cir) {
        TransportLocation removed = TransportLocationIndex.remove(locations, categories, id);
        if (removed != null) {
            saveCategories();
            net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                CompoundTag update = new CompoundTag();
                update.putIntArray("Deleted", new int[]{id});
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    top.cnpcplus.transport.network.TransportPacketHandler.CHANNEL.send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                            new top.cnpcplus.transport.network.PacketTransportMetaResponse(update, true));
                }
            }
        }
        cir.setReturnValue(removed);
    }
}
