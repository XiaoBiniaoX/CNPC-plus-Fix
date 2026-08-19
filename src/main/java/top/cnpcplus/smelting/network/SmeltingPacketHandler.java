package top.cnpcplus.smelting.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import top.cnpcplus.CnpcPlus;

/**
 * 熔炼功能的网络通道。id 0-5 手工分配，改动顺序会破坏与旧版本的协议兼容（PROTOCOL_VERSION 已锁 "1"）。
 * 每个包都显式声明 NetworkDirection：不声明则双向都接受，单人/局域网开房时主机自身也是客户端，
 * 会接受伪造的 PLAY_TO_CLIENT 包（例如用 PacketSmeltingSync 覆盖主机的配方缓存）。
 */
public class SmeltingPacketHandler {
    private static final String PROTOCOL_VERSION = "1";

    @SuppressWarnings({"deprecation", "removal"})
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(CnpcPlus.MOD_ID, "smelting"))
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .simpleChannel();

    public static void init() {
        final net.minecraftforge.network.NetworkDirection toServer =
                net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER;
        final net.minecraftforge.network.NetworkDirection toClient =
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT;
        CHANNEL.registerMessage(0, PacketSmeltingSave.class,
                PacketSmeltingSave::encode, PacketSmeltingSave::decode, PacketSmeltingSave::handle, java.util.Optional.of(toServer));
        CHANNEL.registerMessage(1, PacketSmeltingRemove.class,
                PacketSmeltingRemove::encode, PacketSmeltingRemove::decode, PacketSmeltingRemove::handle, java.util.Optional.of(toServer));
        CHANNEL.registerMessage(2, PacketSmeltingSync.class,
                PacketSmeltingSync::encode, PacketSmeltingSync::decode, PacketSmeltingSync::handle, java.util.Optional.of(toClient));
        CHANNEL.registerMessage(3, PacketSmeltingRequestList.class,
                PacketSmeltingRequestList::encode, PacketSmeltingRequestList::decode, PacketSmeltingRequestList::handle, java.util.Optional.of(toServer));
        CHANNEL.registerMessage(4, PacketOpenSmeltingGui.class,
                PacketOpenSmeltingGui::encode, PacketOpenSmeltingGui::decode, PacketOpenSmeltingGui::handle, java.util.Optional.of(toServer));
        CHANNEL.registerMessage(5, PacketSmeltingSelect.class,
                PacketSmeltingSelect::encode, PacketSmeltingSelect::decode, PacketSmeltingSelect::handle, java.util.Optional.of(toServer));
    }
}
