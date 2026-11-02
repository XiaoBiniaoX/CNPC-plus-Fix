package top.cnpcplus.text;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.data.Dialog;
import noppes.npcs.controllers.data.Quest;
import noppes.npcs.controllers.data.QuestCategory;

/** Detached snapshots use registry-aware native NBT, including all rewards and options. */
public final class PreviewTextData {
    private PreviewTextData() {}

    public static Dialog dialog(HolderLookup.Provider lookup, Dialog source, StyledText text) {
        Dialog copy = new Dialog(source.category);
        copy.readNBT(lookup, source.save(lookup, new CompoundTag()).copy());
        copy.sound = "";
        copy.text = text.text();
        TextStyles.put(copy, TextStyles.DIALOG, text);
        return copy;
    }

    public static Quest quest(HolderLookup.Provider lookup, Quest source, StyledText text, boolean log) {
        QuestCategory category = new QuestCategory();
        category.title = source.category == null ? "" : source.category.title;
        Quest copy = new Quest(category);
        copy.readNBT(lookup, source.save(lookup, new CompoundTag()).copy());
        copy.logText = log ? text.text() : source.logText;
        copy.completeText = log ? source.completeText : text.text();
        TextStyles.put(copy, TextStyles.LOG, log ? text : TextStyles.get(source, TextStyles.LOG, source.logText));
        TextStyles.put(copy, TextStyles.COMPLETE, log ? TextStyles.get(source, TextStyles.COMPLETE, source.completeText) : text);
        return copy;
    }
}
