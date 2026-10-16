package top.cnpcplus.smithing.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import top.cnpcplus.CnpcPlus;

/**
 * 锻造功能的网络通道（227 数据包补齐，id 0-5 手工分配，改动顺序会破坏与旧版本的协议兼容；
 * PROTOCOL_VERSION 已锁 "1"）。
 * 每个包都显式声明 NetworkDirection：不声明则双向都接受，单人/局域网开房时主机自身也是客户端，
 * 会接受伪造的 PLAY_TO_CLIENT 包（例如用 PacketSmithingSync 覆盖主机的配方缓存）。
 */
public class SmithingPacketHandler {
    private static final String PROTOCOL_VERSION = "1";

    @SuppressWarnings({"deprecation", "removal"})
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(CnpcPlus.MOD_ID, "smithing"))
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .simpleChannel();

    public static void init() {
        final net.minecraftforge.network.NetworkDirection toServer =
                net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER;
        final net.minecraftforge.network.NetworkDirection toClient =
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT;
        CHANNEL.registerMessage(0, PacketOpenSmithingGui.class,
                PacketOpenSmithingGui::encode, PacketOpenSmithingGui::decode, PacketOpenSmithingGui::handle,
                java.util.Optional.of(toServer));
        CHANNEL.registerMessage(1, PacketSmithingSave.class,
                PacketSmithingSave::encode, PacketSmithingSave::decode, PacketSmithingSave::handle,
                java.util.Optional.of(toServer));
        CHANNEL.registerMessage(2, PacketSmithingRemove.class,
                PacketSmithingRemove::encode, PacketSmithingRemove::decode, PacketSmithingRemove::handle,
                java.util.Optional.of(toServer));
        CHANNEL.registerMessage(3, PacketSmithingSync.class,
                PacketSmithingSync::encode, PacketSmithingSync::decode, PacketSmithingSync::handle,
                java.util.Optional.of(toClient));
        CHANNEL.registerMessage(4, PacketSmithingRequestList.class,
                PacketSmithingRequestList::encode, PacketSmithingRequestList::decode, PacketSmithingRequestList::handle,
                java.util.Optional.of(toServer));
        CHANNEL.registerMessage(5, PacketSmithingSelect.class,
                PacketSmithingSelect::encode, PacketSmithingSelect::decode, PacketSmithingSelect::handle,
                java.util.Optional.of(toServer));
    }
}
