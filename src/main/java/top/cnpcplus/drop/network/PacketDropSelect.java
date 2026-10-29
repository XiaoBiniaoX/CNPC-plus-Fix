package top.cnpcplus.drop.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcplus.drop.DropRecipeData;
import top.cnpcplus.drop.DropRecipeRegistry;
import top.cnpcplus.drop.ContainerDropRecipes;

import java.util.function.Supplier;

/** 客户端→服务端：选中某个配方，服务端把五个配方槽位内容填充进当前打开的编辑容器（232 照抄铁砧 Select）。 */
public class PacketDropSelect {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cnpcplus");

    private final int id;

    public PacketDropSelect(int id) { this.id = id; }

    public void encode(FriendlyByteBuf buf) { buf.writeInt(this.id); }

    public static PacketDropSelect decode(FriendlyByteBuf buf) { return new PacketDropSelect(buf.readInt()); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            // 权限校验（纵深防御）：编辑容器只能由同样校验权限的 PacketOpenDropGui 打开，
            // 但不能依赖那一处 —— 本包会往容器里写物品，必须自己也把住权限。
            if (player == null || !player.hasPermissions(2)) {
                LOGGER.debug("[drop] Select拒绝: 无发送者或权限不足 player={}",
                        player == null ? "?" : player.getGameProfile().getName());
                return;
            }
            if (!(player.containerMenu instanceof ContainerDropRecipes c)) {
                LOGGER.debug("[drop] Select拒绝: 未打开编辑容器 id={} player={}", this.id,
                        player.getGameProfile().getName());
                return;
            }
            c.selectedId = this.id;
            DropRecipeData d = DropRecipeRegistry.get(this.id);
            // 覆盖前分流（228 物品复制修复同款）：预览副本槽=吞（归还=每点一次条目白送）；
            // 玩家真物品槽=归还（防销毁——真实物品不能静默吃掉）。
            for (int i = 0; i < 5; i++) {
                net.minecraft.world.item.ItemStack old = c.recipe.getItem(i);
                if (old.isEmpty()) continue;
                c.recipe.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                if (c.slotPreview[i]) continue;
                if (!player.getInventory().add(old)) player.drop(old, false);
            }
            for (int i = 0; i < 4; i++) {
                c.recipe.setItem(i, d == null ? net.minecraft.world.item.ItemStack.EMPTY : d.inputs[i].copy());
            }
            c.recipe.setItem(4, d == null ? net.minecraft.world.item.ItemStack.EMPTY : d.result.copy());
            // 回填内容标记为预览副本（命中配方=5 槽副本；id<0 清空草稿=空槽无预览）
            for (int i = 0; i < 5; i++) c.slotPreview[i] = (d != null);
            c.broadcastChanges();
        });
        ctx.get().setPacketHandled(true);
    }
}
