package top.cnpcplus.wand.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import noppes.npcs.CustomItems;
import noppes.npcs.CustomNpcs;
import noppes.npcs.CustomNpcsPermissions;
import noppes.npcs.controllers.ServerCloneController;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.entity.EntityNPCInterface;

/** Server captures an authorized, loaded NPC. No client NBT, entity creation or file path accepted. */
public record WandNpcPacket(int entityId, long request, boolean copying) implements CustomPacketPayload {
    public static final Type<WandNpcPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cnpcplus", "wand"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WandNpcPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeInt(p.entityId); buf.writeLong(p.request); buf.writeBoolean(p.copying); },
            buf -> new WandNpcPacket(buf.readInt(), buf.readLong(), buf.readBoolean()));
    @Override public Type<WandNpcPacket> type() { return TYPE; }
    public static void send(int id, long nonce, boolean copying) { PacketDistributor.sendToServer(new WandNpcPacket(id, nonce, copying)); }
    public static void handle(WandNpcPacket p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            CompoundTag snapshot = null;
            try {
                if (!CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_GUI)
                        || CustomNpcs.OpsOnly && !player.server.getPlayerList().isOp(player.getGameProfile())) return;
                var tool = player.getMainHandItem().getItem();
                if (tool != CustomItems.wand && tool != CustomItems.cloner) return;
                if (!(player.level().getEntity(p.entityId) instanceof EntityNPCInterface npc) || npc.isRemoved()) return;
                if (p.copying) {
                    if (tool != CustomItems.cloner || !CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_CLONE)) return;
                    CompoundTag captured = top.cnpcplus.clone.CloneMounts.capture(npc);
                    ServerCloneController.Instance.cleanTags(captured);
                    PlayerData.get(player).cloned = captured.copy(); snapshot = captured;
                } else {
                    snapshot = npc.writeSpawnData().copy();
                    snapshot.putString("CNPCPlusPreviewType", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(npc.getType()).toString());
                }
            } catch (Exception e) { org.slf4j.LoggerFactory.getLogger("cnpcplus").error("[wand] NPC snapshot failed", e); }
            finally {
                if (NetworkRegistry.hasChannel(player.connection, Snapshot.TYPE.id()))
                    PacketDistributor.sendToPlayer(player, new Snapshot(p.entityId, p.request, p.copying, snapshot));
            }
        });
    }
    public record Snapshot(int entityId, long request, boolean copying, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cnpcplus", "wand_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeInt(p.entityId); buf.writeLong(p.request); buf.writeBoolean(p.copying); buf.writeNbt(p.data); },
                buf -> new Snapshot(buf.readInt(), buf.readLong(), buf.readBoolean(), buf.readNbt()));
        @Override public Type<Snapshot> type() { return TYPE; }
        public static void handle(Snapshot p, IPayloadContext context) { context.enqueueWork(() -> top.cnpcplus.wand.client.WandPreviewReceiver.accept(p)); }
    }
}
