package top.cnpcplus.text;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.data.Dialog;
import noppes.npcs.controllers.data.Quest;
import noppes.npcs.controllers.data.QuestCategory;

/** Preview snapshots never share mutable options, rewards or text state with an editor. */
public final class PreviewTextData {
    private PreviewTextData() {}

    public static Dialog dialog(Dialog source, StyledText text) {
        Dialog copy = new Dialog(source.category);
        copy.readNBT(source.save(new CompoundTag()).copy());
        copy.sound = ""; // Must be silent before the native screen's constructor calls appendDialog.
        copy.text = text.text();
        TextStyles.put(copy, TextStyles.DIALOG, text);
        return copy;
    }

    public static Quest quest(Quest source, StyledText text, boolean log) {
        QuestCategory category = new QuestCategory();
        category.title = source.category == null ? "" : source.category.title;
        Quest copy = new Quest(category);
        copy.readNBT(source.save(new CompoundTag()).copy());
        copy.logText = log ? text.text() : source.logText;
        copy.completeText = log ? source.completeText : text.text();
        TextStyles.put(copy, TextStyles.LOG, log ? text : TextStyles.get(source, TextStyles.LOG, source.logText));
        TextStyles.put(copy, TextStyles.COMPLETE, log ? TextStyles.get(source, TextStyles.COMPLETE, source.completeText) : text);
        return copy;
    }
}
