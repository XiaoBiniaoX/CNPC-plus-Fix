package bin.cnpcplus.quest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.NpcMiscInventory;
import noppes.npcs.api.CustomNPCsException;
import noppes.npcs.api.handler.data.IQuestObjective;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.QuestData;
import noppes.npcs.quests.QuestItem;

/** Recipe-bound objectives; counters live on the player's active QuestData. */
public final class QuestCraft extends QuestItem {
    private static final String PROGRESS = "CNPCPlusCraftProgress";
    public final String[] stations = new String[9];
    public final String[] recipeIds = new String[9];
    public final int[] required = new int[9];

    public QuestCraft() {
        items = new NpcMiscInventory(9);
        java.util.Arrays.fill(stations, "");
        java.util.Arrays.fill(recipeIds, "");
    }

    public boolean configured(int slot) {
        return slot >= 0 && slot < 9 && !items.items.get(slot).isEmpty()
                && CraftStation.byKey(stations[slot]) != null && !recipeIds[slot].isEmpty();
    }

    public void setRecipe(int slot, String station, String recipeId) {
        if (slot < 0 || slot >= 9 || CraftStation.byKey(station) == null || recipeId == null || recipeId.isEmpty()) return;
        stations[slot] = station;
        recipeIds[slot] = recipeId;
    }

    public int requiredCount(int slot) {
        return required[slot] > 0 ? required[slot] : items.items.get(slot).getCount();
    }

    @Override public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        for (int i = 0; i < 9; i++) {
            stations[i] = tag.getString("CNPCPlusCraftStation" + i);
            recipeIds[i] = tag.getString("CNPCPlusCraftRecipe" + i);
            required[i] = Math.max(0, Math.min(1000000, tag.getInteger("CNPCPlusCraftRequired" + i)));
        }
    }

    @Override public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        for (int i = 0; i < 9; i++) {
            tag.setString("CNPCPlusCraftStation" + i, stations[i] == null ? "" : stations[i]);
            tag.setString("CNPCPlusCraftRecipe" + i, recipeIds[i] == null ? "" : recipeIds[i]);
            tag.setInteger("CNPCPlusCraftRequired" + i, required[i]);
        }
    }

    public int[] progress(QuestData data) { return data.extraData.getIntArray(PROGRESS); }

    public void addProgress(QuestData data, int slot, int amount) {
        int[] values = java.util.Arrays.copyOf(progress(data), 9);
        int required = requiredCount(slot);
        values[slot] = capProgress(values[slot], required, amount);
        data.extraData.setIntArray(PROGRESS, values);
    }

    public static int capProgress(int current, int required, int amount) {
        return (int)Math.min(Math.max(0, required), Math.max(0L, current) + Math.max(0L, amount));
    }

    public static boolean matches(ItemStack target, ItemStack produced) {
        return target != null && produced != null && !target.isEmpty() && !produced.isEmpty()
                && ItemStack.areItemsEqual(target, produced) && ItemStack.areItemStackTagsEqual(target, produced);
    }

    @Override public boolean isCompleted(EntityPlayer player) {
        QuestData data = PlayerData.get(player).questData.activeQuests.get(questId);
        if (data == null) return false;
        int[] values = progress(data);
        boolean any = false;
        for (int i = 0; i < 9; i++) {
            if (!configured(i)) continue;
            any = true;
            if ((i < values.length ? values[i] : 0) < requiredCount(i)) return false;
        }
        return any;
    }

    @Override public void handleComplete(EntityPlayer player) {}

    @Override public IQuestObjective[] getObjectives(EntityPlayer player) {
        List<IQuestObjective> result = new ArrayList<IQuestObjective>();
        for (int i = 0; i < 9; i++) if (configured(i)) result.add(new CraftObjective(player, i));
        return result.toArray(new IQuestObjective[result.size()]);
    }

    private final class CraftObjective implements IQuestObjective {
        private final EntityPlayer player;
        private final int slot;
        private CraftObjective(EntityPlayer player, int slot) { this.player = player; this.slot = slot; }
        private int current() {
            QuestData data = PlayerData.get(player).questData.activeQuests.get(questId);
            int[] values = data == null ? new int[0] : progress(data);
            return slot < values.length ? values[slot] : 0;
        }
        @Override public int getProgress() { return current(); }
        @Override public void setProgress(int value) {
            int required = requiredCount(slot);
            if (value < 0 || value > required) throw new CustomNPCsException("Progress has to be between 0 and " + required);
            QuestData data = PlayerData.get(player).questData.activeQuests.get(questId);
            if (data == null) return;
            int[] values = java.util.Arrays.copyOf(progress(data), 9);
            if (values[slot] == value) return;
            values[slot] = value;
            data.extraData.setIntArray(PROGRESS, values);
            PlayerData.get(player).questData.checkQuestCompletion(player, 6);
            PlayerData.get(player).updateClient = true;
        }
        @Override public int getMaxProgress() { return requiredCount(slot); }
        @Override public boolean isCompleted() { return current() >= getMaxProgress(); }
        @Override public String getText() {
            return net.minecraft.util.text.translation.I18n.translateToLocal("cnpcplus.quest.source." + stations[slot])
                    + " / " + items.items.get(slot).getDisplayName() + ": " + current() + "/" + getMaxProgress();
        }
    }
}
