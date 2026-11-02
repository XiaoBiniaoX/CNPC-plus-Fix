package bin.cnpcplus.wand.client;

import bin.cnpcplus.wand.PacketWandNpc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import noppes.npcs.client.gui.util.GuiNPCInterface;

public interface PreviewReceiver {
    void cnpcplus$receive(PacketWandNpc packet);
    static void receive(PacketWandNpc packet) {
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen instanceof GuiNPCInterface && ((GuiNPCInterface) screen).hasSubGui()) screen = ((GuiNPCInterface) screen).getSubGui();
        if (screen instanceof PreviewReceiver) ((PreviewReceiver) screen).cnpcplus$receive(packet);
    }
}
