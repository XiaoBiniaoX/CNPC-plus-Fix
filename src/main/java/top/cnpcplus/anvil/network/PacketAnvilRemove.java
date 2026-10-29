package top.cnpcplus.anvil.network;

import bin.cnpcplus.CnpcPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.cnpcplus.anvil.AnvilRecipeRegistry;

/** 客户端→服务端：删除自定义铁砧配方（阶段231 从 PacketSmithingRemove 复制）。 */
public record PacketAnvilRemove(int id) implements CustomPacketPayload {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cnpcplus");
    public static final Type<PacketAnvilRemove> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "anvil_remove"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAnvilRemove> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeVarInt(packet.id), buf -> new PacketAnvilRemove(buf.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PacketAnvilRemove packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender) || !sender.hasPermissions(2)) {
                LOGGER.debug("[anvil] Remove拒绝: 无发送者或权限不足 player={}",
                        context.player() == null ? "?" : context.player().getGameProfile().getName());
                return;
            }
            if (!AnvilRecipeRegistry.remove(sender.server.registryAccess(), packet.id)) {
                LOGGER.debug("[anvil] Remove终止: Registry无此id={}", packet.id);
                return;
            }
            // 233d-UI9 日志清理：info→debug（默认不输出，只留 error 级）
            LOGGER.debug("[anvil] 配方删除 id={}", packet.id);
            // 一人删除全服生效：全员同步（配方生效在服务端事件直查 Registry，删了自然查不到）
            AnvilRecipeRegistry.syncToPlayers(sender.server);
        });
    }
}
