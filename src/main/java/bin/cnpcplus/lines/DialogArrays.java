package bin.cnpcplus.lines;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.controllers.data.DialogOption;

public final class DialogArrays {
    private DialogArrays() { }
    public static List<DialogOption> ordered(HashMap<Integer, DialogOption> dialogs) {
        return LinesData.slots(dialogs).stream().map(dialogs::get).collect(Collectors.toList());
    }
    public static NBTTagList save(HashMap<Integer, DialogOption> dialogs) {
        NBTTagList list = new NBTTagList();
        for (int slot : LinesData.slots(dialogs)) {
            NBTTagCompound item = new NBTTagCompound();
            item.setInteger("DialogSlot", slot);
            item.setTag("NPCDialog", dialogs.get(slot).writeNBT());
            list.appendTag(item);
        }
        return list;
    }
    public static HashMap<Integer, DialogOption> read(NBTTagList list) {
        HashMap<Integer, DialogOption> dialogs = new HashMap<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound item = list.getCompoundTagAt(i);
            int slot = item.getInteger("DialogSlot");
            if (slot < 0 || !item.hasKey("NPCDialog", 10)) continue;
            DialogOption option = new DialogOption();
            option.readNBT(item.getCompoundTag("NPCDialog"));
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
