package top.cnpcplus.questtrigger.client;

import net.minecraft.client.Minecraft;
import top.cnpcplus.questtrigger.network.PacketOpenTriggerGui;

public final class QuestTriggerClient {
    private QuestTriggerClient() {}
    public static void openGui(PacketOpenTriggerGui packet) {
        Minecraft.getInstance().setScreen(new GuiQuestTrigger(packet.pos(), packet.data()));
    }
}
