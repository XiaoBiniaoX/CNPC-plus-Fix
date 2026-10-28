package bin.cnpcplus.transport;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** 图片以 24KiB 分片发送，避免 C2S 单包 32KiB 限制。 */
public final class PacketTransportImage implements IMessage {
    public static final int CHUNK = 24 * 1024, MAX_BYTES = 4 * 1024 * 1024;
    public int npcId, offset, total;
    public String key = "";
    public byte[] bytes = new byte[0];
    public PacketTransportImage() { }
    public PacketTransportImage(int npcId, String key, int offset, byte[] data) {
        this.npcId = npcId; this.key = key; this.offset = offset; this.total = data.length;
        this.bytes = java.util.Arrays.copyOfRange(data, offset, Math.min(data.length, offset + CHUNK));
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeInt(npcId); ByteBufUtils.writeUTF8String(buf, key); buf.writeInt(offset); buf.writeInt(total); buf.writeInt(bytes.length); buf.writeBytes(bytes);
    }
    @Override public void fromBytes(ByteBuf buf) {
        npcId = buf.readInt(); key = ByteBufUtils.readUTF8String(buf); offset = buf.readInt(); total = buf.readInt();
        int length = buf.readInt();
        if (key.length() > 64 || total < 1 || total > MAX_BYTES || offset < 0 || offset > total
                || length < 1 || length > CHUNK || length > total - offset || length > buf.readableBytes()) throw new IllegalArgumentException("Invalid transport image chunk");
        bytes = new byte[length]; buf.readBytes(bytes);
    }
    public static final class Handler implements IMessageHandler<PacketTransportImage, IMessage> {
        @Override public IMessage onMessage(PacketTransportImage message, MessageContext context) {
            net.minecraft.entity.player.EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> TransportImages.receive(player, message)); return null;
        }
    }
    public static final class ClientHandler implements IMessageHandler<PacketTransportImage, IMessage> {
        @Override @SideOnly(Side.CLIENT) public IMessage onMessage(PacketTransportImage message, MessageContext context) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> bin.cnpcplus.transport.client.ClientTransportImages.receive(message)); return null;
        }
    }
}
