package top.cnpcplus.invpage.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.network.NetworkEvent;
import noppes.npcs.containers.ContainerNPCInv;
import noppes.npcs.entity.data.DataInventory;
import top.cnpcplus.invpage.DropPageStore;

import java.util.function.Supplier;

public class PacketNpcInvPage {
    private final int page;

    public PacketNpcInvPage(int page) {
        this.page = page;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.page);
    }

    public static PacketNpcInvPage decode(FriendlyByteBuf buf) {
        return new PacketNpcInvPage(buf.readInt());
    }

    public static void handle(PacketNpcInvPage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!(player.containerMenu instanceof ContainerNPCInv menu)) return;
            Slot slot = menu.getSlot(4);
            if (slot != null && slot.container instanceof DataInventory inv) {
                DropPageStore.set(inv, msg.page);
                menu.broadcastChanges();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
