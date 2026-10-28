package bin.cnpcplus.crafting;

/** 先计算完整扣减表，任一需求不足则返回 null；调用方拿到成功计划后才扣物品。 */
public final class CraftPlanner {
    private CraftPlanner() { }
    public static int[] plan(int[] stock, int[] needed, boolean[][] matches) {
        int[] taken = new int[stock.length];
        boolean any = false;
        for (int slot = 0; slot < needed.length; slot++) {
            int remaining = needed[slot];
            for (int source = 0; source < stock.length && remaining > 0; source++) {
                if (!matches[slot][source]) continue;
                int amount = Math.min(remaining, Math.max(0, stock[source] - taken[source]));
                taken[source] += amount;
                remaining -= amount;
                any |= amount > 0;
            }
            if (remaining > 0) return null;
        }
        return any ? taken : null;
    }
}
