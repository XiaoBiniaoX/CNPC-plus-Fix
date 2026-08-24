package top.cnpcplus.invpage.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import top.cnpcplus.CnpcPlus;

import java.util.Optional;

public class NpcInvPagePacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    @SuppressWarnings({"deprecation", "removal"})
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(CnpcPlus.MOD_ID, "npcinv_page"))
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .simpleChannel();

    /** 必须显式声明方向：缺失会让两端通道登记不一致，玩家进服时报「无效的数据包」。 */
    public static void init() {
        CHANNEL.registerMessage(0, PacketNpcInvPage.class,
                PacketNpcInvPage::encode, PacketNpcInvPage::decode, PacketNpcInvPage::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}
