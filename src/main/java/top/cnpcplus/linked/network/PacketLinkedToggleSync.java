package top.cnpcplus.linked.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import noppes.npcs.controllers.LinkedNpcController;
import top.cnpcplus.data.ExtraDataStorage;

import java.util.function.Supplier;

public class PacketLinkedToggleSync {
    private final String tagName;
    private final boolean syncScripts;

    public PacketLinkedToggleSync(String tagName, boolean syncScripts) {
        this.tagName = tagName;
        this.syncScripts = syncScripts;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(tagName, 64);
        buf.writeBoolean(syncScripts);
    }

    public static PacketLinkedToggleSync decode(FriendlyByteBuf buf) {
        return new PacketLinkedToggleSync(buf.readUtf(64), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var sender = ctx.get().getSender();
            if (sender == null || !sender.hasPermissions(2)) return;
            for (var data : LinkedNpcController.Instance.list) {
                if (data.name.equalsIgnoreCase(tagName)) {
                    ExtraDataStorage.setBool(data, syncScripts);
                    LinkedNpcController.Instance.save();
                    break;
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
