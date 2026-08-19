package top.cnpcplus.smelting.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcplus.smelting.SmeltingRecipeRegistry;
import top.cnpcplus.smelting.SmeltingRecipeManager;

import java.util.function.Supplier;

/** 客户端→服务端：请求同步配方列表。 */
public class PacketSmeltingRequestList {
    public PacketSmeltingRequestList() {}

    public void encode(FriendlyByteBuf buf) {}

    public static PacketSmeltingRequestList decode(FriendlyByteBuf buf) { return new PacketSmeltingRequestList(); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var sender = ctx.get().getSender();
            // 需要权限：配方列表只给编辑者看。否则任意客户端都能反复索取全部配方 NBT，
            // 既泄露配方内容，也能靠服务端序列化做流量放大。
            if (sender == null || !sender.hasPermissions(2)) return;
            SmeltingRecipeManager.syncToPlayers(sender.server, sender);
        });
        ctx.get().setPacketHandled(true);
    }
}
