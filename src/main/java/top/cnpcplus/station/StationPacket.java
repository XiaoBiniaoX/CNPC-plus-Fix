package top.cnpcplus.station;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@EventBusSubscriber(modid = "cnpcplus")
public record StationPacket(int operation, int kind, CompoundTag data) implements CustomPacketPayload {
    public static final Type<StationPacket> TYPE = new Type<>(ResourceLocation.parse("cnpcplus:stations"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StationPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.operation); buf.writeVarInt(p.kind); buf.writeNbt(p.data); },
            buf -> new StationPacket(buf.readVarInt(), buf.readVarInt(), buf.readNbt()));
    @Override public Type<StationPacket> type() { return TYPE; }
    public static void handle(StationPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)
                    || packet.kind < 0 || packet.kind >= 3 || packet.data == null || packet.data.sizeInBytes() > 30000) return;
            int kind = packet.kind, operation = packet.operation;
            CompoundTag data = packet.data;
            if (operation == 0) {
                player.openMenu(new SimpleMenuProvider((id, inv, p) -> new StationMenu(id, inv, kind),
                        Component.translatable("cnpcplus.station." + StationRecipes.KINDS[kind])), buf -> buf.writeVarInt(kind));
            } else if (player.containerMenu instanceof StationMenu menu && menu.kind == kind) {
                if (operation == 2) { menu.select(data.getInt("Id"), player); return; }
                else if (operation == 3 || operation == 4) {
                    int edited = data.getInt("Id");
                    if (StationRecipes.saving[kind]) {
                        sync(player, kind, edited, menu.selected, "cnpcplus.station.saving", -1);
                        return;
                    }
                    var catalogue = StationRecipes.catalogue(kind);
                    var updated = new LinkedHashMap<>(catalogue);
                    int selected;
                    if (operation == 4) { updated.remove(edited); selected = -1; }
                    else {
                        StationRecipe recipe = StationRecipe.readMetadata(data, player.registryAccess());
                        if (recipe.id != menu.selected) return;
                        if (kind == 2) recipe.shapeless = false;
                        recipe.name = recipe.name.trim();
                        if (recipe.name.isEmpty()) recipe.name = "new";
                        for (int i = 0; i < 4; i++) recipe.inputs[i] = menu.recipe.getItem(i).copy();
                        for (int i = 0; i < 3; i++) recipe.outputs[i] = menu.recipe.getItem(4 + i).copy();
                        boolean invalid = Arrays.stream(recipe.inputs).allMatch(ItemStack::isEmpty) || Arrays.stream(recipe.outputs).allMatch(ItemStack::isEmpty);
                        if (kind == 0) invalid |= !recipe.inputs[2].isEmpty() || !recipe.inputs[3].isEmpty() || !recipe.outputs[1].isEmpty() || !recipe.outputs[2].isEmpty();
                        if (kind == 2) invalid |= !recipe.inputs[1].isEmpty() || !recipe.inputs[2].isEmpty() || !recipe.inputs[3].isEmpty()
                                || !recipe.outputs[1].isEmpty() || !recipe.outputs[2].isEmpty() || recipe.inputs[0].getCount() != 1;
                        if (kind == 1) {
                            invalid |= recipe.inputs[3].isEmpty();
                            for (int i = 0; i < 3; i++) invalid |= recipe.inputs[i].getCount() > 1;
                        }
                        if (invalid) { sync(player, kind, edited, menu.selected, "cnpcplus.station.invalid." + StationRecipes.KINDS[kind], -1); return; }
                        if (recipe.id < 0) recipe.id = catalogue.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
                        updated.put(recipe.id, recipe);
                        selected = recipe.id;
                    }
                    int result = operation == 3 ? 1 : 2;
                    String notice = operation == 3 ? "cnpcplus.station.saved" : "cnpcplus.station.deleted";
                    StationRecipes.saving[kind] = true;
                    StationRecipes.persist(kind, updated.values()).whenComplete((done, error) -> player.server.execute(() -> {
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
                            if (recipient == player && sameMenu) sync(recipient, kind, edited, selected, notice, result);
                            else sync(recipient, kind);
                        }
                    }));
                    return;
                }
            } else return;
            sync(player, kind);
        });
    }
    private static void sync(ServerPlayer player, int kind) { sync(player, kind, -1, -1, "", 0); }
    private static void sync(ServerPlayer player, int kind, int edited, int selected, String notice, int result) {
        if (!NetworkRegistry.hasChannel(player.connection, Sync.TYPE.id())) return;
        CompoundTag tag = new CompoundTag();
        if (result != 0) { tag.putInt("EditedId", edited); tag.putInt("SelectedId", selected); tag.putString("Notice", notice); tag.putInt("Result", result); }
        PacketDistributor.sendToPlayer(player, new Sync(kind, List.copyOf(StationRecipes.catalogue(kind).values()), tag));
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) for (int kind = 0; kind < 3; kind++) sync(player, kind);
    }
    public record Sync(int kind, List<StationRecipe> recipes, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.parse("cnpcplus:stations_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.kind);
                    buf.writeVarInt(p.recipes.size());
                    for (StationRecipe recipe : p.recipes) {
                        buf.writeNbt(recipe.saveMetadata(buf.registryAccess()));
                        for (ItemStack input : recipe.inputs) ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, input);
                        for (ItemStack output : recipe.outputs) ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, output);
                    }
                    buf.writeNbt(p.data);
                }, buf -> {
                    int kind = buf.readVarInt(), count = buf.readVarInt();
                    if (kind < 0 || kind >= 3 || count < 0 || count > 4096) throw new IllegalArgumentException("Invalid station catalogue");
                    List<StationRecipe> recipes = new ArrayList<>(count);
                    for (int n = 0; n < count; n++) {
                        StationRecipe recipe = StationRecipe.readMetadata(buf.readNbt(), buf.registryAccess());
                        for (int i = 0; i < recipe.inputs.length; i++) recipe.inputs[i] = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                        for (int i = 0; i < recipe.outputs.length; i++) recipe.outputs[i] = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                        recipes.add(recipe);
                    }
                    return new Sync(kind, recipes, buf.readNbt());
                });
        @Override public Type<Sync> type() { return TYPE; }
        public static void handle(Sync p, IPayloadContext context) { context.enqueueWork(() -> StationScreen.accept(p.kind, p.recipes, p.data)); }
    }
}
