package bin.cnpcplus.crafting.network;

import bin.cnpcplus.crafting.RecipeContainer;
import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.smelting.SmeltingPermissions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** 大型粒子配方分片，整包收齐并校验后才原子提交；单片小于 vanilla C2S 32767 字节。 */
public final class PacketRecipeUpload implements IMessage {
    private static final int CHUNK = 24 * 1024, MAX = 1024 * 1024;
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final Map<UUID, Upload> UPLOADS = new HashMap<>();
    private int id, offset, total;
    private byte[] bytes;
    private static final class Upload {
        final int id, window;
        final byte[] data;
        int position;
        Upload(int id, int total, int window) { this.id = id; data = new byte[total]; this.window = window; }
    }
    public PacketRecipeUpload() { }
    private PacketRecipeUpload(int id, byte[] bytes, int offset) {
        this.id = id; this.offset = offset; total = bytes.length;
        this.bytes = Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + CHUNK));
    }
    public static void send(PacketRecipeAction message) {
        ByteBuf encoded = Unpooled.buffer();
        try {
            message.toBytes(encoded);
            if (encoded.readableBytes() < 30000) { CraftingViewNetwork.CHANNEL.sendToServer(message); return; }
            if (encoded.readableBytes() > MAX) throw new IllegalArgumentException("Recipe packet exceeds 1 MiB");
            byte[] bytes = new byte[encoded.readableBytes()]; encoded.readBytes(bytes);
            int id = IDS.incrementAndGet();
            for (int offset = 0; offset < bytes.length; offset += CHUNK) CraftingViewNetwork.CHANNEL.sendToServer(new PacketRecipeUpload(id, bytes, offset));
        } finally { encoded.release(); }
    }
    @Override public void toBytes(ByteBuf buf) { buf.writeInt(id); buf.writeInt(offset); buf.writeInt(total); buf.writeInt(bytes.length); buf.writeBytes(bytes); }
    @Override public void fromBytes(ByteBuf buf) {
        id = buf.readInt(); offset = buf.readInt(); total = buf.readInt(); int length = buf.readInt();
        if (total < 1 || total > MAX || offset < 0 || offset > total || length < 1 || length > CHUNK
                || length > total - offset || length > buf.readableBytes()) throw new IllegalArgumentException("Invalid recipe fragment");
        bytes = new byte[length]; buf.readBytes(bytes);
    }
    public static final class Handler implements IMessageHandler<PacketRecipeUpload, IMessage> {
        @Override public IMessage onMessage(PacketRecipeUpload message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!SmeltingPermissions.canEdit(player) || !(player.openContainer instanceof RecipeContainer)) return;
                UUID owner = player.getUniqueID();
                Upload upload = UPLOADS.get(owner);
                if (message.offset == 0) { upload = new Upload(message.id, message.total, player.openContainer.windowId); UPLOADS.put(owner, upload); }
                if (upload == null || upload.id != message.id || upload.position != message.offset
                        || upload.data.length != message.total || upload.window != player.openContainer.windowId) { UPLOADS.remove(owner); return; }
                System.arraycopy(message.bytes, 0, upload.data, upload.position, message.bytes.length);
                upload.position += message.bytes.length;
                if (upload.position != upload.data.length) return;
                UPLOADS.remove(owner);
                ByteBuf encoded = Unpooled.wrappedBuffer(upload.data);
                try {
                    PacketRecipeAction action = new PacketRecipeAction(); action.fromBytes(encoded);
                    if (!encoded.isReadable()) PacketRecipeAction.Handler.handle(action, player);
                } finally { encoded.release(); }
            });
            return null;
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { UPLOADS.remove(event.player.getUniqueID()); }
    public static void clear() { UPLOADS.clear(); }
}
