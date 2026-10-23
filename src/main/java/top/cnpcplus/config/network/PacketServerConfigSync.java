package top.cnpcplus.config.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcplus.config.CnpcPlusServerConfig;

import java.util.function.Supplier;

public class PacketServerConfigSync {
    private static final int MAX_MESSAGE_LENGTH = 512;

    private final boolean realArmorEnabled;
    private final int followerTeleportRange;
    private final int maxHiredNpcs;
    private final String hireLimitMessage;

    public PacketServerConfigSync(boolean realArmorEnabled, int followerTeleportRange,
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
        buf.writeUtf(this.hireLimitMessage, MAX_MESSAGE_LENGTH);
    }

    public static PacketServerConfigSync decode(FriendlyByteBuf buf) {
        return new PacketServerConfigSync(
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf(MAX_MESSAGE_LENGTH));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            try {
                CnpcPlusServerConfig.RealArmorEnabled.set(this.realArmorEnabled);
                CnpcPlusServerConfig.FollowerTeleportRange.set(this.followerTeleportRange);
                CnpcPlusServerConfig.MaxHiredNpcs.set(this.maxHiredNpcs);
                CnpcPlusServerConfig.HireLimitMessage.set(this.hireLimitMessage);
            } catch (Throwable ignored) {
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
