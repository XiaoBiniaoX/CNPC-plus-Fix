package bin.cnpcplus.linked.client;

import bin.cnpcplus.linked.network.PacketLinkedScriptSyncState;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import noppes.npcs.client.gui.global.GuiNPCManageLinkedNpc;
import noppes.npcs.shared.client.gui.listeners.IGuiData;

public final class PacketLinkedScriptSyncStateClient {
    private PacketLinkedScriptSyncStateClient() {
    }

    public static void handle(PacketLinkedScriptSyncState msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Screen screen = GuiNPCManageLinkedNpc.Instance;
            if (!(screen instanceof IGuiData guiData)) return;
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("CNPCPlusScriptSync", msg.scriptSync());
            guiData.setGuiData(tag);
        });
    }
}
