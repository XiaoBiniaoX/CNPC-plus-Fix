package top.cnpcplus.smithing.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcplus.smithing.ContainerSmithingRecipes;
import top.cnpcplus.smithing.SmithingRecipeData;
import top.cnpcplus.smithing.SmithingRecipeRegistry;

import java.util.function.Supplier;

/** 客户端→服务端：选中某个配方，服务端把四个配方槽位内容填充进当前打开的编辑容器（227）。 */
public class PacketSmithingSelect {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cnpcplus");

    private final int id;

    public PacketSmithingSelect(int id) { this.id = id; }

    public void encode(FriendlyByteBuf buf) { buf.writeInt(this.id); }

    public static PacketSmithingSelect decode(FriendlyByteBuf buf) { return new PacketSmithingSelect(buf.readInt()); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            // 权限校验（纵深防御）：编辑容器只能由同样校验权限的 PacketOpenSmithingGui 打开，
            // 但不能依赖那一处 —— 本包会往容器里写物品，必须自己也把住权限。
            if (player == null || !player.hasPermissions(2)) {
                LOGGER.debug("[smithing] Select拒绝: 无发送者或权限不足 player={}",
                        player == null ? "?" : player.getGameProfile().getName());
                return;
            }
            if (!(player.containerMenu instanceof ContainerSmithingRecipes c)) {
                LOGGER.debug("[smithing] Select拒绝: 未打开编辑容器 id={} player={}", this.id,
                        player.getGameProfile().getName());
                return;
            }
            c.selectedId = this.id;
            SmithingRecipeData d = SmithingRecipeRegistry.get(this.id);
            // 覆盖前分流（228 物品复制修复）：预览副本槽=吞（它们是 Registry 数据的凭空副本，
            // 归还=每点一次条目白送 4 样）；玩家真物品槽=归还（防销毁——真实物品不能静默吃掉）。
            // 「是否预览」由 ContainerSmithingRecipes.slotPreview 判定：回填置 true，
            // 玩家对该槽任何 clicked 操作置 false。切换配方和点「添加配方」都会走到这里。
            for (int i = 0; i < 4; i++) {
                net.minecraft.world.item.ItemStack old = c.recipe.getItem(i);
                if (old.isEmpty()) continue;
                c.recipe.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                if (c.slotPreview[i]) continue;
                if (!player.getInventory().add(old)) player.drop(old, false);
            }
            c.recipe.setItem(0, d == null ? net.minecraft.world.item.ItemStack.EMPTY : d.template.copy());
            c.recipe.setItem(1, d == null ? net.minecraft.world.item.ItemStack.EMPTY : d.base.copy());
            c.recipe.setItem(2, d == null ? net.minecraft.world.item.ItemStack.EMPTY : d.addition.copy());
            c.recipe.setItem(3, d == null ? net.minecraft.world.item.ItemStack.EMPTY : d.result.copy());
            // 回填内容标记为预览副本（命中配方=4 槽副本；id<0 清空草稿=空槽无预览）
            for (int i = 0; i < 4; i++) c.slotPreview[i] = (d != null);
            c.broadcastChanges();
        });
        ctx.get().setPacketHandled(true);
    }
}
