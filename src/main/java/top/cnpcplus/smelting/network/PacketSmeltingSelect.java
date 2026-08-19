package top.cnpcplus.smelting.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcplus.smelting.ContainerSmeltingRecipes;
import top.cnpcplus.smelting.SmeltingRecipeData;
import top.cnpcplus.smelting.SmeltingRecipeRegistry;

import java.util.function.Supplier;

/** 客户端→服务端：选中某个配方，服务端把配方槽位内容填充进当前打开的编辑容器。 */
public class PacketSmeltingSelect {
    private final int id;

    public PacketSmeltingSelect(int id) { this.id = id; }

    public void encode(FriendlyByteBuf buf) { buf.writeInt(this.id); }

    public static PacketSmeltingSelect decode(FriendlyByteBuf buf) { return new PacketSmeltingSelect(buf.readInt()); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            // 权限校验（纵深防御）：编辑容器只能由同样校验权限的 PacketOpenSmeltingGui 打开，
            // 但不能依赖那一处 —— 本包会往容器里写物品，必须自己也把住权限。
            if (player == null || !player.hasPermissions(2)) return;
            if (player.containerMenu instanceof ContainerSmeltingRecipes c) {
                c.selectedId = this.id;
                SmeltingRecipeData d = SmeltingRecipeRegistry.get(this.id);
                c.recipe.setItem(0, d == null ? net.minecraft.world.item.ItemStack.EMPTY : d.fuel.copy());
                c.recipe.setItem(1, d == null ? net.minecraft.world.item.ItemStack.EMPTY : d.input.copy());
                c.recipe.setItem(2, d == null ? net.minecraft.world.item.ItemStack.EMPTY : d.output.copy());
                c.broadcastChanges();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
