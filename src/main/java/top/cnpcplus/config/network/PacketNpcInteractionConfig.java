package top.cnpcplus.config.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import top.cnpcplus.config.NpcInteractionConfig;

/** One boolean: client edit request or server-authoritative sync. */
public record PacketNpcInteractionConfig(boolean enabled) {
    public void encode(FriendlyByteBuf buf) { buf.writeBoolean(enabled); }
    public static PacketNpcInteractionConfig decode(FriendlyByteBuf buf) {
        return new PacketNpcInteractionConfig(buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection() == NetworkDirection.PLAY_TO_SERVER) {
                var player = context.getSender();
                if (player == null) return;
                if (!player.hasPermissions(2) && !player.server.isSingleplayerOwner(player.getGameProfile())) {
                    player.displayClientMessage(Component.translatable("cnpcplus.config.server.noperm"), false);
                    return;
                }
                try {
                    NpcInteractionConfig.ALLOW_INVISIBLE_NPC_INTERACTION.set(enabled);
                    NpcInteractionConfig.ALLOW_INVISIBLE_NPC_INTERACTION.save();
                } catch (RuntimeException e) {
                    player.displayClientMessage(Component.translatable("cnpcplus.config.server.saveFailed"), false);
                    return;
                }
                ConfigPacketHandler.CHANNEL.send(PacketDistributor.ALL.noArg(), this);
            } else {
                // Synced client configs are in memory; never save them to disk.
                NpcInteractionConfig.ALLOW_INVISIBLE_NPC_INTERACTION.set(enabled);
            }
        });
        context.setPacketHandled(true);
    }
}
