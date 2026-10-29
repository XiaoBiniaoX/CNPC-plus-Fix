package top.cnpcplus.anvil.network;

import bin.cnpcplus.CnpcPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.cnpcplus.anvil.AnvilRecipeData;

import java.util.ArrayList;
import java.util.List;

/** 服务端→客户端：同步全部自定义铁砧配方（用于 GUI 列表，231 从 PacketSmithingSync 复制）。 */
public record PacketAnvilSync(List<AnvilRecipeData> data) implements CustomPacketPayload {
    public static final Type<PacketAnvilSync> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "anvil_sync"));
    private static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> STACK = ItemStack.OPTIONAL_STREAM_CODEC;
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAnvilSync> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                int count = Math.min(packet.data.size(), 256);
                buf.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    AnvilRecipeData d = packet.data.get(i);
                    buf.writeVarInt(d.id);
                    buf.writeUtf(d.name == null ? "" : d.name, 64);
                    buf.writeInt(d.expCost);
                    buf.writeBoolean(d.nameOnly);
                    STACK.encode(buf, d.inputA);
                    STACK.encode(buf, d.inputB);
                    STACK.encode(buf, d.result);
                }
            },
            buf -> {
                int count = buf.readVarInt();
                if (count < 0 || count > 256) throw new IllegalArgumentException("Invalid anvil recipe count: " + count);
                List<AnvilRecipeData> list = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    AnvilRecipeData d = new AnvilRecipeData();
                    d.id = buf.readVarInt();
                    d.name = buf.readUtf(64);
                    d.expCost = Math.max(-32767, Math.min(32767, buf.readInt()));
                    d.nameOnly = buf.readBoolean();
                    d.inputA = STACK.decode(buf);
                    d.inputB = STACK.decode(buf);
                    d.result = STACK.decode(buf);
                    list.add(d);
                }
                return new PacketAnvilSync(list);
            });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PacketAnvilSync packet, IPayloadContext context) {
        if (context.flow().isClientbound()) {
            context.enqueueWork(() -> top.cnpcplus.anvil.client.AnvilClientSync.accept(packet.data));
        }
    }
}
