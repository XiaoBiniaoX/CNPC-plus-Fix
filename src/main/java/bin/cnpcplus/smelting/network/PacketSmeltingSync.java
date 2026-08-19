package bin.cnpcplus.smelting.network;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.smelting.SmeltingRecipeData;
import bin.cnpcplus.smelting.SmeltingClientData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.List;

public record PacketSmeltingSync(List<SmeltingRecipeData> recipes, int selectedId) implements CustomPacketPayload {
    public static final Type<PacketSmeltingSync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "smelting_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSmeltingSync> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeVarInt(Math.min(packet.recipes.size(), 256));
                for (SmeltingRecipeData d : packet.recipes) {
                    buf.writeVarInt(d.id); buf.writeUtf(d.name == null ? "" : d.name, 256);
                    buf.writeFloat(d.cookTime); buf.writeFloat(d.xp);
                    buf.writeBoolean(d.blastAllowed); buf.writeBoolean(d.smokerAllowed); buf.writeBoolean(d.genericFuelAllowed);
                    net.minecraft.world.item.ItemStack.STREAM_CODEC.encode(buf, d.input);
                    net.minecraft.world.item.ItemStack.STREAM_CODEC.encode(buf, d.fuel);
                    net.minecraft.world.item.ItemStack.STREAM_CODEC.encode(buf, d.output);
                }
                buf.writeVarInt(packet.selectedId);
            },
            buf -> {
                List<SmeltingRecipeData> list = new ArrayList<>();
                int count = Math.min(buf.readVarInt(), 256);
                for (int i = 0; i < count; i++) {
                    SmeltingRecipeData d = new SmeltingRecipeData();
                    d.id = buf.readVarInt(); d.name = buf.readUtf(256); d.cookTime = buf.readFloat(); d.xp = buf.readFloat();
                    d.blastAllowed = buf.readBoolean(); d.smokerAllowed = buf.readBoolean(); d.genericFuelAllowed = buf.readBoolean();
                    d.input = net.minecraft.world.item.ItemStack.STREAM_CODEC.decode(buf);
                    d.fuel = net.minecraft.world.item.ItemStack.STREAM_CODEC.decode(buf);
                    d.output = net.minecraft.world.item.ItemStack.STREAM_CODEC.decode(buf);
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
