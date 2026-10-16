package top.cnpcplus.drop.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import top.cnpcplus.CnpcPlus;

/**
 * 掉落物配方编辑网络通道（阶段232，id 0-5 手工分配，改动顺序会破坏与旧版本的协议兼容；
 * PROTOCOL_VERSION 已锁 "1"）。通道构造与方向声明照抄 AnvilPacketHandler。
 * 每个包都显式声明 NetworkDirection：不声明则双向都接受，单人/局域网开房时主机自身也是客户端，
 * 会接受伪造的 PLAY_TO_CLIENT 包（例如用 PacketDropSync 覆盖主机的配方缓存）。
 */
public class DropPacketHandler {
    private static final String PROTOCOL_VERSION = "1";

    @SuppressWarnings({"deprecation", "removal"})
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(CnpcPlus.MOD_ID, "drop"))
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .simpleChannel();

    public static void init() {
        final net.minecraftforge.network.NetworkDirection toServer =
                net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER;
        final net.minecraftforge.network.NetworkDirection toClient =
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT;
        CHANNEL.registerMessage(0, PacketOpenDropGui.class,
                PacketOpenDropGui::encode, PacketOpenDropGui::decode, PacketOpenDropGui::handle,
                java.util.Optional.of(toServer));
        CHANNEL.registerMessage(1, PacketDropSave.class,
                PacketDropSave::encode, PacketDropSave::decode, PacketDropSave::handle,
                java.util.Optional.of(toServer));
        CHANNEL.registerMessage(2, PacketDropRemove.class,
                PacketDropRemove::encode, PacketDropRemove::decode, PacketDropRemove::handle,
                java.util.Optional.of(toServer));
        CHANNEL.registerMessage(3, PacketDropSync.class,
                PacketDropSync::encode, PacketDropSync::decode, PacketDropSync::handle,
                java.util.Optional.of(toClient));
        CHANNEL.registerMessage(4, PacketDropRequestList.class,
                PacketDropRequestList::encode, PacketDropRequestList::decode, PacketDropRequestList::handle,
                java.util.Optional.of(toServer));
        CHANNEL.registerMessage(5, PacketDropSelect.class,
                PacketDropSelect::encode, PacketDropSelect::decode, PacketDropSelect::handle,
                java.util.Optional.of(toServer));
    }
}
