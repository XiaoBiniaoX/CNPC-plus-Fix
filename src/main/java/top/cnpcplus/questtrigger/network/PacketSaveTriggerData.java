package top.cnpcplus.questtrigger.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcplus.questtrigger.TileQuestTrigger;

import java.util.function.Supplier;

public class PacketSaveTriggerData {
    private final BlockPos pos;
    private final CompoundTag data;

    public PacketSaveTriggerData(BlockPos pos, CompoundTag data) {
        this.pos = pos;
        this.data = data;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeNbt(data);
    }

    public static PacketSaveTriggerData decode(FriendlyByteBuf buf) {
        return new PacketSaveTriggerData(buf.readBlockPos(), buf.readNbt());
    }

    public static void handle(PacketSaveTriggerData msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || msg.data == null) return;
            if (!player.getAbilities().instabuild) return;
            if (player.distanceToSqr(msg.pos.getX() + 0.5, msg.pos.getY() + 0.5, msg.pos.getZ() + 0.5) > 256) return;
            BlockEntity tile = player.level().getBlockEntity(msg.pos);
            if (tile instanceof TileQuestTrigger) {
                tile.load(msg.data);
                tile.setChanged();
                player.level().sendBlockUpdated(msg.pos, tile.getBlockState(), tile.getBlockState(), 3);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
