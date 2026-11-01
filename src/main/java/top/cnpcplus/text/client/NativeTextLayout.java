package top.cnpcplus.text.client;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import noppes.npcs.shared.client.gui.components.TextContainer;
import noppes.npcs.shared.client.util.TrueTypeFont;
import top.cnpcplus.text.StyledText;

/** Keep CNPC's native line objects and source indices, including the EOF caret slot. */
public final class NativeTextLayout {
    private static final Constructor<?> LINE = lineConstructor();
    private NativeTextLayout() {}

    private static Constructor<?> lineConstructor() {
        try {
            Constructor<?> ctor = Class.forName("noppes.npcs.shared.client.gui.components.TextContainer$LineData")
                    .getDeclaredConstructor(TextContainer.class, String.class, int.class, int.class);
            ctor.setAccessible(true);
            return ctor;
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("CNPC text line signature changed", e); }
    }

    public static List<Object> lines(TextContainer container, TrueTypeFont font, int width) {
        List<StyledText.Line> wrapped = new StyledText(container.text, "").lines(Math.max(1, width - 10), c -> font.width(c.getString()));
        List<Object> result = new ArrayList<>(wrapped.size());
        try {
            for (int i = 0; i < wrapped.size(); i++) {
                StyledText.Line line = wrapped.get(i);
                int end = i + 1 < wrapped.size() ? wrapped.get(i + 1).start() : container.text.length() + 1;
                result.add(LINE.newInstance(container, line.component().getString(), line.start(), end));
            }
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot create CNPC text line", e); }
        return result;
    }

    public static int selectionPos(String text, int start, double x, TrueTypeFont font) {
        int left = 0;
        for (int i = 0; i < text.length();) {
            int next = i + Character.charCount(text.codePointAt(i));
            int right = font.width(text.substring(0, next));
            if (x < (left + right) / 2.0) return start + i;
            left = right;
            i = next;
        }
        return start + text.length();
    }
}
