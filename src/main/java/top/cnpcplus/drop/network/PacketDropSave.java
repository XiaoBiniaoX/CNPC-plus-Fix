package top.cnpcplus.drop.network;

import net.minecraft.nbt.CompoundTag;
import bin.cnpcplus.CnpcPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.cnpcplus.drop.DropRecipeData;
import top.cnpcplus.drop.DropRecipeRegistry;
import top.cnpcplus.drop.ContainerDropRecipes;

/**
 * 客户端→服务端：保存（新建/修改）自定义掉落物配方（阶段232 从 PacketAnvilSave 复制改五槽）。
 * 物品槽位以服务端容器（当前打开的 ContainerDropRecipes）为准，客户端 NBT 只带 id/name/nameOnly。
 * 保存后立即 syncToPlayers 全员广播（配方不进 RecipeManager——DropCraftHandler 直查 Registry）。
 */
public record PacketDropSave(CompoundTag data) implements CustomPacketPayload {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cnpcplus");

    public static final Type<PacketDropSave> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "drop_save"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketDropSave> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeNbt(packet.data), buf -> new PacketDropSave(buf.readNbt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PacketDropSave packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender) || !sender.hasPermissions(2)) {
                LOGGER.debug("[drop] Save拒绝: 无发送者或权限不足 player={}",
                        context.player() == null ? "?" : context.player().getGameProfile().getName());
                return;
            }
            if (packet.data == null) {
                LOGGER.debug("[drop] Save拒绝: NBT为空");
                return;
            }
            DropRecipeData d = DropRecipeData.fromMetadataNBT(packet.data);
            // 名称长度设上限：它会落盘并被 S→C 同步包重新广播，而 S→C 自定义载荷上限是 1MB。
            if (d.name != null && d.name.length() > 64) d.name = d.name.substring(0, 64);
            if (d.soundId.length() > 256) d.soundId = d.soundId.substring(0, 256);
            if (d.dim.length() > 256) d.dim = d.dim.substring(0, 256);
            // 物品槽位一律以服务端当前打开的编辑容器为准（权威）。
            if (!(sender.containerMenu instanceof ContainerDropRecipes c)) {
                LOGGER.debug("[drop] Save拒绝: 未打开编辑容器 player={}", sender.getGameProfile().getName());
                return;
            }
            for (int i = 0; i < 4; i++) {
                d.inputs[i] = c.getInput(i).copy();
                // 需求数量夹 1..64：槽内 count 即消耗数量（1.20.1 单实体上限 64），防异常负数/超大
                if (!d.inputs[i].isEmpty() && d.inputs[i].getCount() < 1) d.inputs[i].setCount(1);
                if (d.inputs[i].getCount() > 64) d.inputs[i].setCount(64);
            }
            d.result = c.getOutput().copy();
            if (!d.result.isEmpty() && d.result.getCount() < 1) d.result.setCount(1);
            if (d.result.getCount() > 64) d.result.setCount(64);
            // 232 注册条件（用户要求1）：至少两个输入槽有物 + 结果必放。
            if (d.filledInputCount() < 2 || d.result.isEmpty()) {
                LOGGER.debug("[drop] Save拒绝: 槽位条件不满足 filled={} r={} (规则=输入≥2槽+结果必放)",
                        d.filledInputCount(), !d.result.isEmpty());
                return;
            }
            var registries = sender.server.registryAccess();
            boolean ok = d.id >= 0 ? DropRecipeRegistry.update(registries, d)
                    : DropRecipeRegistry.create(registries, d) != null;
            if (!ok) {
                LOGGER.debug("[drop] Save拒绝: Registry写入失败 id={}", d.id);
                return;
            }
            // 233d-UI8：fxs 由「数量」改「明细 particleId:[TYPE,…]」——「出来是圆形/
            // 音效没播放」决定性日志：保存那一刻配方里到底存了哪些形状一眼可见
            // （233d-UI9 降为 debug：默认不输出，排查时开 cnpcplus debug 级即可）
            String fxsDetail = String.join(", ",
                    d.fxs.stream().map(fx -> fx.summary()).toList());
            LOGGER.debug("[drop] 配方保存 id={} name={} inputs={} result={} soundId={} fxs={} (233d-UI8 明细；soundId 空=不播)",
                    d.id, d.name, d.filledInputCount(), d.result.getCount() + "x" + d.result.getItem(),
                    d.soundId, fxsDetail);
            // 一人创建全服可用：全员同步编辑列表（配方生效在 DropCraftHandler 直查 Registry）
            DropRecipeRegistry.syncToPlayers(sender.server);
        });
    }
}
