package top.cnpcplus.smithing.network;

import bin.cnpcplus.CnpcPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.cnpcplus.smithing.ContainerSmithingRecipes;
import top.cnpcplus.smithing.SmithingRecipeData;
import top.cnpcplus.smithing.SmithingRecipeManager;
import top.cnpcplus.smithing.SmithingRecipeRegistry;

/**
 * 客户端→服务端：保存（新建/修改）自定义锻造配方（227 从 PacketSmeltingSave 复制）。
 * 物品槽位以服务端容器（当前打开的 ContainerSmithingRecipes）为准，客户端 NBT 只带 id/name。
 * 保存后立即注入 RecipeManager 并广播 = 热添加（原版锻造台下一次操作即可用）。
 */
public record PacketSmithingSave(int id, String name, boolean nameOnly) implements CustomPacketPayload {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cnpcplus");

    public static final Type<PacketSmithingSave> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "smithing_save"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSmithingSave> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeVarInt(packet.id);
                buf.writeUtf(packet.name == null ? "" : packet.name, 64);
                buf.writeBoolean(packet.nameOnly);
            }, buf -> new PacketSmithingSave(buf.readVarInt(), buf.readUtf(64), buf.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PacketSmithingSave packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender) || !sender.hasPermissions(2)) {
                LOGGER.debug("[smithing] Save拒绝: 无发送者或权限不足 player={}",
                        context.player() == null ? "?" : context.player().getGameProfile().getName());
                return;
            }
            SmithingRecipeData d = new SmithingRecipeData();
            d.id = packet.id;
            d.name = packet.name == null ? "" : packet.name;
            d.nameOnly = packet.nameOnly;
            // 物品槽位一律以服务端当前打开的编辑容器为准（权威）。
            // 没有打开编辑容器就直接拒绝——否则会退化成信任客户端发来的 ItemStack（可伪造任意 NBT）。
            if (!(sender.containerMenu instanceof ContainerSmithingRecipes c)) {
                LOGGER.debug("[smithing] Save拒绝: 未打开编辑容器 player={}", sender.getGameProfile().getName());
                return;
            }
            d.template = c.getTemplate().copy();
            d.base = c.getBase().copy();
            d.addition = c.getAddition().copy();
            d.result = c.getOutput().copy();
            // 228 注册条件（用户规则）：结果槽必须放置 + 三输入槽至少一个非空。
            boolean anyInput = !d.template.isEmpty() || !d.base.isEmpty() || !d.addition.isEmpty();
            if (!anyInput || d.result.isEmpty()) {
                LOGGER.debug("[smithing] Save拒绝: 槽位条件不满足 t={} b={} a={} r={} (规则=输入至少一且结果必放)",
                        !d.template.isEmpty(), !d.base.isEmpty(), !d.addition.isEmpty(), !d.result.isEmpty());
                return;
            }
            var registries = sender.server.registryAccess();
            boolean ok = d.id >= 0 ? SmithingRecipeRegistry.update(registries, d)
                    : SmithingRecipeRegistry.create(registries, d) != null;
            if (!ok) {
                LOGGER.debug("[smithing] Save拒绝: Registry写入失败 id={}", d.id);
                return;
            }
            SmithingRecipeManager.injectAll(sender.server.getRecipeManager());
            SmithingRecipeManager.syncToPlayers(sender.server);
            // 228 用户要求：一人修改，全部玩家立即可合成（原版配方表全员群发）
            SmithingRecipeManager.resendVanillaRecipes(sender.server);
        });
    }
}
