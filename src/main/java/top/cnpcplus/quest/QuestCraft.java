package top.cnpcplus.quest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.api.CustomNPCsException;
import noppes.npcs.api.handler.data.IQuestObjective;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.QuestData;
import noppes.npcs.quests.QuestItem;

/** Craft objectives are definitions on the quest; progress belongs to each active QuestData. */
public final class QuestCraft extends QuestItem {
    public final int[] required = new int[9];
    public final String[] stations = new String[9];
    public final String[] recipeIds = new String[9];
    public final String[] recipeNames = new String[9];
    public final ItemStack[] outputs = new ItemStack[9];

    public QuestCraft() {
        java.util.Arrays.fill(required, 1);
        java.util.Arrays.fill(stations, "");
        java.util.Arrays.fill(recipeIds, "");
        java.util.Arrays.fill(recipeNames, "");
        java.util.Arrays.fill(outputs, ItemStack.EMPTY);
    }

    public boolean configured(int slot) {
        return slot >= 0 && slot < 9 && CraftStation.byKey(stations[slot]) != null
                && !recipeIds[slot].isEmpty() && !outputs[slot].isEmpty() && required[slot] > 0;
    }

    public void setRecipe(int slot, String station, String recipeId, String name, ItemStack output) {
        if (slot < 0 || slot >= 9 || CraftStation.byKey(station) == null || recipeId == null || recipeId.isBlank()) return;
        stations[slot] = station;
        recipeIds[slot] = recipeId;
        recipeNames[slot] = name == null ? "" : name;
        outputs[slot] = output == null ? ItemStack.EMPTY : output.copy();
    }

    public void clearRecipe(int slot) {
        if (slot < 0 || slot >= 9) return;
        stations[slot] = "";
        recipeIds[slot] = "";
        recipeNames[slot] = "";
        outputs[slot] = ItemStack.EMPTY;
    }

    public String recipeName(int slot) {
        return recipeNames[slot].isBlank() ? recipeIds[slot] : recipeNames[slot];
    }

    public static boolean matchesRecipe(String expectedStation, String expectedId, String actualStation, String actualId) {
        return expectedStation != null && expectedStation.equals(actualStation)
                && expectedId != null && expectedId.equals(actualId);
    }

    public record RecipeReference(String station, String id) {}

    public static RecipeReference furnaceRecipe(String id) {
        if (id == null) return null;
        String[][] prefixes = {{"cnpcplus:smelting/", "furnace"},
                {"cnpcplus:blasting/", "blast_furnace"}, {"cnpcplus:smoking/", "smoker"}};
        for (String[] prefix : prefixes) if (id.startsWith(prefix[0])) {
            String recipeId = id.substring(prefix[0].length());
            return recipeId.matches("\\d+") ? new RecipeReference(prefix[1], recipeId) : null;
        }
        return null;
    }

    public static boolean isWorkbenchGrid(int size) { return size == 9; }

    public static int takeBrewRecipe(int[] recipes, long[] batches, int slot, boolean outputEmpty) {
        if (slot < 0 || slot >= recipes.length || slot >= batches.length
                || recipes[slot] < 0 || batches[slot] < 0) return -1;
        int recipe = recipes[slot];
        if (outputEmpty) {
            recipes[slot] = -1;
            batches[slot] = -1;
        }
        return recipe;
    }

    public int[] progress(QuestData data) {
        return data.extraData.getIntArray("CNPCPlusCraftProgress");
    }

    public void addProgress(QuestData data, int slot, int amount) {
        int[] values = java.util.Arrays.copyOf(progress(data), 9);
        values[slot] = capProgress(values[slot], required[slot], amount);
        data.extraData.putIntArray("CNPCPlusCraftProgress", values);
    }

    public static int capProgress(int current, int required, int amount) {
        return (int)Math.min(Math.max(0, required), Math.max(0L, current) + Math.max(0L, amount));
    }

    public static int producedItems(int completions, int outputPerCompletion) {
        if (completions <= 0 || outputPerCompletion <= 0) return 0;
        return (int)Math.min(Integer.MAX_VALUE, (long)completions * outputPerCompletion);
    }

