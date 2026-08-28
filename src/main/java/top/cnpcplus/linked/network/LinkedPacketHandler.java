package top.cnpcplus.linked.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import top.cnpcplus.CnpcPlus;

public class LinkedPacketHandler {
    private static final String PROTOCOL_VERSION = "1";

    @SuppressWarnings({"deprecation", "removal"})
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(CnpcPlus.MOD_ID, "linked"))
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .simpleChannel();

    public static void init() {
        var toServer = net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER;
        var toClient = net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT;
        CHANNEL.registerMessage(0, PacketLinkedToggleSync.class,
                PacketLinkedToggleSync::encode, PacketLinkedToggleSync::decode, PacketLinkedToggleSync::handle, java.util.Optional.of(toServer));
        CHANNEL.registerMessage(1, PacketLinkedSyncStatus.class,
                PacketLinkedSyncStatus::encode, PacketLinkedSyncStatus::decode, PacketLinkedSyncStatus::handle, java.util.Optional.of(toClient));
        CHANNEL.registerMessage(2, PacketLinkedRequestSyncStatus.class,
                PacketLinkedRequestSyncStatus::encode, PacketLinkedRequestSyncStatus::decode, PacketLinkedRequestSyncStatus::handle, java.util.Optional.of(toServer));
    }
}