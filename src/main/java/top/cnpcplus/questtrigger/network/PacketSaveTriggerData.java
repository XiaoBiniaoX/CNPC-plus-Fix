package top.cnpcplus.questtrigger.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.cnpcplus.questtrigger.TileQuestTrigger;

public record PacketSaveTriggerData(BlockPos pos, CompoundTag data) implements CustomPacketPayload {
    public static final Type<PacketSaveTriggerData> TYPE = new Type<>(ResourceLocation.parse("cnpcplus:quest_trigger_save"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSaveTriggerData> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> { buf.writeBlockPos(packet.pos); buf.writeNbt(packet.data); },
            buf -> new PacketSaveTriggerData(buf.readBlockPos(), buf.readNbt()));
    @Override public Type<PacketSaveTriggerData> type() { return TYPE; }

    public static void handle(PacketSaveTriggerData packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || packet.data == null
                    || !player.getAbilities().instabuild
                    || player.distanceToSqr(packet.pos.getX() + .5, packet.pos.getY() + .5, packet.pos.getZ() + .5) > 256
                    || !player.level().hasChunkAt(packet.pos)) return;
            if (player.level().getBlockEntity(packet.pos) instanceof TileQuestTrigger tile) {
                tile.loadWithComponents(packet.data, player.registryAccess());
                tile.setChanged();
                player.level().sendBlockUpdated(packet.pos, tile.getBlockState(), tile.getBlockState(), 3);
            }
        });
    }
}
