package top.cnpcplus.interact;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import noppes.npcs.CustomItems;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcplus.config.CnpcPlusServerConfig;

/**
 * 「准星对准 NPC 时右键用不了弓/食物/药水」的判定中枢。
 *
 * <p>问题背景（CNPC 长期存在）：玩家举着弓、食物、药水对准 NPC 右键时，物品没反应。
 * 原因是原版把「与实体交互」排在「使用物品」之前，一旦交互链路认领了这次右键，
 * 使用物品的分支就不会执行。NPC 几乎总会认领（说台词、开对话、开商店都算），
 * 于是站在 NPC 前面就没法正常用物品。
 *
 * <p>本类提供「使用物品优先」的判据。设计上刻意保守，只在能确定玩家意图时才让物品优先：
 * <ol>
 *   <li>配置关掉 → 一律不干预，完全是原版行为。</li>
 *   <li>潜行右键 → 视为「我就是要交互」，不干预。给用户一个无需改配置的强制交互手段。</li>
 *   <li>CNPC 工具（魔杖/克隆/骑乘/脚本/路径）→ 绝不干预，否则编辑功能就废了。</li>
 *   <li>物品自身没有使用动作（{@code getUseAnimation() == NONE}）→ 不干预。
 *       这一条把绝大多数普通物品（方块、材料、剑、镐）排除在外，它们本来就没有
 *       「右键使用」语义，让它们优先只会白白吞掉交互。</li>
 *   <li>其余情况（弓/弩/食物/药水/望远镜/号角等）→ 物品优先。</li>
 * </ol>
 *
 * <p>为什么用 {@code getUseAnimation()} 而不是列白名单：它是原版给「这个物品右键会进入
 * 持续使用状态」定义的统一标记（BOW/CROSSBOW/EAT/DRINK/SPYGLASS/TOOT_HORN/BLOCK/SPEAR），
 * 模组物品只要按原版约定实现就自动受益，不需要我们维护清单。
 *
 * <p>为什么放在 mixin 包外：普通 {@code @Mixin} 类不能被外部直接引用
 * （ModLauncher 会抛 {@code NoClassDefFoundError: ... is invalid}），而这套判据要被
 * 交互 mixin 调用，所以必须是普通类。这条教训已写在 task_plan.md 的 Mixin 章节。
 *
 * <p>双端安全：只读物品与玩家状态，不碰任何客户端专有类，服务端可安全加载。
 */
public final class InteractPriority {

    private InteractPriority() {
    }

    /**
     * 这次右键是否应该让「使用手上物品」优先于「与 NPC 交互」。
     *
     * @param target 被右键的实体；非 NPC 一律返回 false（不干预其他模组的实体）
     */
    public static boolean itemWins(Player player, Entity target, InteractionHand hand) {
        if (player == null || hand == null) return false;
        if (!(target instanceof EntityNPCInterface)) return false;

        // 配置开关：默认开启，用户可整体关掉恢复原版。
        // 这里每次读取（ConfigValue.get() 是热读），改配置无需重启。
        if (!CnpcPlusServerConfig.InteractItemPriority.get()) return false;

        // 潜行右键 = 明确表达「我要交互」，不抢。
        if (player.isShiftKeyDown()) return false;

        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) return false;

        // CNPC 自己的工具必须保持交互优先，否则编辑 NPC 的功能全废。
        if (isCnpcTool(stack)) return false;

        // 只有「右键会进入使用状态」的物品才值得抢：弓、弩、食物、药水、望远镜、山羊角……
        // 普通物品（方块/材料/武器/工具）没有右键使用语义，抢了只会吞掉交互。
        return stack.getUseAnimation() != UseAnim.NONE;
    }

    /** CNPC 的编辑类工具，交互优先级必须最高。 */
    private static boolean isCnpcTool(ItemStack stack) {
        var item = stack.getItem();
        return item == CustomItems.wand
                || item == CustomItems.cloner
                || item == CustomItems.mount
                || item == CustomItems.scripter
                || item == CustomItems.moving
                || item == CustomItems.soulstoneEmpty
                || item == CustomItems.nbt_book;
    }
}
