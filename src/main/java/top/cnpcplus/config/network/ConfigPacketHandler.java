package top.cnpcplus.config.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import top.cnpcplus.CnpcPlus;

import java.util.Optional;

public final class ConfigPacketHandler {
    private static final String PROTOCOL_VERSION = "1";

    @SuppressWarnings({"deprecation", "removal"})
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(CnpcPlus.MOD_ID, "config"))
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .simpleChannel();

    private ConfigPacketHandler() {
    }

    public static void init() {
        CHANNEL.registerMessage(0, PacketUpdateServerConfig.class,
                PacketUpdateServerConfig::encode, PacketUpdateServerConfig::decode, PacketUpdateServerConfig::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, PacketServerConfigSync.class,
                PacketServerConfigSync::encode, PacketServerConfigSync::decode, PacketServerConfigSync::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
