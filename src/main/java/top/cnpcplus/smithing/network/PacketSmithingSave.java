package top.cnpcplus.smithing.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcplus.smithing.ContainerSmithingRecipes;
import top.cnpcplus.smithing.SmithingRecipeData;
import top.cnpcplus.smithing.SmithingRecipeManager;
import top.cnpcplus.smithing.SmithingRecipeRegistry;

import java.util.function.Supplier;

/**
 * 客户端→服务端：保存（新建/修改）自定义锻造配方（227 从 PacketSmeltingSave 复制）。
 * 物品槽位以服务端容器（当前打开的 ContainerSmithingRecipes）为准，客户端 NBT 只带 id/name。
 * 保存后立即注入 RecipeManager 并广播 = 热添加（原版锻造台下一次操作即可用）。
 */
public class PacketSmithingSave {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cnpcplus");

    private final CompoundTag data;

    public PacketSmithingSave(CompoundTag data) { this.data = data; }

    public void encode(FriendlyByteBuf buf) { buf.writeNbt(this.data); }

    public static PacketSmithingSave decode(FriendlyByteBuf buf) { return new PacketSmithingSave(buf.readNbt()); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var sender = ctx.get().getSender();
            if (sender == null || !sender.hasPermissions(2)) {
                LOGGER.debug("[smithing] Save拒绝: 无发送者或权限不足 player={}",
                        sender == null ? "?" : sender.getGameProfile().getName());
                return;
            }
            if (this.data == null) {
                LOGGER.debug("[smithing] Save拒绝: NBT为空");
                return;
            }
            SmithingRecipeData d = SmithingRecipeData.fromNBT(this.data);
            // 名称长度设上限：它会落盘并被 S→C 同步包重新广播，而 S→C 自定义载荷上限是 1MB。
            // 客户端发来的字符串属于不可信输入，不设限的话攒够超长名称就能让同步包超限、把所有客户端打崩。
            if (d.name != null && d.name.length() > 64) d.name = d.name.substring(0, 64);
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
            boolean ok = d.id >= 0 ? SmithingRecipeRegistry.update(d) : (SmithingRecipeRegistry.create(d) != null);
            if (!ok) {
                LOGGER.debug("[smithing] Save拒绝: Registry写入失败 id={}", d.id);
                return;
            }
            SmithingRecipeManager.injectAll(sender.server.getRecipeManager());
            SmithingRecipeManager.syncToPlayers(sender.server);
            // 228 用户要求：一人修改，全部玩家立即可合成（原版配方表全员群发）
            SmithingRecipeManager.resendVanillaRecipes(sender.server);
        });
        ctx.get().setPacketHandled(true);
    }
}
