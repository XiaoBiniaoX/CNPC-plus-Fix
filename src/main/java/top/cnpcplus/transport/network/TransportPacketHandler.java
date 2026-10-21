package top.cnpcplus.transport.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import top.cnpcplus.CnpcPlus;

/**
 * 传送师扩展网络通道（任务241，id 0-4 手工分配，改动顺序会破坏协议兼容；PROTOCOL_VERSION 锁 "1"）。
 * 每个包显式声明 NetworkDirection。
 */
public class TransportPacketHandler {
    private static final String PROTOCOL_VERSION = "1";

    @SuppressWarnings({"deprecation", "removal"})
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(CnpcPlus.MOD_ID, "transport"))
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .simpleChannel();

    public static void init() {
        final net.minecraftforge.network.NetworkDirection toServer =
                net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER;
        final net.minecraftforge.network.NetworkDirection toClient =
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT;
        CHANNEL.registerMessage(0, PacketTransportMetaRequest.class,
                PacketTransportMetaRequest::encode, PacketTransportMetaRequest::decode, PacketTransportMetaRequest::handle,
                java.util.Optional.of(toServer));
        CHANNEL.registerMessage(1, PacketTransportMetaResponse.class,
                PacketTransportMetaResponse::encode, PacketTransportMetaResponse::decode, PacketTransportMetaResponse::handle,
                java.util.Optional.of(toClient));
        CHANNEL.registerMessage(2, PacketTransportImageRequest.class,
                PacketTransportImageRequest::encode, PacketTransportImageRequest::decode, PacketTransportImageRequest::handle,
                java.util.Optional.of(toServer));
        CHANNEL.registerMessage(3, PacketTransportImageResponse.class,
                PacketTransportImageResponse::encode, PacketTransportImageResponse::decode, PacketTransportImageResponse::handle,
                java.util.Optional.of(toClient));
        CHANNEL.registerMessage(4, PacketTransportImageUpload.class,
                PacketTransportImageUpload::encode, PacketTransportImageUpload::decode, PacketTransportImageUpload::handle,
                java.util.Optional.of(toServer));
    }
}
