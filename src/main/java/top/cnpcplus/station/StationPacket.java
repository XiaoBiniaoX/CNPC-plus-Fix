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
            String notice = "";
            int result = 0;
            if (operation == 0) {
                player.openMenu(new SimpleMenuProvider((id, inv, p) -> new StationMenu(id, inv, kind),
                        Component.translatable("cnpcplus.station." + StationRecipes.KINDS[kind])), buf -> buf.writeVarInt(kind));
            } else if (player.containerMenu instanceof StationMenu menu && menu.kind == kind) {
                if (operation == 2) menu.select(data.getInt("Id"), player);
                else if (operation == 3 || operation == 4) {
                    var catalogue = StationRecipes.catalogue(kind);
                    var before = new LinkedHashMap<>(catalogue);
                    int previousId = menu.selected;
                    if (operation == 4) catalogue.remove(data.getInt("Id"));
                    else {
                        StationRecipe recipe = StationRecipe.readMetadata(data, player.registryAccess());
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
                        if (invalid) { sync(player, kind, menu.selected, "cnpcplus.station.invalid." + StationRecipes.KINDS[kind], -1); return; }
                        if (recipe.id < 0) recipe.id = catalogue.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
                        catalogue.put(recipe.id, recipe);
                        menu.selected = recipe.id;
                    }
                    try { StationRecipes.persist(kind); }
                    catch (java.io.IOException e) {
                        catalogue.clear(); catalogue.putAll(before); menu.selected = previousId;
                        org.slf4j.LoggerFactory.getLogger("cnpcplus").error("Cannot save station recipes", e);
                        sync(player, kind, menu.selected, "cnpcplus.station.save_failed", -1);
                        return;
                    }
                    if (operation == 4) menu.select(-1, player);
                    result = operation == 3 ? 1 : 2;
                    notice = operation == 3 ? "cnpcplus.station.saved" : "cnpcplus.station.deleted";
                }
            } else return;
            if (operation == 3 || operation == 4) {
                for (ServerPlayer recipient : player.server.getPlayerList().getPlayers()) if (recipient != player) sync(recipient, kind);
                sync(player, kind, ((StationMenu)player.containerMenu).selected, notice, result);
            } else if (operation == 2) sync(player, kind, ((StationMenu)player.containerMenu).selected, "", 0);
            else sync(player, kind);
        });
    }
    private static void sync(ServerPlayer player, int kind) { sync(player, kind, Integer.MIN_VALUE, "", 0); }
    private static void sync(ServerPlayer player, int kind, int selected, String notice, int result) {
        if (!NetworkRegistry.hasChannel(player.connection, Sync.TYPE.id())) return;
        CompoundTag tag = new CompoundTag();
        if (selected != Integer.MIN_VALUE) { tag.putInt("SelectedId", selected); tag.putString("Notice", notice); tag.putInt("Result", result); }
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
