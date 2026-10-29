package top.cnpcplus.transport.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import noppes.npcs.CustomNpcsPermissions;
import noppes.npcs.packets.PacketServerBasic;
import top.cnpcplus.transport.TransportImageIO;
import top.cnpcplus.transport.TransportKeys;

import java.util.function.Supplier;

/**
 * 客户端→服务端：上传传送点图片。权限同 NPC_ADVANCED（编辑传送点即需此权限）。
 * 与 PacketServerBasic 分开是因为这是 cnpcplus 自有通道的包，直接走 SimpleChannel。
 */
public class PacketTransportImageUpload {
    private final String key;
    private final byte[] data;

    public PacketTransportImageUpload(String key, byte[] data) {
        this.key = key;
        this.data = data;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.key, 64);
        buf.writeVarInt(this.data.length);
        buf.writeBytes(this.data);
    }

    public static PacketTransportImageUpload decode(FriendlyByteBuf buf) {
        String key = buf.readUtf(64);
        int len = buf.readVarInt();
        if (len < 0 || len > TransportKeys.MAX_IMAGE_BYTES) {
            byte[] skip = new byte[buf.readableBytes()];
            buf.readBytes(skip);
            return new PacketTransportImageUpload(key, new byte[0]);
        }
        byte[] data = new byte[len];
        buf.readBytes(data);
        return new PacketTransportImageUpload(key, data);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var sender = ctx.get().getSender();
            if (sender == null || !sender.hasPermissions(2)) return;
            String safe = TransportKeys.sanitize(this.key);
            if (safe.isEmpty() || this.data.length == 0) return;
            boolean ok = TransportImageIO.write(safe, this.data);
            // 241r：回执 + 日志（之前"进游戏图片没了"完全无从查证）
            noppes.npcs.shared.common.util.LogWriter.warn(
                    "[cnpcplus] transport image upload key=" + safe + " bytes=" + this.data.length
                            + " saved=" + ok);
            try {
                net.minecraft.nbt.CompoundTag out = new net.minecraft.nbt.CompoundTag();
                out.putBoolean("ImageSaved", ok);
                out.putString("ImageKey", safe);
                TransportPacketHandler.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sender),
                        new PacketTransportMetaResponse(out, true));
            } catch (Throwable ignored) {
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
