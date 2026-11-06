package top.cnpcplus.shader;

import com.google.gson.JsonParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client pack metadata only. GLSL and gameplay never cross this channel. */
public record ShaderCatalogPacket(String id, String json) implements CustomPacketPayload {
    public static final Type<ShaderCatalogPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cnpcplus", "shader_catalog"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShaderCatalogPacket> STREAM_CODEC = StreamCodec.of((buf, packet) -> packet.encode(buf), ShaderCatalogPacket::decode);
    @Override public Type<ShaderCatalogPacket> type() { return TYPE; }
    public void encode(FriendlyByteBuf buffer) { buffer.writeUtf(id, 256); buffer.writeUtf(json, 12288); }
    public static ShaderCatalogPacket decode(FriendlyByteBuf buffer) { return new ShaderCatalogPacket(buffer.readUtf(256), buffer.readUtf(12288)); }
    public static void handle(ShaderCatalogPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            try {
                if (packet.id.isEmpty()) { ShaderManager.clearPack(player.getUUID()); ShaderController.clearPlayer(player.getUUID()); }
                else ShaderManager.acceptPack(player.getUUID(), packet.id, JsonParser.parseString(packet.json).getAsJsonObject());
            } catch (RuntimeException e) { org.slf4j.LoggerFactory.getLogger("cnpcplus").error("[shader] pack {} {}", packet.id, e.getMessage()); }
        });
    }
}
