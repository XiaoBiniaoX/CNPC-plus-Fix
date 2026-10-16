package top.cnpcplus.anvil.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcplus.anvil.AnvilRecipeData;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 服务端→客户端：同步全部自定义铁砧配方（用于 GUI 列表，231 从 PacketSmithingSync 复制）。 */
public class PacketAnvilSync {
    private final List<AnvilRecipeData> data;

    public PacketAnvilSync(List<AnvilRecipeData> data) { this.data = data; }

    public void encode(FriendlyByteBuf buf) {
        ListTag list = new ListTag();
        for (AnvilRecipeData d : this.data) list.add(d.toNBT());
        CompoundTag root = new CompoundTag();
        root.put("Data", list);
        buf.writeNbt(root);
    }

    public static PacketAnvilSync decode(FriendlyByteBuf buf) {
        List<AnvilRecipeData> list = new ArrayList<>();
        CompoundTag root = buf.readNbt();
        if (root != null) {
            ListTag lt = root.getList("Data", Tag.TAG_COMPOUND);
            for (int i = 0; i < lt.size(); i++) list.add(AnvilRecipeData.fromNBT(lt.getCompound(i)));
        }
        return new PacketAnvilSync(list);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        // 客户端逻辑放在独立的 client-only 类里，本类不 import 任何客户端类型，
        // 保证专用服务器加载本包类时不会去解析 Minecraft/GUI 类。
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> top.cnpcplus.anvil.client.AnvilClientSync.accept(this.data)));
        ctx.get().setPacketHandled(true);
    }
}
