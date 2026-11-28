package bin.cnpcplus.craftingview.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.packets.Packets;
import noppes.npcs.packets.client.PacketSync;

/** Recover a missing editor catalogue using the same paged snapshot as login. */
public record PacketCarpentryRequestList(boolean global) implements CustomPacketPayload {
    public static final Type<PacketCarpentryRequestList> TYPE = new Type<>(ResourceLocation.parse("cnpcplus:carpentry_request_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketCarpentryRequestList> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, PacketCarpentryRequestList::global, PacketCarpentryRequestList::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PacketCarpentryRequestList packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) return;
            var controller = RecipeController.instance;
            int type = packet.global ? 6 : 7;
            ListTag list = new ListTag();
            var recipes = type == 6 ? controller.globalRecipes : controller.anvilRecipes;
            for (var recipe : recipes.values()) {
                list.add(recipe.writeNBT(player.registryAccess()));
                if (list.size() == 11) {
                    CompoundTag data = new CompoundTag();
                    data.put("Data", list);
                    Packets.send(player, new PacketSync(type, data, false));
                    list = new ListTag();
                }
            }
            CompoundTag data = new CompoundTag();
            data.put("Data", list);
            Packets.send(player, new PacketSync(type, data, true));
        });
    }
}
