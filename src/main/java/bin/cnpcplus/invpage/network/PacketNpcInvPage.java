package bin.cnpcplus.invpage.network;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.invpage.DropPageStore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import noppes.npcs.containers.ContainerNPCInv;
import noppes.npcs.entity.data.DataInventory;

public record PacketNpcInvPage(int page) implements CustomPacketPayload {
    public static final Type<PacketNpcInvPage> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "npcinv_page"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketNpcInvPage> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, PacketNpcInvPage::page, PacketNpcInvPage::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketNpcInvPage msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof ContainerNPCInv menu)) return;
            Slot slot = menu.getSlot(4);
            if (slot != null && slot.container instanceof DataInventory inv) {
                DropPageStore.set(inv, msg.page);
                menu.broadcastChanges();
            }
        });
    }
}
