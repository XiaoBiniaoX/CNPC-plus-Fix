package top.cnpcplus.wand;

import net.minecraft.world.item.ItemStack;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import noppes.npcs.CustomItems;
import top.cnpcplus.transport.client.Pinyin;

/** Shared, side-neutral tool/range/search and list-coordinate checks. */
public final class WandTools {
    public static final double RANGE_SQUARED = 30 * 30;
    private WandTools() {}

    public static boolean showsHitboxes(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var item = stack.getItem();
        return item == CustomItems.wand || item == CustomItems.cloner || item == CustomItems.scripter;
    }

    public static boolean inRange(double distanceSquared) { return distanceSquared >= 0 && distanceSquared <= RANGE_SQUARED; }

    public static boolean showsPaths(ItemStack stack) {
        return showsHitboxes(stack) || !stack.isEmpty() && net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem())
                .equals(new net.minecraft.resources.ResourceLocation("customnpcs:npcmovingpath"));
    }

    public static boolean matches(String query, String label) {
        String q = Pinyin.normalizeQuery(query), text = Pinyin.normalizeQuery(label);
        return text.contains(q) || Pinyin.fuzzy(q, Pinyin.normalizeQuery(Pinyin.of(label)));
    }

    public static List<String> visibleSelection(Map<String, Integer> data, Collection<Integer> ids, String query) {
        return data.entrySet().stream().filter(e -> ids.contains(e.getValue()) && matches(query, e.getKey()))
                .map(Map.Entry::getKey).toList();
    }

    public static boolean intersects(double x0, double y0, double x1, double y1,
                                     double left, double top, double right, double bottom) {
        return Math.min(x0, x1) < right && Math.max(x0, x1) >= left
                && Math.min(y0, y1) < bottom && Math.max(y0, y1) >= top;
    }
}
