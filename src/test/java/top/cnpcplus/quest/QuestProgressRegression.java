package top.cnpcplus.quest;

import java.util.BitSet;
import java.util.Map;
import java.util.UUID;
import noppes.npcs.controllers.data.QuestData;

/** Small offline checks for production caps, per-record persistence and exploration dedupe. */
public final class QuestProgressRegression {
    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion();
        try {
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (ExceptionInInitializerError unavailable) {
            Throwable cause = unavailable;
            while (cause.getCause() != null) cause = cause.getCause();
            if (!(cause instanceof NoSuchMethodException)
                    || !"net.minecraftforge.network.NetworkEvent.<init>()".equals(cause.getMessage())) throw unavailable;
            System.out.println("HEADLESS_ONLY: untransformed Forge network hook; not a loader smoke test");
        }
        check(QuestCraft.capProgress(3, 5, 8) == 5, "production exceeded requirement");
        check(QuestCraft.capProgress(3, 5, -2) == 3, "negative production changed progress");
        UUID smallContribution = UUID.randomUUID(), largeContribution = UUID.randomUUID();
        check(QuestDamageLedger.selectContributor(Map.of(smallContribution, 0.25, largeContribution, 4.0))
                        .equals(largeContribution), "player damage contributor was not selected");

        QuestCraft craft = new QuestCraft();
        craft.required[0] = 5;
        QuestData accepted = new QuestData(null);
        craft.addProgress(accepted, 0, 3);
        QuestData otherAcceptance = new QuestData(null);
        check(craft.progress(otherAcceptance).length == 0, "new acceptance inherited progress");
        var saved = new net.minecraft.nbt.CompoundTag();
        accepted.addAdditionalSaveData(saved);
        QuestData resumed = new QuestData(null);
        resumed.readAdditionalSaveData(saved);
        check(craft.progress(resumed)[0] == 3, "accepted quest progress did not persist");

        BitSet visible = QuestRegionRecorder.visibleCells(new int[][]{
                {1, 2, 2, -1}, {2, 3, -1}, {1, 3}
        });
        check(visible.get(1) && visible.get(2) && visible.get(3) && !visible.get(0), "ray-hit threshold incorrect");
        BitSet explored = new BitSet();
        check(QuestRegionRecorder.recordNewCells(explored, (BitSet)visible.clone()), "first discovery was discarded");
        check(explored.cardinality() == 3, "first discovery count incorrect");
        check(!QuestRegionRecorder.recordNewCells(explored, (BitSet)visible.clone()), "repeat scan counted again");
        BitSet independent = new BitSet();
        check(QuestRegionRecorder.recordNewCells(independent, (BitSet)visible.clone()), "separate acceptance did not start independently");

        int baseX = Math.floorDiv(-5, 4), baseY = Math.floorDiv(-2, 4), baseZ = Math.floorDiv(-1, 4);
        check(QuestRegionRecorder.cellIndex(-5, -2, -1, baseX, baseY, baseZ, 2, 3) == 0,
                "negative world coordinates did not map to the first cell");
        check(QuestRegionRecorder.cellIndex(-4, -2, -1, baseX, baseY, baseZ, 2, 3) == 6,
                "negative grid boundary was not split into a new cell");
        check(QuestExplore.dimensionMatches(new QuestData(null), "minecraft:overworld"), "unlocked dimension rejected");
        QuestData locked = new QuestData(null);
        locked.extraData.putString("CNPCPlusExploreDimension", "minecraft:overworld");
        check(!QuestExplore.dimensionMatches(locked, "minecraft:the_nether"), "cross-dimension progress accepted");

        for (int player = 0; player < 2; player++) {
            BitSet scheduled = new BitSet();
            for (int turn = 0; turn < 3; turn++) scheduled.set(QuestRegionRecorder.questIndex(turn * 2L + player, 2, 3));
            check(scheduled.cardinality() == 3, "round-robin starved a player's quest");
        }
        QuestExplore region = new QuestExplore();
        region.x1 = -5; region.y1 = -100; region.z1 = 3;
        region.x2 = 2; region.y2 = 400; region.z2 = -1;
        region.normalize();
        check(region.cellCount(-64, 320) == 3L * 96L * 2L, "height clipping or negative cell count incorrect");
        region.x1 = Integer.MIN_VALUE; region.x2 = Integer.MAX_VALUE;
        check(region.cellCount(-64, 320) == -1, "oversized region accepted");
        System.out.println("QUEST_PROGRESS_REGRESSION=PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
