package top.cnpcplus.station;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public record StationPacket(int operation, int kind, CompoundTag data) {
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation("cnpcplus:stations"), () -> "2", "2"::equals, "2"::equals);
    public static void init() {
        CHANNEL.registerMessage(0, StationPacket.class, StationPacket::encode, StationPacket::decode, StationPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Sync.class, Sync::encode, Sync::decode, Sync::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public void encode(FriendlyByteBuf buf) { buf.writeVarInt(operation); buf.writeVarInt(kind); buf.writeNbt(data); }
    public static StationPacket decode(FriendlyByteBuf buf) { return new StationPacket(buf.readVarInt(), buf.readVarInt(), buf.readNbt()); }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null || !player.hasPermissions(2) || kind < 0 || kind >= 3 || data == null || data.sizeInBytes() > 30000) return;
            if (operation == 0) {
                NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inv, p) -> new StationMenu(id, inv, kind),
                        Component.translatable("cnpcplus.station." + StationRecipes.KINDS[kind])), buf -> buf.writeVarInt(kind));
            } else if (player.containerMenu instanceof StationMenu menu && menu.kind == kind) {
                if (operation == 2) { menu.select(data.getInt("Id"), player); return; }
                if (operation == 3 || operation == 4) {
                    int edited = data.getInt("Id");
                    if (StationRecipes.saving[kind]) {
                        sync(player, kind, edited, menu.selected, "cnpcplus.station.saving", -1);
                        return;
                    }
                    var catalogue = StationRecipes.catalogue(kind);
                    var updated = new LinkedHashMap<>(catalogue);
                    int selected;
                    if (operation == 4) {
                        updated.remove(edited);
                        selected = -1;
                    } else {
                        StationRecipe recipe = StationRecipe.read(data);
                        if (recipe.id != menu.selected) return;
                        if (kind == 2) recipe.shapeless = false;
                        recipe.name = recipe.name.trim();
                        if (recipe.name.isEmpty()) recipe.name = "new";
                        for (int i = 0; i < 4; i++) recipe.inputs[i] = menu.recipe.getItem(i).copy();
                        for (int i = 0; i < 3; i++) recipe.outputs[i] = menu.recipe.getItem(4 + i).copy();
                        boolean invalid = java.util.Arrays.stream(recipe.inputs).allMatch(net.minecraft.world.item.ItemStack::isEmpty)
                                || java.util.Arrays.stream(recipe.outputs).allMatch(net.minecraft.world.item.ItemStack::isEmpty);
                        if (kind == 0) invalid |= !recipe.inputs[2].isEmpty() || !recipe.inputs[3].isEmpty() || !recipe.outputs[1].isEmpty() || !recipe.outputs[2].isEmpty();
                        if (kind == 2) invalid |= !recipe.inputs[1].isEmpty() || !recipe.inputs[2].isEmpty() || !recipe.inputs[3].isEmpty()
                                || !recipe.outputs[1].isEmpty() || !recipe.outputs[2].isEmpty() || recipe.inputs[0].getCount() != 1;
                        if (kind == 1) {
                            invalid |= recipe.inputs[3].isEmpty();
                            for (int i = 0; i < 3; i++) invalid |= recipe.inputs[i].getCount() > 1;
                        }
                        if (invalid) {
                            sync(player, kind, recipe.id, menu.selected, "cnpcplus.station.invalid." + StationRecipes.KINDS[kind], -1);
                            return;
                        }
                        if (recipe.id < 0) recipe.id = catalogue.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
                        updated.put(recipe.id, recipe);
                        selected = recipe.id;
                    }
                    int result = operation == 3 ? 1 : 2;
                    String notice = operation == 3 ? "cnpcplus.station.saved" : "cnpcplus.station.deleted";
                    var snapshot = StationRecipes.snapshot(updated.values());
                    StationRecipes.saving[kind] = true;
                    StationRecipes.persist(kind, snapshot).whenComplete((done, error) -> player.server.execute(() -> {
                        StationRecipes.saving[kind] = false;
                        boolean sameMenu = player.containerMenu == menu;
                        if (error != null) {
                            if (sameMenu) sync(player, kind, edited, menu.selected, "cnpcplus.station.save_failed", -1);
                            else player.sendSystemMessage(Component.translatable("cnpcplus.station.save_failed"));
                            return;
                        }
                        catalogue.clear();
                        catalogue.putAll(updated);
                        if (sameMenu && menu.selected == edited) {
                            if (operation == 4) menu.select(-1, player);
                            else menu.selected = selected;
                        }
                        for (ServerPlayer recipient : player.server.getPlayerList().getPlayers()) {
                            var response = snapshot;
                            if (recipient == player && sameMenu) {
                                response = new CompoundTag();
                                response.put("Recipes", snapshot.get("Recipes"));
                                response.putInt("EditedId", edited);
                                response.putInt("SelectedId", selected);
                                response.putString("Notice", notice);
                                response.putInt("Result", result);
                            }
                            CHANNEL.send(PacketDistributor.PLAYER.with(() -> recipient), new Sync(kind, response));
                        }
                    }));
                    return;
                }
            } else return;
            sync(player, kind);
        });
        context.setPacketHandled(true);
    }
    private static void sync(ServerPlayer player, int kind) {
        sync(player, kind, -1, -1, "", 0);
    }
    private static void sync(ServerPlayer player, int kind, int edited, int selected, String notice, int result) {
        CompoundTag tag = StationRecipes.snapshot(StationRecipes.catalogue(kind).values());
        if (result != 0) { tag.putInt("EditedId", edited); tag.putInt("SelectedId", selected); tag.putString("Notice", notice); tag.putInt("Result", result); }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Sync(kind, tag));
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void login(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) for (int kind = 0; kind < 3; kind++) sync(player, kind);
    }
    public record Sync(int kind, CompoundTag data) {
        public void encode(FriendlyByteBuf buf) { buf.writeVarInt(kind); buf.writeNbt(data); }
        public static Sync decode(FriendlyByteBuf buf) { return new Sync(buf.readVarInt(), buf.readNbt()); }
        public void handle(Supplier<NetworkEvent.Context> supplier) {
            supplier.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> StationScreen.accept(kind, data)));
            supplier.get().setPacketHandled(true);
        }
    }
}
