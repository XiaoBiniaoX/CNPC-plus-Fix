package top.cnpcplus.questtrigger.client;

import net.minecraft.client.Minecraft;
import top.cnpcplus.questtrigger.network.PacketOpenTriggerGui;

public class QuestTriggerClient {
    public static void openGui(PacketOpenTriggerGui msg) {
        Minecraft.getInstance().setScreen(new GuiQuestTrigger(msg.pos, msg.data));
    }
}
