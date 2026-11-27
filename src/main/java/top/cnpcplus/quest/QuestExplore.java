package top.cnpcplus.quest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.api.CustomNPCsException;
import noppes.npcs.api.handler.data.IQuestObjective;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.QuestData;
import noppes.npcs.quests.QuestInterface;

/** Region definition is shared; explored cells are stored only in an active quest record. */
public final class QuestExplore extends QuestInterface {
    public int x1, y1, z1, x2, y2, z2;
    public int requiredPercent = 50;
    public int viewDistance = 16;
    public static final int MAX_CELLS = 1_000_000;

    public static boolean coordinateInputAllowed(String text) {
        return text.matches("-?[0-9]*");
    }

    public void normalize() {
        int v = Math.min(x1, x2); x2 = Math.max(x1, x2); x1 = v;
        v = Math.min(y1, y2); y2 = Math.max(y1, y2); y1 = v;
        v = Math.min(z1, z2); z2 = Math.max(z1, z2); z1 = v;
        requiredPercent = Math.max(1, Math.min(80, requiredPercent));
        viewDistance = Math.max(1, Math.min(128, viewDistance));
    }

    public long cellCount(int minBuildY, int maxBuildY) {
        int minY = Math.max(Math.min(y1, y2), minBuildY);
        int maxY = Math.min(Math.max(y1, y2), maxBuildY - 1);
        if (maxY < minY) return 0;
        long sx = Math.floorDiv((long)Math.max(x1, x2), 4) - Math.floorDiv((long)Math.min(x1, x2), 4) + 1;
        long sy = Math.floorDiv((long)maxY, 4) - Math.floorDiv((long)minY, 4) + 1;
        long sz = Math.floorDiv((long)Math.max(z1, z2), 4) - Math.floorDiv((long)Math.min(z1, z2), 4) + 1;
        if (sx > MAX_CELLS / sy || sx * sy > MAX_CELLS / sz) return -1;
        return sx * sy * sz;
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        x1 = tag.getInt("CNPCPlusExploreX1"); y1 = tag.getInt("CNPCPlusExploreY1"); z1 = tag.getInt("CNPCPlusExploreZ1");
        x2 = tag.getInt("CNPCPlusExploreX2"); y2 = tag.getInt("CNPCPlusExploreY2"); z2 = tag.getInt("CNPCPlusExploreZ2");
        requiredPercent = tag.contains("CNPCPlusExplorePercent") ? tag.getInt("CNPCPlusExplorePercent") : 50;
        viewDistance = tag.contains("CNPCPlusExploreDistance") ? tag.getInt("CNPCPlusExploreDistance") : 16;
        normalize();
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        normalize();
        tag.putInt("CNPCPlusExploreX1", x1); tag.putInt("CNPCPlusExploreY1", y1); tag.putInt("CNPCPlusExploreZ1", z1);
        tag.putInt("CNPCPlusExploreX2", x2); tag.putInt("CNPCPlusExploreY2", y2); tag.putInt("CNPCPlusExploreZ2", z2);
        tag.putInt("CNPCPlusExplorePercent", requiredPercent); tag.putInt("CNPCPlusExploreDistance", viewDistance);
    }

    @Override public boolean isCompleted(Player player) {
        QuestData data = PlayerData.get(player).questData.activeQuests.get(questId);
        if (data == null || !dimensionMatches(data, player.level().dimension().location().toString())) return false;
        long total = cellCount(player.level().getMinBuildHeight(), player.level().getMaxBuildHeight());
        return total > 0 && explored(data, total) * 100L >= total * requiredPercent;
    }

    private static int explored(QuestData data, long total) {
        return Math.min((int)Math.min(Integer.MAX_VALUE, total), java.util.BitSet.valueOf(data.extraData.getLongArray("CNPCPlusExploreCells")).cardinality());
    }

    @Override public void handleComplete(Player player) {}

    @Override public IQuestObjective[] getObjectives(Player player) { return new IQuestObjective[]{new ExploreObjective(player)}; }

    private final class ExploreObjective implements IQuestObjective {
        private final Player player;
        private ExploreObjective(Player player) { this.player = player; }
        private int percent() {
            QuestData data = PlayerData.get(player).questData.activeQuests.get(questId);
            if (data == null || !dimensionMatches(data, player.level().dimension().location().toString())) return 0;
            long total = cellCount(player.level().getMinBuildHeight(), player.level().getMaxBuildHeight());
            if (total <= 0) return 0;
            return (int)Math.min(100, explored(data, total) * 100L / total);
        }
        @Override public int getProgress() { return percent(); }
        @Override public void setProgress(int value) {
            if (value < 0 || value > 100) throw new CustomNPCsException("Progress has to be between 0 and 100");
        }
        @Override public int getMaxProgress() { return 100; }
        @Override public boolean isCompleted() { return percent() >= requiredPercent; }
        @Override public String getText() { return getMCText().getString(); }
        @Override public Component getMCText() { return Component.translatable("cnpcplus.quest.explore.progress", percent(), requiredPercent); }
    }

    static boolean dimensionMatches(QuestData data, String dimension) {
        String recorded = data.extraData.getString("CNPCPlusExploreDimension");
        return recorded.isEmpty() || recorded.equals(dimension);
    }
}
