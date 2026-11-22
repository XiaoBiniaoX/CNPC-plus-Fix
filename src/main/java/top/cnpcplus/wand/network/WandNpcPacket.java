package top.cnpcplus.wand.network;

import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import noppes.npcs.CustomItems;
import noppes.npcs.CustomNpcs;
import noppes.npcs.CustomNpcsPermissions;
import noppes.npcs.controllers.ServerCloneController;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcplus.ai.NpcControlAccess;

/** No client-supplied NBT or disk path: server captures an authorized, already-loaded NPC. */
public record WandNpcPacket(int entityId, long request, boolean copying, boolean toggleFreeze) {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("cnpcplus", "wand"), () -> "3", "3"::equals, "3"::equals);

    public static void init() {
        CHANNEL.registerMessage(0, WandNpcPacket.class, WandNpcPacket::encode, WandNpcPacket::decode,
                WandNpcPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Snapshot.class, Snapshot::encode, Snapshot::decode,
                Snapshot::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, FreezeState.class, FreezeState::encode, FreezeState::decode,
                FreezeState::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void send(int id, long request, boolean copying) { CHANNEL.sendToServer(new WandNpcPacket(id, request, copying, false)); }
    public static void toggleFreeze(int id, long request) { CHANNEL.sendToServer(new WandNpcPacket(id, request, false, true)); }
    private void encode(FriendlyByteBuf buf) { buf.writeInt(entityId); buf.writeLong(request); buf.writeBoolean(copying); buf.writeBoolean(toggleFreeze); }
    private static WandNpcPacket decode(FriendlyByteBuf buf) { return new WandNpcPacket(buf.readInt(), buf.readLong(), buf.readBoolean(), buf.readBoolean()); }

    private void handle(Supplier<NetworkEvent.Context> ctx) {
        var context = ctx.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null) return;
            CompoundTag snapshot = null;
            try {
                if (!CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_GUI)) return;
                var tool = player.getMainHandItem().getItem();
                if (tool != CustomItems.wand && tool != CustomItems.cloner) return;
                if (!(player.level().getEntity(entityId) instanceof EntityNPCInterface npc) || npc.isRemoved()) return;
                if (copying) {
                    if (tool != CustomItems.cloner || !CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_CLONE)
                            || CustomNpcs.OpsOnly && !player.server.getPlayerList().isOp(player.getGameProfile())) return;
                    CompoundTag captured = top.cnpcplus.clone.CloneMounts.capture(npc);
                    // Portable hook runs BEFORE native cleanTags removes UUIDs; failed captures leave the old clone intact.
                    ServerCloneController.Instance.cleanTags(captured);
                    PlayerData.get(player).cloned = captured.copy();
                    snapshot = captured;
                } else {
                    if (toggleFreeze) {
                        if (CustomNpcs.FreezeNPCs || tool != CustomItems.wand
                                || !CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_FREEZE)) return;
                        NpcControlAccess control = (NpcControlAccess)npc;
                        control.cnpcplus$setFrozen(!control.cnpcplus$isFrozen());
                        player.serverLevel().players().forEach(WandNpcPacket::sendFreezeState);
                    }
                    snapshot = npc.writeSpawnData().copy();
                    snapshot.putString("CNPCPlusPreviewType", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(npc.getType()).toString());
                }
            } catch (Exception e) {
                snapshot = null;
                org.slf4j.LoggerFactory.getLogger("cnpcplus").error("[wand] NPC snapshot failed", e);
            } finally {
                // Null is a nonce-matched failure reply, never permission to use a previous snapshot.
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Snapshot(entityId, request, copying, snapshot));
            }
        });
        context.setPacketHandled(true);
    }

    public static void sendFreezeState(ServerPlayer player) {
        if (player.getMainHandItem().getItem() != CustomItems.wand
                || !CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_GUI)) return;
        var ids = new it.unimi.dsi.fastutil.ints.IntArrayList();
        for (var entity : player.serverLevel().getAllEntities()) {
            if (entity instanceof EntityNPCInterface npc && !npc.isRemoved() && ((NpcControlAccess)npc).cnpcplus$isFrozen()) ids.add(npc.getId());
        }
        boolean allowed = CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_FREEZE);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new FreezeState(CustomNpcs.FreezeNPCs, allowed, ids.toIntArray()));
    }

    private record FreezeState(boolean global, boolean allowed, int[] ids) {
        private void encode(FriendlyByteBuf buf) { buf.writeBoolean(global); buf.writeBoolean(allowed); buf.writeVarIntArray(ids); }
        private static FreezeState decode(FriendlyByteBuf buf) {
            return new FreezeState(buf.readBoolean(), buf.readBoolean(), buf.readVarIntArray());
        }
        private void handle(Supplier<NetworkEvent.Context> ctx) {
            var context = ctx.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> top.cnpcplus.wand.client.WandPreviewReceiver.freezeState(global, allowed, ids)));
            context.setPacketHandled(true);
        }
    }

    public record Snapshot(int entityId, long request, boolean copying, CompoundTag data) {
        private void encode(FriendlyByteBuf buf) { buf.writeInt(entityId); buf.writeLong(request); buf.writeBoolean(copying); buf.writeNbt(data); }
        private static Snapshot decode(FriendlyByteBuf buf) { return new Snapshot(buf.readInt(), buf.readLong(), buf.readBoolean(), buf.readNbt()); }
        private void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> top.cnpcplus.wand.client.WandPreviewReceiver.accept(entityId, request, copying, data)));
            ctx.get().setPacketHandled(true);
        }
    }
}
