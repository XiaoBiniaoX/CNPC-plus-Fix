package top.cnpcplus.smithing.network;

import bin.cnpcplus.CnpcPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.cnpcplus.smithing.ContainerSmithingRecipes;
import top.cnpcplus.smithing.SmithingRecipeManager;
import top.cnpcplus.smithing.SmithingRecipeRegistry;

/** 客户端→服务端：删除自定义锻造配方（227 从 PacketSmeltingRemove 复制，保存路径同为热添加）。 */
public record PacketSmithingRemove(int id) implements CustomPacketPayload {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cnpcplus");

    public static final Type<PacketSmithingRemove> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "smithing_remove"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSmithingRemove> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeVarInt(packet.id), buf -> new PacketSmithingRemove(buf.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PacketSmithingRemove packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender) || !sender.hasPermissions(2)) {
                LOGGER.debug("[smithing] Remove拒绝: 无发送者或权限不足 player={}",
                        context.player() == null ? "?" : context.player().getGameProfile().getName());
                return;
            }
            if (!(sender.containerMenu instanceof ContainerSmithingRecipes)) return;
            if (!SmithingRecipeRegistry.remove(sender.server.registryAccess(), packet.id)) {
                LOGGER.debug("[smithing] Remove终止: Registry无此id={}", packet.id);
                return;
            }
            SmithingRecipeManager.injectAll(sender.server.getRecipeManager());
            SmithingRecipeManager.syncToPlayers(sender.server);
            // 228 用户要求：一人删除，全部玩家立即失效（原版配方表全员群发）
            SmithingRecipeManager.resendVanillaRecipes(sender.server);
        });
    }
}
