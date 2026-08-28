package top.cnpcplus.questtrigger.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketOpenTriggerGui {
    public final BlockPos pos;
    public final CompoundTag data;

    public PacketOpenTriggerGui(BlockPos pos, CompoundTag data) {
        this.pos = pos;
        this.data = data;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeNbt(data);
    }

    public static PacketOpenTriggerGui decode(FriendlyByteBuf buf) {
        return new PacketOpenTriggerGui(buf.readBlockPos(), buf.readNbt());
    }

    public static void handle(PacketOpenTriggerGui msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                top.cnpcplus.questtrigger.client.QuestTriggerPacketClientHandler.open(msg)));
        ctx.get().setPacketHandled(true);
    }
}
