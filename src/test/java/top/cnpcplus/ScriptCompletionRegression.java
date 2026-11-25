package top.cnpcplus;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import noppes.npcs.controllers.ScriptContainer;
import top.cnpcplus.api.script.ScriptCheckApi;
import top.cnpcplus.api.script.ScriptCompletionApi;
import top.cnpcplus.api.script.ScriptCompletionApi.Language;
import top.cnpcplus.api.script.ScriptCompletionApi.Request;
import top.cnpcplus.api.script.ScriptCompletionApi.Result;
import top.cnpcplus.api.script.ScriptCompletionApi.Suggestion;
import top.cnpcplus.script.ScriptDiagnostics;

/** Executes the text API in an isolated JVM classpath with all client classes and language engines forbidden. */
public final class ScriptCompletionRegression {
    public static void main(String[] args) throws Exception {
        String[] paths = System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator));
        URL[] urls = new URL[paths.length];
        for (int i = 0; i < paths.length; i++) urls[i] = Path.of(paths[i]).toUri().toURL();
        try (var server = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                // Forge puts this common, server-present BlockAndTintGetter parent in a client-named package.
                if (name.startsWith("net.minecraft.client.") || name.startsWith("net.minecraftforge.client.")
                        && !name.equals("net.minecraftforge.client.extensions.IForgeBlockAndTintGetter")
                        || name.startsWith("noppes.npcs.client.") || name.startsWith("noppes.npcs.shared.client.")
                        || name.startsWith("top.cnpcplus.") && name.contains(".client.")
                        || name.startsWith("org.lwjgl.") || name.startsWith("org.openjdk.nashorn.")
                        || name.startsWith("jdk.nashorn."))
                    throw new AssertionError("Client/engine class loaded: " + name);
                return super.loadClass(name, resolve);
            }
        }) {
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(server);
            try { server.loadClass(ScriptCompletionRegression.class.getName()).getMethod("run").invoke(null); }
            finally { Thread.currentThread().setContextClassLoader(previous); }
        }
        System.out.println("SCRIPT_COMPLETION_REGRESSION=PASS (client/engine classes forbidden; no game started)");
    }

    public static void run() {
        check(ScriptCompletionApi.registeredIds().isEmpty(), "unexpected starting registration");
        check(ScriptCompletionApi.supports(" ECMAScript ") && ScriptCompletionApi.usesBuiltinChecks("JavaScript"),
                "native ES5 labels lost");
        for (String other : List.of("Python", "Java", "JavaScript ES2024", "ECMAScript 2024", "NotJavaScript")) {
            check(!ScriptCompletionApi.supports(other) && !ScriptCompletionApi.usesBuiltinChecks(other),
                    "language matched by substring: " + other);
            check(ScriptCompletionApi.complete(new Request("", 0, other, null, null, true)).suggestions().isEmpty(),
                    "ES5 suggestions leaked to " + other);
        }
        String text = "function interact(e) { e.player.getN";
        var nativeResult = ScriptCompletionApi.complete(new Request(text, text.length(), "ECMAScript", "NpcEvent", null, false));
        var getName = nativeResult.suggestions().stream().filter(s -> s.insertText().equals("getName()")).findFirst().orElseThrow();
        check(getName.start() == text.length() - 4 && getName.end() == text.length(), "native replacement range changed");
        text = "function interact(e) { e.player.getName()";
        int cursor = text.indexOf("getName") + 4;
        var middle = ScriptCompletionApi.complete(new Request(text, cursor, "JavaScript", "NpcEvent", null, true));
        var same = middle.suggestions().stream().filter(s -> s.insertText().equals("getName")).findFirst().orElseThrow();
        check(same.end() == text.indexOf('(', text.indexOf("getName")), "identifier suffix/existing parentheses lost");
        text = "function interact(e) { e.player.setPosition(";
        check(ScriptCompletionApi.complete(new Request(text, text.length(), "js", "NpcEvent", null, false))
                .signatures().stream().anyMatch(s -> s.startsWith("setPosition(")), "native signatures lost");
        check(ScriptCompletionApi.complete(new Request("", 0, "js", "NpcEvent", null, false)).suggestions().isEmpty(),
                "native suggestions open on idle empty source");
        check(!ScriptCompletionApi.complete(new Request("", 0, "js", "NpcEvent", List.of("addonHook"), true))
                .suggestions().isEmpty(), "explicit native completion failed");
        for (String literal : List.of("// e.player.getN", "'e.player.getN", "/* e.player.getN"))
            check(ScriptCompletionApi.complete(new Request(literal, literal.length(), "js", "NpcEvent", null, true))
                    .suggestions().isEmpty(), "literal/comment completion escaped");

        long revision = ScriptCompletionApi.revision();
        var aliases = new java.util.HashSet<>(Set.of("Python", "GraalPy"));
        Language python = new Language(aliases, false, false, "自定义帮助 / custom help");
        aliases.clear();
        ScriptCompletionApi.register("test:python", python, (request, defaults) -> {
            check(defaults.suggestions().isEmpty() && defaults.signatures().isEmpty(), "ES5 defaults leaked to Python");
            check(request.context().equals("NpcEvent") && request.hooks().equals(List.of("interact")) && request.explicit(),
                    "provider request context/hooks/trigger lost");
            return new Result(List.of(new Suggestion("显示名", "say(\"\")", "说明\n第二行", 3, 5, 5)), List.of("say(text: str)"));
        });
        try {
            check(ScriptCompletionApi.revision() > revision && ScriptCompletionApi.supports(" gRaAlPy ")
                    && !ScriptCompletionApi.supports("GraalPy extra"), "exact normalized aliases/revision failed");
            check(ScriptCompletionApi.language("Python").helpText().equals("自定义帮助 / custom help"), "help text not retained");
            var hooks = new ArrayList<>(List.of("interact"));
            Request request = new Request("😀.sa!", 5, "Python", "NpcEvent", hooks, true);
            hooks.clear();
            Result result = ScriptCompletionApi.complete(request);
            Suggestion suggestion = result.suggestions().get(0);
            String applied = request.text().substring(0, suggestion.start()) + suggestion.insertText()
                    + request.text().substring(suggestion.end());
            check(applied.equals("😀.say(\"\")!") && suggestion.start() + suggestion.caret() == 8,
                    "UTF-16 range/insertion/caret contract broken");
            check(suggestion.label().equals("显示名") && suggestion.detail().equals("说明\n第二行")
                    && result.signatures().equals(List.of("say(text: str)")), "custom display/signature text lost");
            check(ScriptCompletionApi.registeredIds().equals(Set.of("test:python")), "registration snapshot failed");
            boolean conflict = false;
            try { ScriptCompletionApi.register("test:conflict", python, (r, d) -> d); }
            catch (IllegalArgumentException expected) { conflict = true; }
            check(conflict && !ScriptCompletionApi.registeredIds().contains("test:conflict"), "alias collision not rejected atomically");
            ScriptCompletionApi.register("test:python", new Language(Set.of("Java"), false, false, "java.help"),
                    (r, d) -> new Result(List.of(), List.of("Java replacement")));
            check(!ScriptCompletionApi.supports("Python") && ScriptCompletionApi.complete(
                    new Request("", 0, "Java", null, null, true)).signatures().equals(List.of("Java replacement")),
                    "same-ID replacement kept old aliases/provider");
            for (String language : List.of("Python", "JavaScript ES2024", "Java")) {
                ScriptCompletionApi.register("test:python", new Language(Set.of(language), false, false, ""), (r, d) -> {
                    throw new IllegalStateException("expected provider failure");
                });
                Result failed = ScriptCompletionApi.complete(new Request("", 0, language, null, null, true));
                check(!failed.error().isEmpty() && failed.suggestions().isEmpty(), "provider failure escaped: " + language);
            }
            for (Suggestion invalid : List.of(new Suggestion("x", "x", "", -1, 0, 0),
                    new Suggestion("x", "x", "", 2, 1, 0), new Suggestion("x", "x", "", 0, 99, 0),
                    new Suggestion("x", "x", "", 0, 0, -1), new Suggestion("x", "x", "", 0, 0, 2),
                    new Suggestion("x", "\r\n", "", 0, 0, 0))) {
                ScriptCompletionApi.register("test:python", python, (r, d) -> new Result(List.of(invalid), List.of()));
                Result failed = ScriptCompletionApi.complete(new Request("x", 1, "Python", null, null, true));
                check(!failed.error().isEmpty() && failed.suggestions().isEmpty(), "invalid edit was accepted: " + invalid);
            }
            ScriptCompletionApi.register("test:python", python, (r, d) -> null);
            check(!ScriptCompletionApi.complete(new Request("", 0, "Python", null, null, true)).error().isEmpty(),
                    "null provider result escaped");
        } finally { ScriptCompletionApi.unregister("test:python"); }

        Language modern = new Language(Set.of("JavaScript", "JavaScript ES2024"), true, false, "example.help");
        ScriptCompletionApi.register("test:modern", modern, (r, d) -> d);
        try {
            check(!ScriptCompletionApi.usesBuiltinChecks("JavaScript") && ScriptCompletionApi.usesBuiltinChecks("ECMAScript"),
                    "ES5 check override leaked to another label");
            text = "function interact(e){ e.npc.getN";
            check(!ScriptCompletionApi.complete(new Request(text, text.length(), "JavaScript", "NpcEvent", null, true))
                    .suggestions().isEmpty(), "opt-in ES5 metadata/defaults lost");
            ScriptContainer owner = new ScriptContainer(null);
            String source = "const f = (e) => e?.npc;";
            owner.script = source;
            check(ScriptDiagnostics.checkStaticAll(owner, source, "JavaScript", Set.of()).isEmpty(),
                    "ES5 analysis ran on overridden modern JavaScript");
            ScriptCheckApi.register("test:modern", (r, reporter) -> reporter.report(
                    ScriptCheckApi.Severity.INFO, 0, 1, "test.modern", "addon parser"));
            try {
                var findings = ScriptDiagnostics.checkStaticAll(owner, source, "JavaScript ES2024", Set.of());
                check(findings.size() == 1 && findings.get(0).subject.equals("addon parser"), "addon checker bypassed by profile");
                check(ScriptDiagnostics.get(owner) == null && owner.console.isEmpty() && owner.script.equals(source),
                        "static/completion work mutated runtime state");
            } finally { ScriptCheckApi.unregister("test:modern"); }
        } finally { ScriptCompletionApi.unregister("test:modern"); }
        check(ScriptCompletionApi.usesBuiltinChecks("JavaScript") && ScriptCompletionApi.registeredIds().isEmpty(),
                "unregister did not restore native defaults");

        var metadata = ScriptCompletionApi.members(Probe.class);
        check(metadata.stream().anyMatch(m -> m.name().equals("getValue") && m.method()
                        && m.typeName().equals("java.lang.String"))
                && metadata.stream().anyMatch(m -> m.name().equals("value") && !m.method())
                && metadata.stream().anyMatch(m -> m.name().equals("inherited")), "reflection metadata incomplete");
        check(ScriptCompletionApi.members(noppes.npcs.api.entity.IPlayer.class).stream().anyMatch(m -> m.name().equals("getName")),
                "CNPC wrapper metadata missing on blocked-client loader");
        Request normalized = new Request(null, 99, null, null, null, false);
        check(normalized.text().isEmpty() && normalized.cursor() == 0 && normalized.context().equals("NpcEvent"), "request boundary normalization failed");

        example.addon.AddonScriptCompletionExample.init();
        try {
            for (String language : List.of("Python", "GraalPy", "JavaScript ES2024", "ECMAScript 2024", "Java")) {
                text = "e.npc.sa";
                Result demo = ScriptCompletionApi.complete(new Request(text, text.length(), language, "NpcEvent", null, true));
                check(demo.error().isEmpty() && demo.suggestions().size() == 1
                                && demo.suggestions().get(0).insertText().equals("say(\"你好\")"),
                        "copyable addon example failed: " + language);
            }
        } finally { example.addon.AddonScriptCompletionExample.shutdown(); }
        check(ScriptCompletionApi.registeredIds().isEmpty(), "example leaked registration");
    }

    public static class Parent {
        public void inherited() { throw new AssertionError("metadata invoked a method"); }
    }
    public static final class Probe extends Parent {
        static final int INITIALIZER = fail();
        private static int fail() { throw new AssertionError("metadata initialized a class"); }
        public String getValue() { throw new AssertionError("metadata invoked a getter"); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
