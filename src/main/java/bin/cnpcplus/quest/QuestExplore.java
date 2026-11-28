package bin.cnpcplus.quest;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.api.CustomNPCsException;
import noppes.npcs.api.handler.data.IQuestObjective;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.QuestData;
import noppes.npcs.quests.QuestInterface;

/** Region definition belongs to the quest; discovered cells belong to an active quest record. */
public final class QuestExplore extends QuestInterface {
    public static final String DEFAULT_PROGRESS_TEXT = "探索区域....吧！";
    public static final String COVERAGE_MODE_KEY = "CNPCPlusExploreHorizontalCoverage";
    public static final int MAX_CELLS = 1000000;
    static final String CELLS = "CNPCPlusExploreCells";
    static final String DIMENSION = "CNPCPlusExploreDimension";
    public int x1, y1, z1, x2, y2, z2;
    public int requiredPercent = 50;
    public int viewDistance = 16;
    public String progressText = DEFAULT_PROGRESS_TEXT;
    public boolean horizontalCoverage;
    public boolean boundsSet;

    public String getProgressText() {
        return progressText == null || progressText.trim().isEmpty() ? DEFAULT_PROGRESS_TEXT : progressText;
    }

    public void setProgressText(String text) {
        progressText = text == null || text.trim().isEmpty() ? DEFAULT_PROGRESS_TEXT : text;
    }

    public String coverageModeKey() {
        return horizontalCoverage ? "cnpcplus.quest.explore.coverage.horizontal" : "cnpcplus.quest.explore.coverage.volume";
    }

    public boolean progressModeMatches(NBTTagCompound tag) {
        return tag.getBoolean(COVERAGE_MODE_KEY) == horizontalCoverage;
    }

    public void setBounds(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.x1 = x1; this.y1 = y1; this.z1 = z1;
        this.x2 = x2; this.y2 = y2; this.z2 = z2;
        boundsSet = true;
        normalize();
    }

    public void normalize() {
        int value = Math.min(x1, x2); x2 = Math.max(x1, x2); x1 = value;
        value = Math.min(y1, y2); y2 = Math.max(y1, y2); y1 = value;
        value = Math.min(z1, z2); z2 = Math.max(z1, z2); z1 = value;
        requiredPercent = Math.max(1, Math.min(80, requiredPercent));
        viewDistance = Math.max(1, Math.min(128, viewDistance));
    }

    public long cellCount() {
        if (!boundsSet) return 0;
        normalize();
        int minY = Math.max(0, y1), maxY = Math.min(255, y2);
        if (maxY < minY) return 0;
        long sx = Math.floorDiv((long)x2, 4) - Math.floorDiv((long)x1, 4) + 1;
        long sy = Math.floorDiv((long)maxY, 4) - Math.floorDiv((long)minY, 4) + 1;
        long sz = Math.floorDiv((long)z2, 4) - Math.floorDiv((long)z1, 4) + 1;
        if (horizontalCoverage) return sx > MAX_CELLS / sz ? -1 : sx * sz;
        if (sx <= 0 || sy <= 0 || sz <= 0 || sx > MAX_CELLS / sy || sx * sy > MAX_CELLS / sz) return -1;
        return sx * sy * sz;
    }

    @Override public void readEntityFromNBT(NBTTagCompound tag) {
        x1 = tag.getInteger("CNPCPlusExploreX1"); y1 = tag.getInteger("CNPCPlusExploreY1"); z1 = tag.getInteger("CNPCPlusExploreZ1");
        x2 = tag.getInteger("CNPCPlusExploreX2"); y2 = tag.getInteger("CNPCPlusExploreY2"); z2 = tag.getInteger("CNPCPlusExploreZ2");
        requiredPercent = tag.hasKey("CNPCPlusExplorePercent") ? tag.getInteger("CNPCPlusExplorePercent") : 50;
        viewDistance = tag.hasKey("CNPCPlusExploreDistance") ? tag.getInteger("CNPCPlusExploreDistance") : 16;
        boundsSet = tag.getBoolean("CNPCPlusExploreBoundsSet");
        horizontalCoverage = tag.getBoolean(COVERAGE_MODE_KEY);
        setProgressText(tag.getString("CNPCPlusExploreProgressText"));
        normalize();
    }

    @Override public void writeEntityToNBT(NBTTagCompound tag) {
        normalize();
        tag.setInteger("CNPCPlusExploreX1", x1); tag.setInteger("CNPCPlusExploreY1", y1); tag.setInteger("CNPCPlusExploreZ1", z1);
        tag.setInteger("CNPCPlusExploreX2", x2); tag.setInteger("CNPCPlusExploreY2", y2); tag.setInteger("CNPCPlusExploreZ2", z2);
        tag.setInteger("CNPCPlusExplorePercent", requiredPercent); tag.setInteger("CNPCPlusExploreDistance", viewDistance);
        tag.setBoolean("CNPCPlusExploreBoundsSet", boundsSet);
        tag.setBoolean(COVERAGE_MODE_KEY, horizontalCoverage);
        tag.setString("CNPCPlusExploreProgressText", getProgressText());
    }

    @Override public boolean isCompleted(EntityPlayer player) {
        QuestData data = PlayerData.get(player).questData.activeQuests.get(questId);
        return progressHundredths(data) == 10000;
    }

    @Override public void handleComplete(EntityPlayer player) {}

    @Override public IQuestObjective[] getObjectives(EntityPlayer player) {
        return new IQuestObjective[]{new ExploreObjective(player)};
    }

    private int explored(QuestData data, long total) {
        if (!progressModeMatches(data.extraData)) return 0;
        java.util.BitSet cells = java.util.BitSet.valueOf(data.extraData.getByteArray(CELLS));
        return (int)Math.min(total, cells.cardinality());
    }

    private int progressHundredths(QuestData data) {
        if (data == null) return 0;
        long total = cellCount();
        // Task progress is relative to the configured coverage; truncate so unfinished text stays below 100.00%.
        return total <= 0 ? 0 : (int)Math.min(10000L, explored(data, total) * 1000000L / (total * requiredPercent));
    }

    private final class ExploreObjective implements IQuestObjective {
        private final EntityPlayer player;
        private ExploreObjective(EntityPlayer player) { this.player = player; }
        private double percent() {
            QuestData data = PlayerData.get(player).questData.activeQuests.get(questId);
            return progressHundredths(data) / 100.0D;
        }
        @Override public int getProgress() { return (int)percent(); }
        @Override public void setProgress(int value) {
            if (value < 0 || value > 100) throw new CustomNPCsException("Progress has to be between 0 and 100");
        }
        @Override public int getMaxProgress() { return 100; }
        @Override public boolean isCompleted() { return percent() == 100.0D; }
        @Override public String getText() {
            return getProgressText() + ": " + String.format(java.util.Locale.ROOT, "%.2f%%", percent());
        }
    }

    static boolean dimensionMatches(QuestData data, int dimension) {
        String saved = data.extraData.getString(DIMENSION);
        return saved.isEmpty() || saved.equals(Integer.toString(dimension));
    }

}
