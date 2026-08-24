package top.cnpcplus.questtrigger.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import top.cnpcplus.CnpcPlus;

import java.util.Optional;

public class TriggerPacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    @SuppressWarnings({"deprecation", "removal"})
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(CnpcPlus.MOD_ID, "quest_trigger"))
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .simpleChannel();

    /**
     * 必须显式声明方向。OpenTriggerGui 的 handle 走 DistExecutor 客户端分支，
     * 若在服务端也被登记为可接收，专用服务器上会去解析客户端 GUI 类；
     * 且方向缺失会让两端通道登记不一致，玩家进服时报「无效的数据包」。
     */
    public static void init() {
        CHANNEL.registerMessage(0, PacketOpenTriggerGui.class,
                PacketOpenTriggerGui::encode, PacketOpenTriggerGui::decode, PacketOpenTriggerGui::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, PacketSaveTriggerData.class,
                PacketSaveTriggerData::encode, PacketSaveTriggerData::decode, PacketSaveTriggerData::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}
