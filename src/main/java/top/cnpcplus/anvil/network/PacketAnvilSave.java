package top.cnpcplus.anvil.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcplus.anvil.AnvilRecipeData;
import top.cnpcplus.anvil.AnvilRecipeRegistry;
import top.cnpcplus.anvil.ContainerAnvilRecipes;

import java.util.function.Supplier;

/**
 * 客户端→服务端：保存（新建/修改）自定义铁砧配方（阶段231 从 PacketSmithingSave 复制改三槽）。
 * 物品槽位以服务端容器（当前打开的 ContainerAnvilRecipes）为准，客户端 NBT 只带 id/name/expCost/nameOnly。
 * 保存后立即 syncToPlayers 全员广播（配方不进 RecipeManager——AnvilCraftHandler 直查 Registry）。
 */
public class PacketAnvilSave {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cnpcplus");

    private final CompoundTag data;

    public PacketAnvilSave(CompoundTag data) { this.data = data; }

    public void encode(FriendlyByteBuf buf) { buf.writeNbt(this.data); }

    public static PacketAnvilSave decode(FriendlyByteBuf buf) { return new PacketAnvilSave(buf.readNbt()); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var sender = ctx.get().getSender();
            if (sender == null || !sender.hasPermissions(2)) {
                LOGGER.warn("[anvil] Save拒绝: 无发送者或权限不足 player={}",
                        sender == null ? "?" : sender.getGameProfile().getName());
                return;
            }
            if (this.data == null) {
                LOGGER.warn("[anvil] Save拒绝: NBT为空");
                return;
            }
            AnvilRecipeData d = AnvilRecipeData.fromNBT(this.data);
            // 名称长度设上限：它会落盘并被 S→C 同步包重新广播，而 S→C 自定义载荷上限是 1MB。
            // 客户端发来的字符串属于不可信输入，不设限的话攒够超长名称就能让同步包超限、把所有客户端打崩。
            if (d.name != null && d.name.length() > 64) d.name = d.name.substring(0, 64);
            // 经验重夹（客户端输入框约束不信任，服务端自己夹 -32767..32767，231b 用户指定范围）：
            // 负数=给锻造者经验（onTake 原生）、0=免费、正数=扣锻造者；越界按 int 载荷安全夹。
            if (d.expCost < -32767) d.expCost = -32767;
            if (d.expCost > 32767) d.expCost = 32767;
            // 物品槽位一律以服务端当前打开的编辑容器为准（权威）。
            // 没有打开编辑容器就直接拒绝——否则会退化成信任客户端发来的 ItemStack（可伪造任意 NBT）。
            if (!(sender.containerMenu instanceof ContainerAnvilRecipes c)) {
                LOGGER.warn("[anvil] Save拒绝: 未打开编辑容器 player={}", sender.getGameProfile().getName());
                return;
            }
            d.inputA = c.getInputA().copy();
            d.inputB = c.getInputB().copy();
            d.result = c.getOutput().copy();
            // 231 注册条件（铁砧语义）：A、B、结果三槽全部必放（A+B 敲打=C 必须两输入）。
            if (d.inputA.isEmpty() || d.inputB.isEmpty() || d.result.isEmpty()) {
                LOGGER.warn("[anvil] Save拒绝: 槽位条件不满足 a={} b={} r={} (规则=A/B/结果全必放)",
                        !d.inputA.isEmpty(), !d.inputB.isEmpty(), !d.result.isEmpty());
                return;
            }
            boolean ok = d.id >= 0 ? AnvilRecipeRegistry.update(d) : (AnvilRecipeRegistry.create(d) != null);
            if (!ok) {
                LOGGER.warn("[anvil] Save拒绝: Registry写入失败 id={}", d.id);
                return;
            }
            // 本轮用户允许打日志：保存成功一行 INFO（低频操作）
            LOGGER.info("[anvil] 配方保存 id={} name={} exp={} nameOnly={}", d.id, d.name, d.expCost, d.nameOnly);
            // 一人创建全服可用：全员同步编辑列表（配方生效在服务端事件直查 Registry，无需 RecipeManager 注入）
            AnvilRecipeRegistry.syncToPlayers(sender.server);
        });
        ctx.get().setPacketHandled(true);
    }
}
