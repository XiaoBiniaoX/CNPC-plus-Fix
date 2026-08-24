package bin.cnpcplus.smelting.network;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.smelting.SmeltingRecipeData;
import bin.cnpcplus.smelting.SmeltingClientData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.List;

public record PacketSmeltingSync(List<SmeltingRecipeData> recipes, int selectedId) implements CustomPacketPayload {
    public static final Type<PacketSmeltingSync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "smelting_sync"));

    // 必须用 OPTIONAL_STREAM_CODEC，不能用 STREAM_CODEC。
    // ItemStack.STREAM_CODEC 对空栈会直接抛异常（字节码实证：encode 抛 EncoderException
    // "Empty ItemStack not allowed"，decode 解出空栈抛 DecoderException），
    // 而熔炼配方的三个槽位都可能为空 —— 勾了「通用燃料允许」时 fuel 槽就是空的，
    // 新建配方尚未填 input/output 时更是全空。一旦编码抛异常，客户端就会看到
    // 「无效的数据包」并被踢出。OPTIONAL_STREAM_CODEC 用一个前置布尔表示是否为空，正确支持空栈。
    private static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> STACK = ItemStack.OPTIONAL_STREAM_CODEC;

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSmeltingSync> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                // 长度头必须与实际写入的元素数完全一致。
                // 若写 min(size,256) 却循环写全部元素，配方超过 256 条时读取侧只读 256 条，
                // 缓冲区剩余字节会被当成下一个包解析 —— 直接协议错位断线。
                int count = Math.min(packet.recipes.size(), 256);
                buf.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    SmeltingRecipeData d = packet.recipes.get(i);
                    buf.writeVarInt(d.id); buf.writeUtf(d.name == null ? "" : d.name, 256);
                    buf.writeFloat(d.cookTime); buf.writeFloat(d.xp);
                    buf.writeBoolean(d.blastAllowed); buf.writeBoolean(d.smokerAllowed); buf.writeBoolean(d.genericFuelAllowed);
                    STACK.encode(buf, d.input);
                    STACK.encode(buf, d.fuel);
                    STACK.encode(buf, d.output);
                }
                buf.writeVarInt(packet.selectedId);
            },
            buf -> {
                List<SmeltingRecipeData> list = new ArrayList<>();
                // 负数或超限一律视为非法包，直接拒绝，不做「截断后继续读」的容忍处理，
                // 否则同样会把剩余字节错当下一个包。
                int count = buf.readVarInt();
                if (count < 0 || count > 256) throw new IllegalArgumentException("Invalid smelting recipe count: " + count);
                for (int i = 0; i < count; i++) {
                    SmeltingRecipeData d = new SmeltingRecipeData();
                    d.id = buf.readVarInt(); d.name = buf.readUtf(256); d.cookTime = buf.readFloat(); d.xp = buf.readFloat();
                    d.blastAllowed = buf.readBoolean(); d.smokerAllowed = buf.readBoolean(); d.genericFuelAllowed = buf.readBoolean();
                    d.input = STACK.decode(buf);
                    d.fuel = STACK.decode(buf);
                    d.output = STACK.decode(buf);
                    list.add(d);
                }
                return new PacketSmeltingSync(list, buf.readVarInt());
            });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(PacketSmeltingSync packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            SmeltingClientData.set(packet.recipes, packet.selectedId);
        });
    }
}
