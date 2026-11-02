package bin.cnpcplus.mixin.transport;

import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.transport.PacketTransportMeta;
import bin.cnpcplus.transport.TransportLocationIndex;
import bin.cnpcplus.transport.TransportOwnership;
import java.util.HashMap;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.controllers.TransportController;
import noppes.npcs.controllers.data.TransportCategory;
import noppes.npcs.controllers.data.TransportLocation;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.RoleTransporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TransportController.class, remap = false)
public abstract class MixinTransportControllerIndex {
    @Shadow private HashMap<Integer, TransportLocation> locations;
    @Shadow public HashMap<Integer, TransportCategory> categories;
    @Shadow public abstract void saveCategories();

    @Inject(method = "setLocation", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$set(TransportLocation location, CallbackInfo ci) {
        TransportLocationIndex.put(locations, categories, location); ci.cancel();
    }
    @Redirect(method = "saveLocation", at = @At(value = "INVOKE",
            target = "Ljava/util/HashMap;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"), require = 2)
    private Object cnpcplus$save(HashMap<Integer, TransportLocation> map, Object key, Object value) {
        Object previous = map.get(key);
        TransportLocationIndex.put(locations, categories, (TransportLocation) value);
        return previous;
    }
    @Inject(method = "saveLocation", at = @At("RETURN"), require = 1)
    private void cnpcplus$owner(int categoryId, NBTTagCompound tag, EntityPlayerMP player, EntityNPCInterface npc,
                               CallbackInfoReturnable<TransportLocation> cir) {
        TransportLocation location = cir.getReturnValue();
        if (location == null) return;
        // PacketHandlerServer assigns the role only after this method returns.
        NBTTagCompound meta = bin.cnpcplus.transport.TransportData.location(location);
        if (!meta.hasUniqueId(TransportOwnership.KEY)) {
            meta.setUniqueId(TransportOwnership.KEY, npc.getUniqueID());
            bin.cnpcplus.transport.TransportData.setLocation(location, meta); saveCategories();
        }
    }
    @Inject(method = "removeLocation", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$removeAll(int id, CallbackInfoReturnable<TransportLocation> cir) {
        TransportLocation removed = TransportLocationIndex.remove(locations, categories, id);
        if (removed != null) {
            saveCategories();
            NBTTagCompound update = new NBTTagCompound(); update.setIntArray("Deleted", new int[]{id});
            CraftingViewNetwork.CHANNEL.sendToAll(new PacketTransportMeta(-1, PacketTransportMeta.DELETED, update));
        }
        cir.setReturnValue(removed);
    }
}
