package top.cnpcplus.anvil.network;

import bin.cnpcplus.CnpcPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.cnpcplus.anvil.AnvilRecipeData;
import top.cnpcplus.anvil.AnvilRecipeRegistry;
import top.cnpcplus.anvil.ContainerAnvilRecipes;

/**
 * 客户端→服务端：保存（新建/修改）自定义铁砧配方（阶段231 从 PacketSmithingSave 复制改三槽）。
 * 物品槽位以服务端容器（当前打开的 ContainerAnvilRecipes）为准，客户端 NBT 只带 id/name/expCost/nameOnly。
 * 保存后立即 syncToPlayers 全员广播（配方不进 RecipeManager——AnvilCraftHandler 直查 Registry）。
 */
public record PacketAnvilSave(int id, String name, int expCost, boolean nameOnly) implements CustomPacketPayload {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cnpcplus");
    public static final Type<PacketAnvilSave> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "anvil_save"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAnvilSave> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeVarInt(packet.id);
                buf.writeUtf(packet.name == null ? "" : packet.name, 64);
                buf.writeInt(packet.expCost);
                buf.writeBoolean(packet.nameOnly);
            },
            buf -> new PacketAnvilSave(buf.readVarInt(), buf.readUtf(64), buf.readInt(), buf.readBoolean()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PacketAnvilSave packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender) || !sender.hasPermissions(2)) {
                LOGGER.debug("[anvil] Save拒绝: 无发送者或权限不足 player={}",
                        context.player() == null ? "?" : context.player().getGameProfile().getName());
                return;
            }
            AnvilRecipeData d = new AnvilRecipeData();
            d.id = packet.id;
            d.name = packet.name == null ? "" : packet.name;
            d.expCost = Math.max(-32767, Math.min(32767, packet.expCost));
            d.nameOnly = packet.nameOnly;
            // 物品槽位一律以服务端当前打开的编辑容器为准（权威）。
            // 没有打开编辑容器就直接拒绝——否则会退化成信任客户端发来的 ItemStack（可伪造任意 NBT）。
            if (!(sender.containerMenu instanceof ContainerAnvilRecipes c)) {
                LOGGER.debug("[anvil] Save拒绝: 未打开编辑容器 player={}", sender.getGameProfile().getName());
                return;
            }
            d.inputA = c.getInputA().copy();
            d.inputB = c.getInputB().copy();
            d.result = c.getOutput().copy();
            // 231 注册条件（铁砧语义）：A、B、结果三槽全部必放（A+B 敲打=C 必须两输入）。
            if (d.inputA.isEmpty() || d.inputB.isEmpty() || d.result.isEmpty()) {
                LOGGER.debug("[anvil] Save拒绝: 槽位条件不满足 a={} b={} r={} (规则=A/B/结果全必放)",
                        !d.inputA.isEmpty(), !d.inputB.isEmpty(), !d.result.isEmpty());
                return;
            }
            var registries = sender.server.registryAccess();
            boolean ok = d.id >= 0 ? AnvilRecipeRegistry.update(registries, d)
                    : AnvilRecipeRegistry.create(registries, d) != null;
            if (!ok) {
                LOGGER.debug("[anvil] Save拒绝: Registry写入失败 id={}", d.id);
                return;
            }
            // 233d-UI9 日志清理：info→debug（默认不输出，只留 error 级）
            LOGGER.debug("[anvil] 配方保存 id={} name={} exp={} nameOnly={}", d.id, d.name, d.expCost, d.nameOnly);
            c.selectedId = d.id;
            // 一人创建全服可用：全员同步编辑列表（配方生效在服务端事件直查 Registry，无需 RecipeManager 注入）
            AnvilRecipeRegistry.syncToPlayers(sender.server);
        });
    }
}
