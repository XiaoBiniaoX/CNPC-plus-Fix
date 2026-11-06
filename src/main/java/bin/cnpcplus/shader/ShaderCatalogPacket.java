package bin.cnpcplus.shader;

import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Metadata only. Clients cannot upload gameplay code or GLSL to the server. */
public final class ShaderCatalogPacket implements IMessage {
    private String id, json;
    public ShaderCatalogPacket() {} public ShaderCatalogPacket(String id, String json) { this.id = id; this.json = json; }
    @Override public void toBytes(ByteBuf buf) { ByteBufUtils.writeUTF8String(buf, id); ByteBufUtils.writeUTF8String(buf, json); }
    @Override public void fromBytes(ByteBuf buf) {
        id = ByteBufUtils.readUTF8String(buf); json = ByteBufUtils.readUTF8String(buf);
        if (id.length() > 256 || json.length() > 12288) throw new IllegalArgumentException("Shader catalogue entry too large");
    }
    public static final class Handler implements IMessageHandler<ShaderCatalogPacket, IMessage> {
        @Override public IMessage onMessage(ShaderCatalogPacket msg, MessageContext ctx) {
            net.minecraft.entity.player.EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                try {
                    if (msg.id.isEmpty()) { if (msg.json.equals("done")) ShaderController.syncPlayer(player); else { ShaderManager.clearPack(player.getUniqueID()); ShaderController.clearPlayer(player.getUniqueID()); } }
                    else ShaderManager.acceptPack(player.getUniqueID(), msg.id, new JsonParser().parse(msg.json).getAsJsonObject());
                } catch (RuntimeException error) { bin.cnpcplus.CnpcPlus.LOGGER.error("[shader] catalog {}", msg.id, error); }
            }); return null;
        }
    }
}
