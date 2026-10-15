package top.cnpcplus.villagertrade.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import top.cnpcplus.CnpcPlus;

import java.util.Optional;

/** 村民交易商编辑面板专用通道（关面板背包提交等 C2S）。 */
public class VillagerEditPacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    @SuppressWarnings({"deprecation", "removal"})
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(CnpcPlus.MOD_ID, "villager_edit"))
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .simpleChannel();

    /** 必须显式声明方向：缺失会让两端通道登记不一致，玩家进服时报「无效的数据包」。 */
    public static void init() {
        CHANNEL.registerMessage(0, PacketEditInventoryCommit.class,
                PacketEditInventoryCommit::encode, PacketEditInventoryCommit::decode, PacketEditInventoryCommit::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}
