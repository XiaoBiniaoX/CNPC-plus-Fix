package top.cnpcplus.api.script;

import java.util.List;
import java.util.Objects;

/** Literal text or an explicit translation key. Resolution belongs to the client drawing the text. */
public record ApiText(String value, boolean translatable, List<String> arguments) {
    public ApiText {
        Objects.requireNonNull(value, "value");
        arguments = List.copyOf(arguments);
    }
    public static ApiText literal(String value) { return new ApiText(value, false, List.of()); }
    public static ApiText translatable(String key, String... arguments) {
        return new ApiText(key, true, List.of(arguments));
    }
}
