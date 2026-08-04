package top.cnpcplus.questtrigger.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import top.cnpcplus.CnpcPlus;

public class TriggerPacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    @SuppressWarnings({"deprecation", "removal"})
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(CnpcPlus.MOD_ID, "quest_trigger"))
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .simpleChannel();

    public static void init() {
        CHANNEL.registerMessage(0, PacketOpenTriggerGui.class,
                PacketOpenTriggerGui::encode, PacketOpenTriggerGui::decode, PacketOpenTriggerGui::handle);
        CHANNEL.registerMessage(1, PacketSaveTriggerData.class,
                PacketSaveTriggerData::encode, PacketSaveTriggerData::decode, PacketSaveTriggerData::handle);
    }
}
