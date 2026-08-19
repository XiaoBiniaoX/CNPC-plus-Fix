package top.cnpcplus.smelting.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcplus.smelting.SmeltingRecipeRegistry;
import top.cnpcplus.smelting.SmeltingRecipeManager;

import java.util.function.Supplier;

/** 客户端→服务端：删除自定义熔炼配方。 */
public class PacketSmeltingRemove {
    private final int id;

    public PacketSmeltingRemove(int id) { this.id = id; }

    public void encode(FriendlyByteBuf buf) { buf.writeInt(this.id); }

    public static PacketSmeltingRemove decode(FriendlyByteBuf buf) { return new PacketSmeltingRemove(buf.readInt()); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var sender = ctx.get().getSender();
            if (sender == null || !sender.hasPermissions(2)) return;
            if (!SmeltingRecipeRegistry.remove(this.id)) return;
            SmeltingRecipeManager.injectAll(sender.server.getRecipeManager());
            SmeltingRecipeManager.syncToPlayers(sender.server);
            // 让客户端的配方书/JEI 同步移除，否则要重连才生效
            SmeltingRecipeManager.resendVanillaRecipes(sender.server);
        });
        ctx.get().setPacketHandled(true);
    }
}
