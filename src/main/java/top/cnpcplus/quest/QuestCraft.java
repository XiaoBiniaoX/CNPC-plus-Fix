package top.cnpcplus.quest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.api.CustomNPCsException;
import noppes.npcs.api.handler.data.IQuestObjective;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.QuestData;
import noppes.npcs.quests.QuestItem;

/** Craft objectives are definitions on the quest; progress belongs to each active QuestData. */
public final class QuestCraft extends QuestItem {
    public final int[] required = new int[9];
    public final ItemProductionEvent.Source[] sources = new ItemProductionEvent.Source[9];

    public QuestCraft() {
        java.util.Arrays.fill(required, 1);
        java.util.Arrays.fill(sources, ItemProductionEvent.Source.WORKBENCH);
    }

    public boolean configured(int slot) {
        return slot >= 0 && slot < 9 && !items.items.get(slot).isEmpty() && required[slot] > 0;
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

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        java.util.Arrays.fill(required, 1);
        java.util.Arrays.fill(sources, ItemProductionEvent.Source.WORKBENCH);
        ListTag list = tag.getList("CNPCPlusCraftTargets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            int slot = row.getInt("Slot");
            if (slot < 0 || slot >= 9) continue;
            required[slot] = Math.max(1, row.getInt("Required"));
            int source = row.getInt("Source");
            sources[slot] = source >= 0 && source < ItemProductionEvent.Source.values().length
                    ? ItemProductionEvent.Source.values()[source] : ItemProductionEvent.Source.WORKBENCH;
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ListTag list = new ListTag();
        for (int i = 0; i < 9; i++) {
            if (!configured(i)) continue;
            CompoundTag row = new CompoundTag();
            row.putInt("Slot", i);
            row.putInt("Required", required[i]);
            row.putInt("Source", sources[i].ordinal());
            list.add(row);
        }
        tag.put("CNPCPlusCraftTargets", list);
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
            return items.items.get(slot).getHoverName().copy().append(Component.literal(": " + current() + "/" + required[slot]));
        }
    }
}
