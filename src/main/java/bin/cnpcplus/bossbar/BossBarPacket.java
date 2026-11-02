package bin.cnpcplus.bossbar;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public final class BossBarPacket implements IMessage {
    public UUID id;
    public int rgb;
    public boolean removed;
    public BossBarPacket() { }
    public BossBarPacket(UUID id, int rgb, boolean removed) { this.id = id; this.rgb = rgb; this.removed = removed; }
    @Override public void fromBytes(ByteBuf buf) { id = new UUID(buf.readLong(), buf.readLong()); rgb = buf.readInt() & 0xFFFFFF; removed = buf.readBoolean(); }
    @Override public void toBytes(ByteBuf buf) { buf.writeLong(id.getMostSignificantBits()); buf.writeLong(id.getLeastSignificantBits()); buf.writeInt(rgb); buf.writeBoolean(removed); }
    public static final class Handler implements IMessageHandler<BossBarPacket, IMessage> {
        @SideOnly(Side.CLIENT)
        @Override public IMessage onMessage(BossBarPacket packet, MessageContext context) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> bin.cnpcplus.bossbar.client.BossBarColors.receive(packet));
            return null;
        }
    }
}
