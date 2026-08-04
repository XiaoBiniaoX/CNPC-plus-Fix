package top.cnpcplus.persist.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import top.cnpcplus.craftingview.network.PacketHandler;
import top.cnpcplus.persist.PersistedRecipeStore;

import java.util.ArrayList;
import java.util.function.Supplier;

public class PacketRequestPersistIds {

    public PacketRequestPersistIds() {}

    public void encode(FriendlyByteBuf buf) {}

    public static PacketRequestPersistIds decode(FriendlyByteBuf buf) {
        return new PacketRequestPersistIds();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            PacketHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new PacketSyncPersistIds(new ArrayList<>(PersistedRecipeStore.ids()))
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
