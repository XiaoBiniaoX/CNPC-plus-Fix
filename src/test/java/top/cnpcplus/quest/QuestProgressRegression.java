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
        check(QuestCraft.producedItems(1, 4) == 4, "one recipe did not count its four produced items");
        check(QuestCraft.producedItems(2, 4) == 8, "multiple recipe outputs were not counted as items");
        check(QuestCraft.producedItems(Integer.MAX_VALUE, 4) == Integer.MAX_VALUE, "production count overflowed");
        check(QuestCraft.producedItems(3, 0) == 0, "invalid recipe output size counted progress");
        for (CraftStation station : CraftStation.values()) {
            check(CraftStation.byKey(station.key) == station, "station key was not registered: " + station.key);
            check(QuestCraft.matchesRecipe(station.key, "17", station.key, "17"),
                    "matching station/recipe identity rejected: " + station.key);
            check(!QuestCraft.matchesRecipe(station.key, "17", "other", "17"),
                    "wrong station accepted: " + station.key);
            check(!QuestCraft.matchesRecipe(station.key, "17", station.key, "18"),
                    "wrong recipe accepted: " + station.key);
        }
        check(QuestCraft.furnaceRecipe("cnpcplus:smelting/12").station().equals("furnace")
                        && QuestCraft.furnaceRecipe("cnpcplus:blasting/12").station().equals("blast_furnace")
                        && QuestCraft.furnaceRecipe("cnpcplus:smoking/12").station().equals("smoker"),
                "one of the three furnace station keys was routed incorrectly");
        check(QuestCraft.isWorkbenchGrid(9) && !QuestCraft.isWorkbenchGrid(16),
                "workbench production grid size does not match the vanilla 3x3 menu");
        var sourceOutput = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 4);
        var previewOutput = QuestCraft.previewStack(sourceOutput, 3);
        check(previewOutput.getCount() == 3 && sourceOutput.getCount() == 4 && previewOutput != sourceOutput,
                "required-count preview changed the recipe output stack");
        check(QuestCraft.previewStack(net.minecraft.world.item.ItemStack.EMPTY, 3).isEmpty(),
                "empty recipe output became visible in preview");
        check(QuestExplore.coordinateInputAllowed("-123") && QuestExplore.coordinateInputAllowed("123")
                        && QuestExplore.coordinateInputAllowed("") && QuestExplore.coordinateInputAllowed("-")
                        && !QuestExplore.coordinateInputAllowed("+1") && !QuestExplore.coordinateInputAllowed("1.5")
                        && !QuestExplore.coordinateInputAllowed("--1"),
                "signed coordinate editing accepted invalid input or rejected a negative coordinate");
        QuestExplore exploreText = new QuestExplore();
        check(exploreText.getProgressText().equals("探索区域....吧！"), "exploration progress label default changed");
        exploreText.setProgressText("已探访");
        var exploreTextTag = new net.minecraft.nbt.CompoundTag();
        exploreText.addAdditionalSaveData(exploreTextTag);
        QuestExplore loadedExploreText = new QuestExplore();
        loadedExploreText.readAdditionalSaveData(exploreTextTag);
        check(loadedExploreText.getProgressText().equals("已探访"), "exploration progress label did not persist");
        check(QuestExplore.formatProgressLabel("探索区域....吧！", 12.345)
                        .equals("探索区域....吧！: 12.35%"), "exploration percentage was not rounded to two decimals");
        var legacyExploreTag = new net.minecraft.nbt.CompoundTag();
        loadedExploreText.readAdditionalSaveData(legacyExploreTag);
        check(loadedExploreText.getProgressText().equals(QuestExplore.DEFAULT_PROGRESS_TEXT),
                "legacy exploration task missed its progress label default");
        check(QuestCraft.furnaceRecipe("cnpcplus:smelting/12").equals(
                new QuestCraft.RecipeReference("furnace", "12")), "furnace recipe identity was not parsed");
        check(QuestCraft.furnaceRecipe("cnpcplus:blasting/not-a-number") == null,
                "invalid custom furnace identity was accepted");
        check(QuestCraft.furnaceRecipe("minecraft:smelting/12") == null,
                "vanilla furnace recipe entered custom progress");
        int[] brewRecipes = {7, 7, 7};
        long[] brewBatches = {24L, 24L, 24L};
        check(QuestCraft.takeBrewRecipe(brewRecipes, brewBatches, 1, false) == 7
                        && QuestCraft.takeBrewRecipe(brewRecipes, brewBatches, 0, false) == 7,
                "separate output bottles did not retain their recipe identity");
        check(QuestCraft.takeBrewRecipe(brewRecipes, brewBatches, 2, true) == 7
                        && QuestCraft.takeBrewRecipe(brewRecipes, brewBatches, 2, true) == -1,
                "an exhausted bottle retained its recipe identity");
        UUID smallContribution = UUID.randomUUID(), largeContribution = UUID.randomUUID();
        check(QuestDamageLedger.selectContributor(Map.of(smallContribution, 0.25, largeContribution, 4.0))
                        .equals(largeContribution), "player damage contributor was not selected");

        QuestCraft craft = new QuestCraft();
        craft.required[0] = 5;
        craft.items.items.set(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
        check(!craft.configured(0), "legacy item-only target became a recipe objective");
        var legacyCraftTag = new net.minecraft.nbt.CompoundTag();
        craft.addAdditionalSaveData(legacyCraftTag);
        QuestCraft loadedLegacyCraft = new QuestCraft();
        loadedLegacyCraft.readAdditionalSaveData(legacyCraftTag);
        check(!loadedLegacyCraft.configured(0)
                        && loadedLegacyCraft.items.items.get(0).is(net.minecraft.world.item.Items.DIAMOND),
                "legacy item-only save became a recipe objective or lost its old target");
        craft.setRecipe(1, "anvil", "7", "Example recipe",
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
        craft.required[1] = 4;
        var craftTag = new net.minecraft.nbt.CompoundTag();
        craft.addAdditionalSaveData(craftTag);
        QuestCraft loadedCraft = new QuestCraft();
        loadedCraft.readAdditionalSaveData(craftTag);
        check(loadedCraft.configured(1) && loadedCraft.required[1] == 4
                        && loadedCraft.stations[1].equals("anvil") && loadedCraft.recipeIds[1].equals("7")
                        && loadedCraft.outputs[1].getCount() == 3,
                "recipe identity, count, or output snapshot did not persist");
        check(QuestCraft.matchesRecipe("anvil", "7", "anvil", "7"), "matching recipe identity rejected");
        check(!QuestCraft.matchesRecipe("anvil", "7", "smithing", "7"), "different workstation was accepted");
        check(!QuestCraft.matchesRecipe("anvil", "7", "anvil", "8"), "different recipe was accepted");
        QuestData accepted = new QuestData(null);
        craft.addProgress(accepted, 0, 3);
        QuestData otherAcceptance = new QuestData(null);
        check(craft.progress(otherAcceptance).length == 0, "new acceptance inherited progress");
        var saved = new net.minecraft.nbt.CompoundTag();
        accepted.addAdditionalSaveData(saved);
        QuestData resumed = new QuestData(null);
        resumed.readAdditionalSaveData(saved);
        check(craft.progress(resumed)[0] == 3, "accepted quest progress did not persist");

        QuestAreaKill areaKill = new QuestAreaKill();
        areaKill.targets.put("minecraft:zombie", 4);
        areaKill.refreshTargets();
        check(areaKill.targets.getClass().getName().endsWith("$AreaTargets"), "area target filter wrapper was lost");
        check(areaKill.targets.get("minecraft:zombie") == 4, "refreshing area targets discarded configured counts");

        BitSet visible = QuestRegionRecorder.visibleCells(new int[][]{
                {1, 2, 2, -1}, {2, 3, -1}, {1, 3}
        });
        check(visible.get(1) && visible.get(2) && visible.get(3) && !visible.get(0), "visible ray cells were not aggregated");
        BitSet singleRay = QuestRegionRecorder.visibleCells(new int[][]{{7, 7}});
        check(singleRay.cardinality() == 1 && singleRay.get(7), "a cell seen by one gaze ray did not count");
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
        region.horizontalCoverage = true;
        check(region.cellCount(-64, 320) == 3L * 2L, "horizontal coverage still counted every Y layer");
        var horizontalTag = new net.minecraft.nbt.CompoundTag();
        region.addAdditionalSaveData(horizontalTag);
        QuestExplore loadedHorizontal = new QuestExplore();
        loadedHorizontal.readAdditionalSaveData(horizontalTag);
        check(loadedHorizontal.horizontalCoverage && loadedHorizontal.cellCount(-64, 320) == 3L * 2L,
                "horizontal coverage mode did not persist");
        check(QuestRegionRecorder.horizontalCellIndex(-5, -1, baseX, baseZ, 2) == 0
                        && QuestRegionRecorder.horizontalCellIndex(-4, -1, baseX, baseZ, 2) == 2,
                "horizontal cells did not merge Y layers or preserve X/Z boundaries");
        var volumeProgress = new net.minecraft.nbt.CompoundTag();
        check(!loadedHorizontal.progressModeMatches(volumeProgress), "missing mode marker was reused by horizontal mode");
        loadedHorizontal.horizontalCoverage = false;
        check(loadedHorizontal.progressModeMatches(volumeProgress), "missing mode marker was not treated as legacy volume");
        volumeProgress.putBoolean(QuestExplore.COVERAGE_MODE_KEY, false);
        loadedHorizontal.horizontalCoverage = true;
        check(!loadedHorizontal.progressModeMatches(volumeProgress), "volume progress was reused by horizontal mode");
        region.x1 = Integer.MIN_VALUE; region.x2 = Integer.MAX_VALUE;
        check(region.cellCount(-64, 320) == -1, "oversized region accepted");
        System.out.println("QUEST_PROGRESS_REGRESSION=PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
