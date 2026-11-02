package top.cnpcplus.config.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import top.cnpcplus.config.CnpcPlusServerConfig;

import java.util.function.Supplier;

public class PacketUpdateServerConfig {
    private static final int MAX_MESSAGE_BYTES = 512;

    private final boolean realArmorEnabled;
    private final int followerTeleportRange;
    private final int maxHiredNpcs;

    public PacketUpdateServerConfig(boolean realArmorEnabled, int followerTeleportRange,
                                    int maxHiredNpcs) {
        this.realArmorEnabled = realArmorEnabled;
        this.followerTeleportRange = followerTeleportRange;
        this.maxHiredNpcs = maxHiredNpcs;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.realArmorEnabled);
        buf.writeVarInt(this.followerTeleportRange);
        buf.writeVarInt(this.maxHiredNpcs);
        buf.writeUtf("", MAX_MESSAGE_BYTES); // Keep the old wire slot; never overwrite legacy migration data.
    }

    public static PacketUpdateServerConfig decode(FriendlyByteBuf buf) {
        var packet = new PacketUpdateServerConfig(
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt());
        buf.readUtf(MAX_MESSAGE_BYTES);
        return packet;
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || player.server == null) return;
            if (!player.hasPermissions(2) && !player.server.isSingleplayerOwner(player.getGameProfile())) {
                player.displayClientMessage(Component.translatable("cnpcplus.config.server.noperm"), false);
                return;
            }

            int range = Mth.clamp(this.followerTeleportRange, 1, 64);
            int maxHired = Mth.clamp(this.maxHiredNpcs, 1, 10000);
            try {
                CnpcPlusServerConfig.RealArmorEnabled.set(this.realArmorEnabled);
                CnpcPlusServerConfig.FollowerTeleportRange.set(range);
                CnpcPlusServerConfig.MaxHiredNpcs.set(maxHired);
                CnpcPlusServerConfig.RealArmorEnabled.save();
            } catch (Throwable t) {
                player.displayClientMessage(Component.translatable("cnpcplus.config.server.saveFailed"), false);
                return;
            }

            ConfigPacketHandler.CHANNEL.send(
                    PacketDistributor.ALL.noArg(),
                    new PacketServerConfigSync(this.realArmorEnabled, range, maxHired));
        });
        ctx.get().setPacketHandled(true);
    }
}
