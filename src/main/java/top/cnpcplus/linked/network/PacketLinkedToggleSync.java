package top.cnpcplus.linked.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import noppes.npcs.controllers.LinkedNpcController;
import top.cnpcplus.linked.LinkedSyncFlags;

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
            if (LinkedNpcController.Instance == null) return;
            for (var data : LinkedNpcController.Instance.list) {
                if (data.name.equalsIgnoreCase(tagName)) {
                    // 用标签名作 key（见 LinkedSyncFlags 注释）：LinkedData 实例会被
                    // loadNpcs() 周期性重建，用实例作 key 会丢状态。
                    LinkedSyncFlags.setSyncScripts(data.name, syncScripts);
                    LinkedNpcController.Instance.save();
                    break;
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
