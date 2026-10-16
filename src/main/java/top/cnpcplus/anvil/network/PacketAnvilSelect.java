package top.cnpcplus.anvil.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcplus.anvil.AnvilRecipeData;
import top.cnpcplus.anvil.AnvilRecipeRegistry;
import top.cnpcplus.anvil.ContainerAnvilRecipes;

import java.util.function.Supplier;

/** 客户端→服务端：选中某个配方，服务端把三个配方槽位内容填充进当前打开的编辑容器（231 照抄锻造 Select）。 */
public class PacketAnvilSelect {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cnpcplus");

    private final int id;

    public PacketAnvilSelect(int id) { this.id = id; }

    public void encode(FriendlyByteBuf buf) { buf.writeInt(this.id); }

    public static PacketAnvilSelect decode(FriendlyByteBuf buf) { return new PacketAnvilSelect(buf.readInt()); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            // 权限校验（纵深防御）：编辑容器只能由同样校验权限的 PacketOpenAnvilGui 打开，
            // 但不能依赖那一处 —— 本包会往容器里写物品，必须自己也把住权限。
            if (player == null || !player.hasPermissions(2)) {
                LOGGER.warn("[anvil] Select拒绝: 无发送者或权限不足 player={}",
                        player == null ? "?" : player.getGameProfile().getName());
                return;
            }
            if (!(player.containerMenu instanceof ContainerAnvilRecipes c)) {
                LOGGER.warn("[anvil] Select拒绝: 未打开编辑容器 id={} player={}", this.id,
                        player.getGameProfile().getName());
                return;
            }
            c.selectedId = this.id;
            AnvilRecipeData d = AnvilRecipeRegistry.get(this.id);
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
        ctx.get().setPacketHandled(true);
    }
}
