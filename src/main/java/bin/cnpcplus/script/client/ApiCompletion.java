package bin.cnpcplus.script.client;

import java.lang.reflect.Type;
import java.util.List;

/** Existing client metadata entry point; analysis itself is shared with server-safe addon APIs. */
public final class ApiCompletion {
    private ApiCompletion() {}
    public record Entry(String name, String insert, String signature, Type type, boolean method) {}
    public record Result(int start, List<Entry> entries) {}

    public static Result complete(String text, int cursor, String context, List<String> hooks) {
        var result = bin.cnpcplus.script.ApiCompletion.complete(text, cursor, context, hooks);
        return new Result(result.start(), result.entries().stream().map(ApiCompletion::entry).toList());
    }

    public static List<Entry> hint(String text, int cursor, String context) {
        return bin.cnpcplus.script.ApiCompletion.hint(text, cursor, context).stream().map(ApiCompletion::entry).toList();
    }

    public static List<Entry> members(Class<?> owner) {
        return bin.cnpcplus.script.ApiCompletion.members(owner).stream().map(ApiCompletion::entry).toList();
    }

    public static Class<?> runtimeWrapper(Class<?> owner) {
        return bin.cnpcplus.script.ApiCompletion.runtimeWrapper(owner);
    }

    private static Entry entry(bin.cnpcplus.script.ApiCompletion.Entry entry) {
        return new Entry(entry.name(), entry.insert(), entry.signature(), entry.type(), entry.method());
    }
}
