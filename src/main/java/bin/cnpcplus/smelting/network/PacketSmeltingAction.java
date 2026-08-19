package bin.cnpcplus.smelting.network;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.recipe.services.RecipeServices;
import bin.cnpcplus.smelting.ContainerSmeltingRecipes;
import bin.cnpcplus.smelting.SmeltingRecipeData;
import bin.cnpcplus.smelting.SmeltingRecipeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

public record PacketSmeltingAction(int action, int id, String name, float cookTime, float xp,
                                   boolean blast, boolean smoker, boolean generic) implements CustomPacketPayload {
    public static final Type<PacketSmeltingAction> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "smelting_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSmeltingAction> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> { buf.writeVarInt(packet.action); buf.writeVarInt(packet.id); buf.writeUtf(packet.name, 256);
                buf.writeFloat(packet.cookTime); buf.writeFloat(packet.xp); buf.writeBoolean(packet.blast);
                buf.writeBoolean(packet.smoker); buf.writeBoolean(packet.generic); },
            buf -> new PacketSmeltingAction(buf.readVarInt(), buf.readVarInt(), buf.readUtf(256), buf.readFloat(),
                    buf.readFloat(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PacketSmeltingAction packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) return;
            var registries = player.server.registryAccess();
            if (packet.action == 0) {
                player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ContainerSmeltingRecipes(id, inv, packet.id), Component.translatable("cnpcplus.smelting.title")), buf -> buf.writeVarInt(packet.id));
                sendList(player, packet.id);
                return;
            }
            if (packet.action == 3) { sendList(player, player.containerMenu instanceof ContainerSmeltingRecipes menu ? menu.selectedId : -1); return; }
            if (packet.action == 5 && player.containerMenu instanceof ContainerSmeltingRecipes menu) {
                menu.clearRecipe();
                sendList(player, -1);
                return;
            }
            if (packet.action == 4 && player.containerMenu instanceof ContainerSmeltingRecipes menu) {
                SmeltingRecipeData selected = SmeltingRecipeRegistry.get(registries, packet.id);
                if (selected != null) menu.setRecipe(selected);
                sendList(player, menu.selectedId);
                return;
            }
            if (packet.action == 2) {
                if (packet.id >= 0 && SmeltingRecipeRegistry.remove(registries, packet.id)) {
                    RecipeServices.reloadSmeltingRecipes(player.server);
                }
            }
            if (packet.action == 1 && player.containerMenu instanceof ContainerSmeltingRecipes menu) {
                SmeltingRecipeData data = new SmeltingRecipeData();
                data.id = packet.id; data.name = packet.name; data.cookTime = packet.cookTime; data.xp = packet.xp;
                data.blastAllowed = packet.blast; data.smokerAllowed = packet.smoker; data.genericFuelAllowed = packet.generic;
                data.input = menu.input().copy(); data.fuel = menu.fuel().copy(); data.output = menu.output().copy();
                if (!data.input.isEmpty() && !data.output.isEmpty()) {
                    if (data.id < 0) {
                        SmeltingRecipeData created = SmeltingRecipeRegistry.create(registries, data);
                        menu.selectedId = created.id;
                    } else if (SmeltingRecipeRegistry.update(registries, data)) {
                        menu.selectedId = data.id;
                    } else {
                        return;
                    }
                    RecipeServices.reloadSmeltingRecipes(player.server);
                }
            }
            sendList(player, player.containerMenu instanceof ContainerSmeltingRecipes menu ? menu.selectedId : -1);
        });
    }

    private static void sendList(ServerPlayer player, int selectedId) {
        PacketDistributor.sendToPlayer(player, new PacketSmeltingSync(
                SmeltingRecipeRegistry.list(player.server.registryAccess()), selectedId));
    }
}
