package top.cnpcplus.text;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;

/** Shared by native NBT/copy and the editor; values cannot alias mutable data across NPCs. */
public final class TextStyles {
    public static final String DIALOG = "CNPCPlusDialogTextSpans";
    public static final String LOG = "CNPCPlusQuestLogSpans";
    public static final String COMPLETE = "CNPCPlusQuestCompleteSpans";
    public static final String LINE = "Spans";
    private static final Map<Object, Map<String, StyledText>> DATA = new WeakHashMap<>();
    private TextStyles() {}

    public static synchronized StyledText get(Object owner, String key, String text) {
        Map<String, StyledText> values = DATA.computeIfAbsent(owner, unused -> new HashMap<>());
        StyledText value = values.getOrDefault(key, new StyledText(text, "")).edit(text);
        values.put(key, value);
        return value;
    }

    public static synchronized void put(Object owner, String key, StyledText value) {
        DATA.computeIfAbsent(owner, unused -> new HashMap<>()).put(key, value);
    }

    public static void read(Object owner, String key, String text, CompoundTag tag) {
        put(owner, key, new StyledText(text, tag.getString(key)));
    }

    public static void save(Object owner, String key, String text, CompoundTag tag) {
        tag.putString(key, get(owner, key, text).spans());
    }
}
