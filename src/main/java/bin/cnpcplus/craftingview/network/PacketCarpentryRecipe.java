package bin.cnpcplus.craftingview.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A single saved/deleted recipe, using the same identity as the initial CNPC list. */
public record PacketCarpentryRecipe(ResourceLocation id, CompoundTag data) implements CustomPacketPayload {
    public static final Type<PacketCarpentryRecipe> TYPE = new Type<>(ResourceLocation.parse("cnpcplus:carpentry_recipe"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketCarpentryRecipe> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> { buf.writeResourceLocation(packet.id); buf.writeNbt(packet.data); },
            buf -> new PacketCarpentryRecipe(buf.readResourceLocation(), buf.readNbt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(PacketCarpentryRecipe packet, IPayloadContext context) {
        context.enqueueWork(() -> bin.cnpcplus.craftingview.RecipeAccess.acceptUpdate(packet.id, packet.data));
    }
}
