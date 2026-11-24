package top.cnpcplus.craftingview;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public record RecipeDeltaPacket(ResourceLocation id, RecipeHolder<?> recipe) implements CustomPacketPayload {
    public static final Type<RecipeDeltaPacket> TYPE = new Type<>(ResourceLocation.parse("cnpcplus:recipe_delta"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeDeltaPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeResourceLocation(p.id); buf.writeBoolean(p.recipe != null);
                if (p.recipe != null) Recipe.STREAM_CODEC.encode(buf, p.recipe.value());
            }, buf -> {
                var id = buf.readResourceLocation();
                return new RecipeDeltaPacket(id, buf.readBoolean() ? new RecipeHolder<>(id, Recipe.STREAM_CODEC.decode(buf)) : null);
            });
    @Override public Type<RecipeDeltaPacket> type() { return TYPE; }
    public static void broadcast(MinecraftServer server, ResourceLocation id, RecipeHolder<?> recipe) {
        for (var player : server.getPlayerList().getPlayers()) {
            if (NetworkRegistry.hasChannel(player.connection, TYPE.id())) PacketDistributor.sendToPlayer(player, new RecipeDeltaPacket(id, recipe));
            if (player.containerMenu instanceof top.cnpcplus.smithing.SmithingMenuAccess menu) {
                menu.cnpcplus$refreshRecipes(); player.containerMenu.broadcastChanges();
            }
        }
    }
    public static void handle(RecipeDeltaPacket p, IPayloadContext context) {
        context.enqueueWork(() -> top.cnpcplus.craftingview.client.RecipeDeltaReceiver.accept(p));
    }
}
