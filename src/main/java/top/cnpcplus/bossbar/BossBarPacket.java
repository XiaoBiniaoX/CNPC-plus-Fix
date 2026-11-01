package top.cnpcplus.bossbar;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Server-only color authority; progress/name/visibility remain vanilla boss packets. */
public record BossBarPacket(UUID id, int rgb, boolean removed) {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("cnpcplus", "bossbar"), () -> "1", "1"::equals, "1"::equals);

    public static void init() {
        CHANNEL.registerMessage(0, BossBarPacket.class, BossBarPacket::encode, BossBarPacket::decode,
                BossBarPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeInt(rgb & 0xFFFFFF);
        buffer.writeBoolean(removed);
    }

    private static BossBarPacket decode(FriendlyByteBuf buffer) {
        return new BossBarPacket(buffer.readUUID(), buffer.readInt() & 0xFFFFFF, buffer.readBoolean());
    }

    private static void handle(BossBarPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> top.cnpcplus.bossbar.client.BossBarColors.receive(packet)));
        context.get().setPacketHandled(true);
    }

    static void send(ServerPlayer player, UUID id, int rgb, boolean removed) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BossBarPacket(id, rgb, removed));
    }
}
