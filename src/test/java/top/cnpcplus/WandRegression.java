package top.cnpcplus;

import top.cnpcplus.config.CnpcPlusConfigData;
import top.cnpcplus.wand.WandTools;

/** Search/range/drag geometry only, not GUI rendering, packets or Mixin application. */
public final class WandRegression {
    public static void main(String[] args) {
        check(CnpcPlusConfigData.WandNpcHitboxes.getDefault(), "hitboxes must default on");
        for (double distance : new double[]{0, 1, 29.99, 30})
            check(WandTools.inRange(distance * distance), "inside 30 blocks rejected");
        for (double distance : new double[]{-1, 900.0001, Double.NaN, Double.POSITIVE_INFINITY})
            check(!WandTools.inRange(distance), "outside/invalid distance accepted");
        check(!WandTools.showsHitboxes(null), "null tool accepted");
        check(WandTools.matches("", "00.5 : 我是NPC的名字"), "empty search");
        check(WandTools.matches("名字", "00.5 : 我是NPC的名字"), "Chinese name search");
        check(WandTools.matches("WO SHI NPC", "00.5 : 我是NPC的名字"), "full pinyin/case/spacing search");
        check(WandTools.matches("wsnpcdmz", "00.5 : 我是NPC的名字"), "pinyin abbreviated search");
        check(!WandTools.matches("nonexistent", "00.5 : 我是NPC的名字"), "unrelated pinyin matched");
        var refreshed = java.util.Map.of("05.0 : 我是NPC的名字", 41, "01.0 : 另一NPC", 42);
        check(WandTools.visibleSelection(refreshed, java.util.List.of(41), "").equals(java.util.List.of("05.0 : 我是NPC的名字")), "selection lost when distance/name changed");
        check(WandTools.visibleSelection(refreshed, java.util.List.of(41), "另一").isEmpty(), "filtered-out selection must not enable destructive actions");
        check(WandTools.visibleSelection(refreshed, java.util.List.of(99), "").isEmpty(), "removed NPC selected");
        for (boolean reverse : new boolean[]{false, true}) {
            double a = reverse ? 60 : 3, b = reverse ? 3 : 60;
            check(WandTools.intersects(a, a, b, b, 2, 1, 202, 15), "first row drag");
            check(WandTools.intersects(a, a, b, b, 2, 43, 202, 57), "last intersecting row drag");
            check(!WandTools.intersects(a, a, b, b, 2, 71, 202, 85), "outside rectangle selected");
        }
        check(WandTools.intersects(30, 30, 30, 75, 2, 43, 202, 57), "vertical same-column drag");
        System.out.println("WAND_REGRESSION=PASS (search/range/drag geometry only; GUI/network/Mixin remain separate)");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
