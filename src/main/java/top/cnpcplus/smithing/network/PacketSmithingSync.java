package top.cnpcplus.smithing.network;

import bin.cnpcplus.CnpcPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.cnpcplus.smithing.SmithingRecipeData;

import java.util.ArrayList;
import java.util.List;

/** 服务端→客户端：同步全部自定义锻造配方（用于 GUI 列表，227 从 PacketSmeltingSync 复制）。 */
public record PacketSmithingSync(List<SmithingRecipeData> data) implements CustomPacketPayload {
    public static final Type<PacketSmithingSync> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "smithing_sync"));
    private static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> STACK = ItemStack.OPTIONAL_STREAM_CODEC;
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSmithingSync> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                int count = Math.min(packet.data.size(), 256);
                buf.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    SmithingRecipeData d = packet.data.get(i);
                    buf.writeVarInt(d.id);
                    buf.writeUtf(d.name == null ? "" : d.name, 64);
                    buf.writeBoolean(d.nameOnly);
                    STACK.encode(buf, d.template);
                    STACK.encode(buf, d.base);
                    STACK.encode(buf, d.addition);
                    STACK.encode(buf, d.result);
                }
            }, buf -> {
                int count = buf.readVarInt();
                if (count < 0 || count > 256) throw new IllegalArgumentException("Invalid smithing recipe count: " + count);
                List<SmithingRecipeData> list = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    SmithingRecipeData d = new SmithingRecipeData();
                    d.id = buf.readVarInt();
                    d.name = buf.readUtf(64);
                    d.nameOnly = buf.readBoolean();
                    d.template = STACK.decode(buf);
                    d.base = STACK.decode(buf);
                    d.addition = STACK.decode(buf);
                    d.result = STACK.decode(buf);
                    list.add(d);
                }
                return new PacketSmithingSync(list);
            });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PacketSmithingSync packet, IPayloadContext context) {
        if (context.flow().isClientbound()) {
            context.enqueueWork(() -> top.cnpcplus.smithing.client.SmithingClientSync.accept(packet.data));
        }
    }
}
