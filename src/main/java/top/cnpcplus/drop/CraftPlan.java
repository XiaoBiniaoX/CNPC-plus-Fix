package top.cnpcplus.drop;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 掉落物配方单次合成的「已判定消耗计划」（阶段232，先判定后消耗的载体）。
 *
 * <p>planCraft 跑完后全部槽位需求都被验证充足才产出本对象——execute 阶段只按计划扣减，
 * 不会再做判定，也就不会出现「扣了一半发现不够」的部分消耗。
 *
 * <p><b>批量接口位（用户要求「多倍只触发一次，留接口后面要改」）</b>：
 * 本轮 DropCraftHandler 每次链式步骤只 plan 一份、execute 一份；
 * 后续要支持批量 = 对同一实体快照循环 plan/execute，或给 planCraft 加 multiplier 参数，
 * 本类结构无需改动（扣减表本来就是按实体聚合的）。
 */
public final class CraftPlan {

    /** 被命中的配方（id 升序稳定优先级选出的第一条）。 */
    public final DropRecipeData recipe;
    /** 每个参与扣减的实体 → 扣减数量（已验证 ≤ 该实体当前可用量）。 */
    public final Map<ItemEntity, Integer> deduct;
    /** 产出（copy，count=配方结果槽 count）。 */
    public final ItemStack output;

    public CraftPlan(DropRecipeData recipe, Map<ItemEntity, Integer> deduct, ItemStack output) {
        this.recipe = recipe;
        this.deduct = deduct;
        this.output = output;
    }

    /** 便捷工厂：把扣减明细复制成 IdentityHashMap（实体身份做键，防 hashCode/equals 干扰）。 */
    public static Map<ItemEntity, Integer> newDeductMap() {
        return new IdentityHashMap<>();
    }

    /** 需求求解入口（独立成方法=纯函数便于测试与后续批量改造）。 */
    public static CraftPlan plan(List<ItemEntity> candidates, DropRecipeData recipe) {
        Map<ItemEntity, Integer> planned = newDeductMap();
        // 按槽序逐槽求解；多槽共享同一扣减池 → 同类型需求可由单个大实体连续满足（5木板=3+2）
        for (ItemStack need : recipe.inputs) {
            if (need.isEmpty()) continue;
            int left = need.getCount();
            for (ItemEntity e : candidates) {
                if (left <= 0) break;
                ItemStack have = e.getItem();
                if (have.isEmpty()) continue;
                if (!matches(have, need, recipe.nameOnly)) continue;
                int already = planned.getOrDefault(e, 0);
                int avail = have.getCount() - already;
                if (avail <= 0) continue;
                int take = Math.min(left, avail);
                planned.put(e, already + take);
                left -= take;
            }
            if (left > 0) return null; // 任一槽不足 = 整体失败（先判定后消耗，零部分扣减）
        }
        if (planned.isEmpty()) return null; // 全空槽配方不该存在（注册条件拦），兜底
        return new CraftPlan(recipe, planned, recipe.result.copy());
    }

    private static boolean matches(ItemStack have, ItemStack need, boolean nameOnly) {
        if (nameOnly) {
            return have.getItem() == need.getItem()
                    && have.getHoverName().equals(need.getHoverName());
        }
        return ItemStack.isSameItemSameTags(have, need);
    }
}
