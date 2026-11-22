package top.cnpcplus.smithing.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcplus.smithing.SmithingRecipeData;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 服务端→客户端：首次同步编辑列表，或更新已保存/删除的一条配方。 */
public class PacketSmithingSync {
    private final List<SmithingRecipeData> data;
    private final int changedId;

    public PacketSmithingSync(List<SmithingRecipeData> data) { this(data, -1); }

    public PacketSmithingSync(List<SmithingRecipeData> data, int changedId) {
        this.data = data;
        this.changedId = changedId;
    }

    public void encode(FriendlyByteBuf buf) {
        ListTag list = new ListTag();
        for (SmithingRecipeData d : this.data) list.add(d.toNBT());
        CompoundTag root = new CompoundTag();
        root.put("Data", list);
        if (this.changedId >= 0) root.putInt("ChangedId", this.changedId);
        buf.writeNbt(root);
    }

    public static PacketSmithingSync decode(FriendlyByteBuf buf) {
        List<SmithingRecipeData> list = new ArrayList<>();
        CompoundTag root = buf.readNbt();
        if (root != null) {
            ListTag lt = root.getList("Data", Tag.TAG_COMPOUND);
            for (int i = 0; i < lt.size(); i++) list.add(SmithingRecipeData.fromNBT(lt.getCompound(i)));
        }
        return new PacketSmithingSync(list, root != null && root.contains("ChangedId") ? root.getInt("ChangedId") : -1);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        // 客户端逻辑放在独立的 client-only 类里，本类不 import 任何客户端类型，
        // 保证专用服务器加载本包类时不会去解析 Minecraft/GUI 类。
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> top.cnpcplus.smithing.client.SmithingClientSync.accept(this.data, this.changedId)));
        ctx.get().setPacketHandled(true);
    }
}
