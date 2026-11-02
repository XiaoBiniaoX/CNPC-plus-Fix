package bin.cnpcplus.text;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.NBTTagCompound;

public final class TextStyles {
    public static final String DIALOG = "CNPCPlusDialogTextSpans", LOG = "CNPCPlusQuestLogSpans", COMPLETE = "CNPCPlusQuestCompleteSpans";
    private static final Map<Object, Map<String, StyledText>> DATA = new WeakHashMap<>();
    private TextStyles() { }
    public static StyledText get(Object owner, String key, String text) {
        Map<String, StyledText> values = DATA.computeIfAbsent(owner, unused -> new HashMap<>());
        StyledText value = values.getOrDefault(key, new StyledText(text, "")).edit(text);
        values.put(key, value); return value;
    }
    public static void put(Object owner, String key, StyledText value) { DATA.computeIfAbsent(owner, unused -> new HashMap<>()).put(key, value); }
    public static void read(Object owner, String key, String text, NBTTagCompound tag) { put(owner, key, new StyledText(text, tag.getString(key))); }
    public static void write(Object owner, String key, String text, NBTTagCompound tag) { tag.setString(key, get(owner, key, text).spans); }
}
