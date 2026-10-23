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
    private static final int MAX_MESSAGE_CHARS = 128;
    private static final int MAX_MESSAGE_BYTES = 512;

    private final boolean realArmorEnabled;
    private final int followerTeleportRange;
    private final int maxHiredNpcs;
    private final String hireLimitMessage;

    public PacketUpdateServerConfig(boolean realArmorEnabled, int followerTeleportRange,
                                    int maxHiredNpcs, String hireLimitMessage) {
        this.realArmorEnabled = realArmorEnabled;
        this.followerTeleportRange = followerTeleportRange;
        this.maxHiredNpcs = maxHiredNpcs;
        this.hireLimitMessage = hireLimitMessage == null ? "" : hireLimitMessage;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.realArmorEnabled);
        buf.writeVarInt(this.followerTeleportRange);
        buf.writeVarInt(this.maxHiredNpcs);
        buf.writeUtf(this.hireLimitMessage, MAX_MESSAGE_BYTES);
    }

    public static PacketUpdateServerConfig decode(FriendlyByteBuf buf) {
        return new PacketUpdateServerConfig(
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf(MAX_MESSAGE_BYTES));
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
            String message = this.hireLimitMessage.trim();
            if (message.isEmpty()) message = "你雇佣的人过多";
            if (message.length() > MAX_MESSAGE_CHARS) {
                message = message.substring(0, MAX_MESSAGE_CHARS);
            }

            try {
                CnpcPlusServerConfig.RealArmorEnabled.set(this.realArmorEnabled);
                CnpcPlusServerConfig.FollowerTeleportRange.set(range);
                CnpcPlusServerConfig.MaxHiredNpcs.set(maxHired);
                CnpcPlusServerConfig.HireLimitMessage.set(message);
                CnpcPlusServerConfig.RealArmorEnabled.save();
            } catch (Throwable t) {
                player.displayClientMessage(Component.literal("[CNPCplus] 保存服务端配置失败"), false);
                return;
            }

            ConfigPacketHandler.CHANNEL.send(
                    PacketDistributor.ALL.noArg(),
                    new PacketServerConfigSync(this.realArmorEnabled, range, maxHired, message));
        });
        ctx.get().setPacketHandled(true);
    }
}
