package bin.cnpcplus.quest;

import java.lang.reflect.Method;
import java.util.BitSet;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.controllers.data.QuestData;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pure quest/NBT checks; no world, client, or Mixin bootstrap. */
public class QuestRegressionTest {
    @Test public void explorationNormalizesNegativeCoordinatesAndClipsHeight() {
        QuestExplore quest = new QuestExplore();
        assertEquals(0, quest.cellCount());
        quest.setBounds(3, 300, 3, -4, -10, -4);
        assertEquals(256, quest.cellCount());
        assertEquals(-4, quest.x1);
        assertEquals(3, quest.x2);
        quest.horizontalCoverage = true;
        assertEquals(4, quest.cellCount());
        quest.setBounds(0, 256, 0, 3, 300, 3);
        assertEquals(0, quest.cellCount());
        quest.setBounds(0, 64, 0, 0, 64, 0);
        assertEquals(1, quest.cellCount());
        quest.setBounds(Integer.MIN_VALUE, 0, Integer.MIN_VALUE, Integer.MAX_VALUE, 255, Integer.MAX_VALUE);
        assertEquals(-1, quest.cellCount());
    }

    @Test public void explorationDefinitionRoundTripsAndClampsSettings() {
        QuestExplore quest = new QuestExplore();
        quest.setBounds(7, 70, 7, 0, 64, 0);
        quest.requiredPercent = 99;
        quest.viewDistance = 0;
        quest.horizontalCoverage = true;
        quest.setProgressText("Test region");
        NBTTagCompound tag = new NBTTagCompound();
        quest.writeEntityToNBT(tag);
        QuestExplore loaded = new QuestExplore();
        loaded.readEntityFromNBT(tag);
        assertEquals(80, loaded.requiredPercent);
        assertEquals(1, loaded.viewDistance);
        assertEquals(4, loaded.cellCount());
        assertTrue(loaded.horizontalCoverage);
        assertEquals("Test region", loaded.getProgressText());
        loaded.setProgressText(" ");
        assertEquals(QuestExplore.DEFAULT_PROGRESS_TEXT, loaded.getProgressText());
        loaded.readEntityFromNBT(new NBTTagCompound());
        assertEquals(16, loaded.viewDistance);
        assertEquals(50, loaded.requiredPercent);
    }

    @Test public void visibleCellsRequireTwoSamplesAndIgnoreOutOfBounds() {
        assertArrayEquals(new int[]{0, 2}, QuestRegionRecorder.visibleCells(
                new int[][]{{0, 1, 2, -1}, {0, 2, 9}, {3}}, 4));
        assertArrayEquals(new int[0], QuestRegionRecorder.visibleCells(new int[][]{{1, 2}, {3}}, 4));
        assertArrayEquals(new int[0], QuestRegionRecorder.visibleCells(new int[0][], 1000000));
    }

    @Test public void discoveryIsDeduplicatedPersistentAndScopedToAcceptanceAndMode() throws Exception {
        QuestExplore quest = new QuestExplore();
        quest.setBounds(0, 64, 0, 7, 71, 7);
        QuestData first = new QuestData(null), secondPlayer = new QuestData(null), reacquired = new QuestData(null);
        BitSet cells = new BitSet();
        cells.set(0); cells.set(0); cells.set(5);
        first.extraData.setByteArray(QuestExplore.CELLS, cells.toByteArray());
        first.extraData.setString(QuestExplore.DIMENSION, "0");
        QuestData reloaded = new QuestData(null);
        reloaded.extraData = first.extraData.copy();
        Method explored = QuestExplore.class.getDeclaredMethod("explored", QuestData.class, long.class);
        explored.setAccessible(true);
        assertEquals(2, explored.invoke(quest, reloaded, quest.cellCount()));
        assertEquals(0, explored.invoke(quest, secondPlayer, quest.cellCount()));
        assertEquals(0, explored.invoke(quest, reacquired, quest.cellCount()));
        assertTrue(QuestExplore.dimensionMatches(reloaded, 0));
        assertFalse(QuestExplore.dimensionMatches(reloaded, -1));
        quest.horizontalCoverage = true;
        assertEquals(0, explored.invoke(quest, reloaded, quest.cellCount()));
        reloaded.extraData.setBoolean(QuestExplore.COVERAGE_MODE_KEY, true);
        assertEquals(2, explored.invoke(quest, reloaded, quest.cellCount()));
    }

