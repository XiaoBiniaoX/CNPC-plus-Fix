package top.cnpcplus.transport.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcplus.transport.client.TransportClientCache;

import java.util.function.Supplier;

/** 服务端→客户端：传送点扩展元数据（Entries=名字→meta，Current=当前位置名，CurrentMeta）。 */
public class PacketTransportMetaResponse {
    private final CompoundTag data;
    private final boolean editor;

    public PacketTransportMetaResponse(CompoundTag data, boolean editor) {
        this.data = data;
        this.editor = editor;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(this.data);
        buf.writeBoolean(this.editor);
    }

    public static PacketTransportMetaResponse decode(FriendlyByteBuf buf) {
        return new PacketTransportMetaResponse(buf.readNbt(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> TransportClientCache.acceptMeta(this.data, this.editor)));
        ctx.get().setPacketHandled(true);
    }
}
