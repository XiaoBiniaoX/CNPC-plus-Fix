package top.cnpcplus.drop.network;

import net.minecraft.nbt.CompoundTag;
import bin.cnpcplus.CnpcPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.cnpcplus.drop.DropRecipeData;

import java.util.ArrayList;
import java.util.List;

/** 服务端→客户端：同步全部自定义掉落物配方（用于 GUI 列表，232 从 PacketAnvilSync 复制）。 */
public record PacketDropSync(List<DropRecipeData> data) implements CustomPacketPayload {
    public static final Type<PacketDropSync> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "drop_sync"));
    private static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> STACK = ItemStack.OPTIONAL_STREAM_CODEC;
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketDropSync> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                int count = Math.min(packet.data.size(), 256);
                buf.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    DropRecipeData d = packet.data.get(i);
                    buf.writeNbt(d.toMetadataNBT());
                    for (ItemStack input : d.inputs) STACK.encode(buf, input);
                    STACK.encode(buf, d.result);
                }
            }, buf -> {
                int count = buf.readVarInt();
                if (count < 0 || count > 256) throw new IllegalArgumentException("Invalid drop recipe count: " + count);
                List<DropRecipeData> list = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    CompoundTag metadata = buf.readNbt();
                    if (metadata == null) throw new IllegalArgumentException("Missing drop recipe metadata");
                    DropRecipeData d = DropRecipeData.fromMetadataNBT(metadata);
                    for (int slot = 0; slot < 4; slot++) d.inputs[slot] = STACK.decode(buf);
                    d.result = STACK.decode(buf);
                    list.add(d);
                }
                return new PacketDropSync(list);
            });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PacketDropSync packet, IPayloadContext context) {
        if (context.flow().isClientbound()) {
            context.enqueueWork(() -> top.cnpcplus.drop.client.DropClientSync.accept(packet.data));
        }
    }
}