    @Test public void batchCraftingCapsWithoutOverflowOrNegativeProgress() {
        assertEquals(9, QuestCraft.capProgress(4, 20, 5));
        assertEquals(20, QuestCraft.capProgress(19, 20, 64));
        assertEquals(1000000, QuestCraft.capProgress(999999, 1000000, Integer.MAX_VALUE));
        assertEquals(0, QuestCraft.capProgress(-4, 10, -1));
        assertEquals(7, QuestCraft.capProgress(7, 10, -1));
    }

    @Test public void explorationProgressReaches100AtConfiguredCoverageAndNeverRoundsUpEarly() throws Exception {
        QuestExplore quest = new QuestExplore();
        quest.setBounds(0, 64, 0, 399, 67, 3);
        assertEquals(100, quest.cellCount());
        QuestData data = new QuestData(null);
        Method progress = QuestExplore.class.getDeclaredMethod("progressHundredths", QuestData.class);
        progress.setAccessible(true);
        int[][] cases = {{0, 0}, {10, 2000}, {25, 5000}, {49, 9800}, {50, 10000}, {70, 10000}, {100, 10000}};
        for (int[] example : cases) {
            BitSet cells = new BitSet(); cells.set(0, example[0]);
            data.extraData.setByteArray(QuestExplore.CELLS, cells.toByteArray());
            assertEquals("50% target with " + example[0] + "% coverage", example[1], progress.invoke(quest, data));
        }
        for (int threshold = 1; threshold <= 80; threshold++) {
            quest.requiredPercent = threshold;
            for (int discovered : new int[]{threshold - 1, threshold, 100}) {
                BitSet cells = new BitSet(); cells.set(0, discovered);
                data.extraData.setByteArray(QuestExplore.CELLS, cells.toByteArray());
                int value = (Integer)progress.invoke(quest, data);
                assertEquals(discovered >= threshold, value == 10000);
            }
        }
        quest.requiredPercent = 50;
        quest.setBounds(0, 64, 0, 3999, 67, 3999);
        assertEquals(1000000, quest.cellCount());
        BitSet cells = new BitSet(); cells.set(0, 499999);
        data.extraData.setByteArray(QuestExplore.CELLS, cells.toByteArray());
        assertEquals(9999, progress.invoke(quest, data));
        cells.set(499999); data.extraData.setByteArray(QuestExplore.CELLS, cells.toByteArray());
        assertEquals(10000, progress.invoke(quest, data));
        quest.horizontalCoverage = true;
        assertEquals(0, progress.invoke(quest, data));
        assertEquals(0, progress.invoke(quest, new Object[]{null}));
        quest.setBounds(0, 256, 0, 3, 300, 3);
        assertEquals(0, progress.invoke(quest, data));
    }

    @Test public void furnaceConsumesRecipeBatchesInOrderAndKeepsSnapshotIndependent() {
        NBTTagList records = new NBTTagList();
        FurnaceProduction.append(records, "12", 2);
        FurnaceProduction.append(records, "12", 3);
        FurnaceProduction.append(records, "34", 4);
        assertEquals(2, records.tagCount());
        NBTTagList normalTake = FurnaceProduction.take(records.copy(), 3);
        assertEquals(3, normalTake.getCompoundTagAt(0).getInteger("Count"));
        assertEquals(5, records.getCompoundTagAt(0).getInteger("Count"));
        NBTTagList shifted = FurnaceProduction.take(records, 7);
        assertEquals("12", shifted.getCompoundTagAt(0).getString("Recipe"));
        assertEquals(5, shifted.getCompoundTagAt(0).getInteger("Count"));
        assertEquals("34", shifted.getCompoundTagAt(1).getString("Recipe"));
        assertEquals(2, shifted.getCompoundTagAt(1).getInteger("Count"));
        assertEquals(2, records.getCompoundTagAt(0).getInteger("Count"));
        FurnaceProduction.take(records, 64);
        assertEquals(0, records.tagCount());
    }
}
