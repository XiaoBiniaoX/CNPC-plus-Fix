package top.cnpcplus.questtrigger.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PacketOpenTriggerGui(BlockPos pos, CompoundTag data) implements CustomPacketPayload {
    public static final Type<PacketOpenTriggerGui> TYPE = new Type<>(ResourceLocation.parse("cnpcplus:quest_trigger_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketOpenTriggerGui> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> { buf.writeBlockPos(packet.pos); buf.writeNbt(packet.data); },
            buf -> new PacketOpenTriggerGui(buf.readBlockPos(), buf.readNbt()));
    @Override public Type<PacketOpenTriggerGui> type() { return TYPE; }

    public static void handle(PacketOpenTriggerGui packet, IPayloadContext context) {
        context.enqueueWork(() -> top.cnpcplus.questtrigger.client.QuestTriggerClient.openGui(packet));
    }
}
