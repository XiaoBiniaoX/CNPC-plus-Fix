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

/** 客户端→服务端：选中某个配方，服务端把三个配方槽位内容填充进当前打开的编辑容器（231 照抄锻造 Select）。 */
public record PacketAnvilSelect(int id) implements CustomPacketPayload {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cnpcplus");
    public static final Type<PacketAnvilSelect> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "anvil_select"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAnvilSelect> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeVarInt(packet.id), buf -> new PacketAnvilSelect(buf.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PacketAnvilSelect packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // 权限校验（纵深防御）：编辑容器只能由同样校验权限的 PacketOpenAnvilGui 打开，
            // 但不能依赖那一处 —— 本包会往容器里写物品，必须自己也把住权限。
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) {
                LOGGER.debug("[anvil] Select拒绝: 无发送者或权限不足 player={}",
                        context.player() == null ? "?" : context.player().getGameProfile().getName());
                return;
            }
            if (!(player.containerMenu instanceof ContainerAnvilRecipes c)) {
                LOGGER.debug("[anvil] Select拒绝: 未打开编辑容器 id={} player={}", packet.id,
                        player.getGameProfile().getName());
                return;
            }
            AnvilRecipeData d = packet.id < 0 ? null
                    : AnvilRecipeRegistry.get(player.server.registryAccess(), packet.id);
            if (packet.id >= 0 && d == null) return;
            c.selectedId = d == null ? -1 : d.id;
            // 覆盖前分流（228 物品复制修复同款）：预览副本槽=吞（归还=每点一次条目白送）；
            // 玩家真物品槽=归还（防销毁——真实物品不能静默吃掉）。
            for (int i = 0; i < 3; i++) {
                net.minecraft.world.item.ItemStack old = c.recipe.getItem(i);
                if (old.isEmpty()) continue;
                c.recipe.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                if (c.slotPreview[i]) continue;
                if (!player.getInventory().add(old)) player.drop(old, false);
            }
            c.recipe.setItem(0, d == null ? net.minecraft.world.item.ItemStack.EMPTY : d.inputA.copy());
            c.recipe.setItem(1, d == null ? net.minecraft.world.item.ItemStack.EMPTY : d.inputB.copy());
            c.recipe.setItem(2, d == null ? net.minecraft.world.item.ItemStack.EMPTY : d.result.copy());
            // 回填内容标记为预览副本（命中配方=3 槽副本；id<0 清空草稿=空槽无预览）
            for (int i = 0; i < 3; i++) c.slotPreview[i] = (d != null);
            c.broadcastChanges();
        });
    }
}
