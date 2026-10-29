package top.cnpcplus.anvil;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import top.cnpcplus.anvil.client.AnvilClientData;

import java.util.List;

/**
 * 自定义铁砧配方「生效层」（阶段231 核心，与熔炼/锻造的本质差异所在）。
 *
 * <p>vanilla AnvilMenu 不查 RecipeManager（反编译实证：createResult 里只有原版
 * 改名/修复/合并硬编码），所以铁砧配方不注入 RecipeManager，改挂 Forge 原生
 * {@link AnvilUpdateEvent} —— 该事件在 AnvilMenu.createResult 内 fire（双端），
 * ForgeHooks.onAnvilChange 实证行为：
 * <ul>
 *   <li>output 空 → 返回 true → 继续原版逻辑（改名/修复/合并完全共存，零回归）；</li>
 *   <li>output 非空 → resultSlots.setItem(0, output) + setMaximumCost(cost)
 *       + repairItemCountCost = materialCost → return 跳过原版。</li>
 * </ul>
 * 全链路原生复用：cost DataSlot → GUI 显示等级 + mayPickup 检查（231b 前实证 cost>0 才可取，
 * 故当时强制 expCost≥1；231b 用户要求负数经验后由 MixinAnvilMenu 放宽 mayPickup 的 cost>0
 * 条件 → 可取走条件变为 instabuild||level>=cost，0/负数均通）→ onTake 扣 giveExperienceLevels(-cost)
 * （**负 cost 原生=给经验**，javap ineg 实证）+ A 槽(item0) 无条件清空
 * + B 槽按 repairItemCountCost shrink(1)（=0 分支会清空 B，故 materialCost 恒 1 = 消耗1个）。
 *
 * <p>数据源按逻辑端分流：服务端 = AnvilRecipeRegistry（共享权威表，一人创建全服可用）；
 * 客户端 = AnvilClientData（编辑器 Sync 下发的列表；未同步时空表 → 不干预 →
 * 结果槽由服务端槽同步 broadcast 覆盖显示，不会不同步）。
 * 事件匹配命中不打日志（每次放物品都触发 = 高频刷屏；本轮日志只打保存/删除/载入）。
 */
public final class AnvilCraftHandler {

    private AnvilCraftHandler() {}

    /**
     * 单次配方经验范围（231b 用户指定 -32767..32767，负数=给锻造者经验）。
     * 与 AnvilRecipeData/PacketAnvilSave 夹值、GUI 输入框 min/max 保持一致。
     */
    public static final int EXP_MIN = -32767;
    public static final int EXP_MAX = 32767;

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty()) return;

        // 客户端逻辑不引服务端类的反向问题：AnvilRecipeRegistry 客户端 list() 恒空（hasServer），
        // 这里按 isClientSide 显式分源，语义清晰且客户端事件（本地预览计算）不碰 Registry 锁。
        List<AnvilRecipeData> list = player.level().isClientSide()
                ? AnvilClientData.get()
                : AnvilRecipeRegistry.list(player.level().registryAccess());
        if (list.isEmpty()) return;

        for (AnvilRecipeData d : list) {
            if (d.result.isEmpty() || d.inputA.isEmpty() || d.inputB.isEmpty()) continue;
            if (matchesSlot(d.inputA, left, d.nameOnly) && matchesSlot(d.inputB, right, d.nameOnly)) {
                event.setOutput(d.result.copy());
                event.setCost(Math.max(EXP_MIN, Math.min(EXP_MAX, d.expCost)));
                event.setMaterialCost(1); // B 槽消耗 1 个（A 槽由 onTake 原生无条件清空）
                return;
            }
        }
        // 未命中：不 setOutput → ForgeHooks 返回 true → 原版改名/修复/合并继续（共存不回归）
    }

    /**
     * 单槽匹配：nameOnly=false → isSameItemSameComponents（物品+组件全等）；
     * nameOnly=true → 仅名字检查（只校验物品id+物品名字，附魔/耐久/其余NBT不参与，
     * = CNPC NoppesUtilPlayer.compareItemDetails 反编译 L27-30 同链）。
     * 注意对象顺序：配方栈为左（isSame 非对称有 ItemStackTag 短路差异，固定 方=配方 右=槽内）。
     */
    private static boolean matchesSlot(ItemStack recipe, ItemStack stack, boolean nameOnly) {
        if (nameOnly) {
            return stack.is(recipe.getItem())
                    && stack.getHoverName().getString().equals(recipe.getHoverName().getString());
        }
        return ItemStack.isSameItemSameComponents(recipe, stack);
    }
}