    public static ItemStack previewStack(ItemStack stack, int count) {
        ItemStack preview = stack.copy();
        if (!preview.isEmpty()) preview.setCount(Math.max(1, count));
        return preview;
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.core.HolderLookup.Provider registries, CompoundTag tag) {
        super.readAdditionalSaveData(registries, tag);
        java.util.Arrays.fill(required, 1);
        java.util.Arrays.fill(stations, "");
        java.util.Arrays.fill(recipeIds, "");
        java.util.Arrays.fill(recipeNames, "");
        java.util.Arrays.fill(outputs, ItemStack.EMPTY);
        ListTag list = tag.getList("CNPCPlusCraftRecipes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            int slot = row.getInt("Slot");
            if (slot < 0 || slot >= 9) continue;
            String station = row.getString("Station");
            String recipeId = row.getString("RecipeId");
            if (CraftStation.byKey(station) == null || recipeId.isBlank()) continue;
            required[slot] = Math.max(1, row.getInt("Required"));
            setRecipe(slot, station, recipeId, row.getString("Name"), ItemStack.parseOptional(registries, row.getCompound("Output")));
        }
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.core.HolderLookup.Provider registries, CompoundTag tag) {
        super.addAdditionalSaveData(registries, tag);
        ListTag list = new ListTag();
        for (int i = 0; i < 9; i++) {
            if (!configured(i)) continue;
            CompoundTag row = new CompoundTag();
            row.putInt("Slot", i);
            row.putInt("Required", required[i]);
            row.putString("Station", stations[i]);
            row.putString("RecipeId", recipeIds[i]);
            row.putString("Name", recipeNames[i]);
            row.put("Output", outputs[i].save(registries));
            list.add(row);
        }
        tag.put("CNPCPlusCraftRecipes", list);
    }

    @Override
    public boolean isCompleted(Player player) {
        QuestData data = PlayerData.get(player).questData.activeQuests.get(questId);
        if (data == null) return false;
        int[] values = progress(data);
        boolean any = false;
        for (int i = 0; i < 9; i++) {
            if (!configured(i)) continue;
            any = true;
            if ((i < values.length ? values[i] : 0) < required[i]) return false;
        }
        return any;
    }

    @Override public void handleComplete(Player player) {}

    @Override
    public IQuestObjective[] getObjectives(Player player) {
        List<IQuestObjective> result = new ArrayList<>();
        for (int i = 0; i < 9; i++) if (configured(i)) result.add(new CraftObjective(player, i));
        return result.toArray(IQuestObjective[]::new);
    }

    private final class CraftObjective implements IQuestObjective {
        private final Player player;
        private final int slot;
        private CraftObjective(Player player, int slot) { this.player = player; this.slot = slot; }
        private int current() {
            QuestData data = PlayerData.get(player).questData.activeQuests.get(questId);
            int[] values = data == null ? new int[0] : progress(data);
            return slot < values.length ? values[slot] : 0;
        }
        @Override public int getProgress() { return current(); }
        @Override public void setProgress(int value) {
            if (value < 0 || value > required[slot]) throw new CustomNPCsException("Progress has to be between 0 and " + required[slot]);
            QuestData data = PlayerData.get(player).questData.activeQuests.get(questId);
            if (data == null) return;
            int[] values = java.util.Arrays.copyOf(progress(data), 9);
            if (values[slot] == value) return;
            values[slot] = value;
            data.extraData.putIntArray("CNPCPlusCraftProgress", values);
            PlayerData.get(player).questData.checkQuestCompletion(player, 6);
            PlayerData.get(player).updateClient = true;
        }
        @Override public int getMaxProgress() { return required[slot]; }
        @Override public boolean isCompleted() { return current() >= required[slot]; }
        @Override public String getText() { return getMCText().getString(); }
        @Override public Component getMCText() {
            CraftStation station = CraftStation.byKey(stations[slot]);
            if (station == null) return Component.translatable("cnpcplus.quest.craft.unset");
            return Component.translatable("cnpcplus.quest.craft.objective", Component.translatable(station.translationKey),
                    outputs[slot].getHoverName(), current(), required[slot]);
        }
    }
}
