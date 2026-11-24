package bin.cnpcplus.script.tools;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.function.ToIntFunction;
import java.util.function.BiFunction;

/** Installed on the physical client; every provider call checks the client thread. */
public final class ScriptMeasure {
    private static ToIntFunction<String> width = text -> { throw new IllegalStateException("CNPCPLUS.THREAD.CLIENT_THREAD_ONLY"); };
    private static ToIntFunction<String> height = width;
    private static BiFunction<String, Integer, List<String>> wrap = (text, maxWidth) -> { throw new IllegalStateException("CNPCPLUS.THREAD.CLIENT_THREAD_ONLY"); };
    public static void install(ToIntFunction<String> measureWidth, ToIntFunction<String> measureHeight, BiFunction<String, Integer, List<String>> wrapText) {
        width = measureWidth; height = measureHeight; wrap = wrapText;
    }
    public int textWidth(String text) { return width.applyAsInt(text); }
    public int textHeight(String text) { return height.applyAsInt(text); }
    public List<String> wrappedText(String text, int maxWidth) {
        if (maxWidth < 1) throw new IllegalArgumentException("maxWidth must be positive");
        return Collections.unmodifiableList(new ArrayList<>(wrap.apply(text, maxWidth)));
    }
    public int width(String text) { return textWidth(text); }
    public List<String> wrap(String text, int maxWidth) { return wrappedText(text, maxWidth); }
}
