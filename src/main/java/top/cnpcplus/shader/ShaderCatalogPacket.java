package top.cnpcplus.shader;

import com.google.gson.JsonParser;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Client pack metadata only. GLSL and gameplay never cross this channel. */
public record ShaderCatalogPacket(String id, String json) {
    public void encode(FriendlyByteBuf buffer) { buffer.writeUtf(id, 256); buffer.writeUtf(json, 12288); }
    public static ShaderCatalogPacket decode(FriendlyByteBuf buffer) { return new ShaderCatalogPacket(buffer.readUtf(256), buffer.readUtf(12288)); }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            try {
                if (id.isEmpty()) { ShaderManager.clearPack(player.getUUID()); ShaderController.clearPlayer(player.getUUID()); }
                else ShaderManager.acceptPack(player.getUUID(), id, JsonParser.parseString(json).getAsJsonObject());
            } catch (RuntimeException e) { org.slf4j.LoggerFactory.getLogger("cnpcplus").error("[shader] pack {} {}", id, e.getMessage()); }
        });
        context.setPacketHandled(true);
    }
}
