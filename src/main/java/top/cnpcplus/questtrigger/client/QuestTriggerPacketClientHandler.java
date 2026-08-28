package top.cnpcplus.questtrigger.client;

import top.cnpcplus.questtrigger.network.PacketOpenTriggerGui;

public final class QuestTriggerPacketClientHandler {
    private QuestTriggerPacketClientHandler() {
    }

    public static void open(PacketOpenTriggerGui message) {
        QuestTriggerClient.openGui(message);
    }
}
