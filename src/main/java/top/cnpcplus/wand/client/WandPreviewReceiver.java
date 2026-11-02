package top.cnpcplus.wand.client;

import net.minecraft.client.Minecraft;
import top.cnpcplus.wand.network.WandNpcPacket;

public final class WandPreviewReceiver {
    private WandPreviewReceiver() {}
    public static void accept(WandNpcPacket.Snapshot packet) {
        if (Minecraft.getInstance().screen instanceof GuiWandNpcList screen) {
            if (packet.copying()) screen.acceptCopy(packet.request(), packet.data());
            else screen.acceptPreview(packet.entityId(), packet.request(), packet.data());
        }
    }
}
