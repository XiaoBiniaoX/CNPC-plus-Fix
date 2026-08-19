package top.cnpcplus.smelting.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcplus.smelting.ContainerSmeltingRecipes;
import top.cnpcplus.smelting.SmeltingRecipeData;
import top.cnpcplus.smelting.SmeltingRecipeRegistry;
import top.cnpcplus.smelting.SmeltingRecipeManager;

import java.util.function.Supplier;

/**
 * 客户端→服务端：保存（新建/修改）自定义熔炼配方。
 * 物品槽位以服务端容器（当前打开的 ContainerSmeltingRecipes）为准，客户端 NBT 只带 id/name/开关/时间/经验。
 */
public class PacketSmeltingSave {
    private final CompoundTag data;

    public PacketSmeltingSave(CompoundTag data) { this.data = data; }

    public void encode(FriendlyByteBuf buf) { buf.writeNbt(this.data); }

    public static PacketSmeltingSave decode(FriendlyByteBuf buf) { return new PacketSmeltingSave(buf.readNbt()); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var sender = ctx.get().getSender();
            if (sender == null || !sender.hasPermissions(2)) return;
            if (this.data == null) return;
            SmeltingRecipeData d = SmeltingRecipeData.fromNBT(this.data);
            // 物品槽位一律以服务端当前打开的编辑容器为准（权威）。
            // 没有打开编辑容器就直接拒绝——否则会退化成信任客户端发来的 ItemStack（可伪造任意 NBT）。
            if (!(sender.containerMenu instanceof ContainerSmeltingRecipes c)) return;
            d.input = c.getInput().copy();
            d.fuel = c.getFuel().copy();
            d.output = c.getOutput().copy();
            // 服务端校验：输入/输出不能为空；时间≥0.01；经验必须是有限数（防 NaN/Infinity 进入熔炉结算）
            if (d.input.isEmpty() || d.output.isEmpty()) return;
            d.cookTime = Float.isFinite(d.cookTime) ? Math.max(0.01f, d.cookTime) : 200.0f;
            if (!Float.isFinite(d.xp)) d.xp = 0.0f;
            boolean ok = d.id >= 0 ? SmeltingRecipeRegistry.update(d) : (SmeltingRecipeRegistry.create(d) != null);
            if (!ok) return;
            SmeltingRecipeManager.injectAll(sender.server.getRecipeManager());
            SmeltingRecipeManager.syncToPlayers(sender.server);
            // 让客户端的配方书/JEI 也拿到新配方，否则要重连才能看到
            SmeltingRecipeManager.resendVanillaRecipes(sender.server);
        });
        ctx.get().setPacketHandled(true);
    }
}