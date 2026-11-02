package top.cnpcplus.lines;

import java.util.HashMap;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import noppes.npcs.controllers.data.DialogOption;

/** Native persisted slot is the priority in both the editor and interaction. */
public final class DialogArrays {
    private DialogArrays() {}
    public static List<DialogOption> ordered(HashMap<Integer, DialogOption> dialogs) {
        return LinesData.slots(dialogs).stream().map(dialogs::get).toList();
    }
    public static ListTag save(HashMap<Integer, DialogOption> dialogs) {
        ListTag list = new ListTag();
        for (int slot : LinesData.slots(dialogs)) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("DialogSlot", slot);
            entry.put("NPCDialog", dialogs.get(slot).writeNBT());
            list.add(entry);
        }
        return list;
    }
    public static HashMap<Integer, DialogOption> read(ListTag list) {
        HashMap<Integer, DialogOption> dialogs = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getInt("DialogSlot");
            if (slot < 0 || !entry.contains("NPCDialog", Tag.TAG_COMPOUND)) continue;
            DialogOption option = new DialogOption();
            option.readNBT(entry.getCompound("NPCDialog"));
            option.optionType = 1;
            option.slot = slot;
            dialogs.put(slot, option);
        }
        return dialogs;
    }

    public static DialogOption copy(DialogOption option) {
        DialogOption copy = new DialogOption();
        copy.readNBT(option.writeNBT().copy());
        copy.slot = option.slot;
        return copy;
    }
}
