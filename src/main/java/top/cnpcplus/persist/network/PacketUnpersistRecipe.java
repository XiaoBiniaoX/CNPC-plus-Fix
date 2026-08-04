package top.cnpcplus.persist.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import top.cnpcplus.craftingview.network.PacketHandler;
import top.cnpcplus.persist.PersistedRecipeStore;

import java.util.function.Supplier;

public class PacketUnpersistRecipe {

    private final ResourceLocation id;

    public PacketUnpersistRecipe(ResourceLocation id) {
        this.id = id;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(this.id);
    }

    public static PacketUnpersistRecipe decode(FriendlyByteBuf buf) {
        return new PacketUnpersistRecipe(buf.readResourceLocation());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            PersistedRecipeStore.remove(this.id);
            PacketHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new PacketPersistStatus(this.id, false)
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
