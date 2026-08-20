package bin.cnpcplus.mixin.lifecycle;

import bin.cnpcplus.common.MountTargetStore;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import noppes.npcs.PacketHandlerServer;
import noppes.npcs.Server;
import noppes.npcs.constants.EnumPacketServer;
import noppes.npcs.entity.EntityNPCInterface;
import net.minecraft.entity.EntityList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PacketHandlerServer.class, remap = false)
public class MixinPacketHandlerServerMount {
    @Inject(method = "handlePacket", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$handleMount(EnumPacketServer type, ByteBuf buffer, EntityPlayerMP player, EntityNPCInterface npc, CallbackInfo ci) {
        if (type != EnumPacketServer.SpawnRider && type != EnumPacketServer.PlayerRider) {
            return;
        }
        Entity target = MountTargetStore.consume(player);
        if (!cnpcplus$valid(player, target)) {
            ci.cancel();
            return;
        }
        if (type == EnumPacketServer.PlayerRider) {
            player.startRiding(target, true);
            ci.cancel();
            return;
        }
        NBTTagCompound compound;
        try {
            compound = Server.readNBT(buffer);
        } catch (java.io.IOException e) {
            ci.cancel();
            return;
        }
        if (compound == null) {
            ci.cancel();
            return;
        }
        // Strip inherited mount trees so cloned riders cannot duplicate passengers.
        compound.removeTag("Passengers");
        compound.removeTag("Riding");
        Entity rider = EntityList.createEntityFromNBT(compound, (World) player.world);
        if (rider != null && rider != player && rider != target && rider.world == player.world
                && rider.isEntityAlive() && rider.getRidingEntity() == null && !rider.isRiding()) {
            player.world.spawnEntity(rider);
            rider.startRiding(target, true);
        }
        ci.cancel();
    }

    private static boolean cnpcplus$valid(EntityPlayerMP player, Entity target) {
        return target != null && target != player && target.world == player.world
            && player.isEntityAlive() && target.isEntityAlive()
            && target.getRidingEntity() == null;
    }
}
