package bin.cnpcplus;

import bin.cnpcplus.script.tools.*;
import bin.cnpcplus.script.ScriptItemTimers;
import bin.cnpcplus.script.ScriptStaticCheck;
import bin.cnpcplus.api.script.ScriptCheckApi;
import bin.cnpcplus.api.script.ScriptCompletionApi;
import bin.cnpcplus.api.script.LanguageDescriptor;
import bin.cnpcplus.api.script.ScriptHostApi;
import java.util.*;
import javax.script.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScriptToolsTest {
    @Test public void addonRuntimeRegistrationIsLazyAndNamespaced() {
        java.util.concurrent.atomic.AtomicInteger created = new java.util.concurrent.atomic.AtomicInteger();
        LanguageDescriptor descriptor = new LanguageDescriptor("contracttest:lazy", "Lazy Test", "",
                Collections.singleton("lazy-test"), Arrays.asList(".lt", ".lang.test"), "1", "", "");
        ScriptHostApi.registerLanguage(descriptor, context -> {
            created.incrementAndGet();
            return new bin.cnpcplus.api.script.ScriptRuntime() {
                public void load(bin.cnpcplus.api.script.SourceBundle source, java.util.Map<String, Object> globals) { }
                public boolean invoke(bin.cnpcplus.api.script.InvocationContext invocation) { return false; }
                public void close() { }
            };
        });
        ScriptCompletionApi.register("contracttest:lazy-profile", new ScriptCompletionApi.Language(
                Collections.singleton("lazy-test"), false, false, ""), (request, defaults) -> defaults);
        try {
            assertEquals(0, created.get());
            assertEquals("contracttest:lazy", ScriptHostApi.runtimeLanguageId("LAZY-TEST").get());
            assertTrue(ScriptHostApi.isExplicitLanguageId("missing:language"));
            assertEquals(descriptor, ScriptHostApi.language("contracttest:lazy").get());
            assertTrue(ScriptHostApi.languages().stream().anyMatch(language -> language.id().equals("contracttest:lazy")));
            assertTrue(ScriptCompletionApi.supports("contracttest:lazy"));
            assertTrue(ScriptHostApi.hasCapability("language-runtime"));
            assertTrue(ScriptHostApi.hasCapability("async-check"));
            assertFalse(ScriptHostApi.currentInvocation().isPresent());
        } finally {
            ScriptCompletionApi.unregister("contracttest:lazy-profile");
            ScriptHostApi.unregisterLanguage("contracttest:lazy");
        }
    }

    private final ScriptMath math = new ScriptMath();
    private final ScriptText text = new ScriptText();
    @Test public void normalizationHandlesZeroAndExtremeValues() {
        assertTrue(math.position(0, 0, 0).normalize().isZero());
        assertFalse(math.position(0, 0, 0).tryNormalize().isSuccess());
        assertFalse(math.position(Double.NaN, 1, 1).tryNormalize().isSuccess());
        assertFalse(math.position(Double.POSITIVE_INFINITY, 1, 1).tryNormalize().isSuccess());
        assertEquals(1, math.position(Double.MAX_VALUE, Double.MAX_VALUE, 0).normalize().length(), 1e-12);
        assertEquals(1, math.position(Double.MIN_VALUE, 0, 0).normalize().length(), 1e-12);
    }
    @Test public void anglesAndGeometryIncludeBoundaries() {
        assertEquals(-180, math.wrapDegrees(540), 0);
        assertEquals(359, math.normalizeDegrees(-1), 0);
        assertEquals(90, math.position(1, 0, 0).angleTo(math.position(0, 1, 0)), 1e-9);
        assertEquals(1, math.position(1, 0, 0).rotate(math.position(0, 1, 0), 90).distance(math.position(0, 0, 0)), 1e-9);
        ScriptMath.Box box = math.box(math.position(0, 0, 0), math.position(1, 1, 1));
        assertTrue(math.containsPoint(box, math.position(1, 1, 1)));
        assertTrue(math.intersects(box, math.box(math.position(1, 1, 1), math.position(2, 2, 2))));
        assertEquals(1, math.intersectRayAABB(math.ray(math.position(-1, .5, .5), math.position(4, 0, 0)), box).getValue(), 0);
        assertEquals(0, math.intersectRayAABB(math.ray(math.position(.5, .5, .5), math.position(1, 0, 0)), box).getValue(), 0);
        assertFalse(math.intersectRayAABB(math.ray(math.position(-1, 2, .5), math.position(1, 0, 0)), box).isSuccess());
        assertEquals(math.position(2, 0, 0), math.closestPointOnSegment(math.segment(math.position(2, 0, 0), math.position(2, 0, 0)), math.position(9, 0, 0)));
    }
    @Test public void unicodeTruncationAndTemplates() {
        String astral = "A\uD83D\uDE00B";
        assertEquals("A", text.truncate(astral, 2));
        assertEquals(3, text.lengthCodePoints(astral));
        assertEquals("A\uD83D\uDE00", text.truncateCodePoints(astral, 2));
        assertEquals(2, text.lengthGraphemes("e\u0301x"));
        assertEquals("e\u0301", text.truncateGraphemes("e\u0301x", 1));
        assertEquals(Arrays.asList("e\u0301", "x"), text.wrap("e\u0301x", 1));
        assertEquals("red", text.stripCodes("§cred§r"));
        assertEquals("$\\x {missing}", text.render(text.compile("{name} {missing}"), Collections.singletonMap("name", "$\\x")));
    }
    @Test public void layoutClampsAndSplits() {
        ScriptUI ui = new ScriptUI();
        ScriptUI.Rect r = ui.Layout.center(ui.rect(10, 20, 100, 80), ui.size(40, 20));
        assertEquals(40, r.x(), 0); assertEquals(50, r.y(), 0);
        List<ScriptUI.Rect> parts = ui.Layout.split(ui.rect(0, 0, 100, 20), "HORIZONTAL", .25, 4);
        assertEquals(24, parts.get(0).width(), 0); assertEquals(28, parts.get(1).x(), 0);
        assertEquals(100, parts.get(1).x() + parts.get(1).width(), 0);
    }
    @Test public void timerCallbacksMayReplaceOrCancelTimers() {
        ScriptItemTimers timers = new ScriptItemTimers(); List<Integer> fired = new ArrayList<>();
        timers.start(1, 2, true);
        timers.tick(fired::add); assertTrue(fired.isEmpty());
        timers.tick(id -> { fired.add(id); timers.forceStart(id, 1, false); });
        timers.tick(fired::add); timers.tick(fired::add);
        assertEquals(Arrays.asList(1, 1), fired); assertFalse(timers.has(1));
        timers.start(2, 1, true); timers.tick(id -> timers.clear()); assertFalse(timers.has(2));
    }
    @Test public void verificationScriptsCompileAsEs5() throws Exception {
        ScriptEngine engine = new jdk.nashorn.api.scripting.NashornScriptEngineFactory().getScriptEngine("--language=es5");
        try (java.nio.file.DirectoryStream<java.nio.file.Path> files = java.nio.file.Files.newDirectoryStream(java.nio.file.Paths.get("验证脚本-4.2.0"), "*.js")) {
            int count = 0;
            for (java.nio.file.Path file : files) {
                String source = new String(java.nio.file.Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
                assertFalse(file.toString(), source.contains(";"));
                ((Compilable)engine).compile(source); count++;
            }
            assertEquals(8, count);
        }
    }
    @Test public void registryBindingsWorkInJava8Nashorn() throws Exception {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
        assertNotNull(engine);
        Map<String, Object> bindings = new HashMap<>(); ScriptApiRegistry.bind(bindings);
        for (Map.Entry<String, Object> entry : bindings.entrySet()) engine.put(entry.getKey(), entry.getValue());
        ScriptApiRegistry.extendJavaScriptMath(engine);
        assertEquals(Boolean.TRUE, engine.eval("Math.abs(-3) === 3 && Math.wrapDegrees(540) === -180 && Math.position(0,0,0).normalize().isZero()"));
        assertEquals(Boolean.TRUE, engine.eval("Math.containsPoint(Math.sphere(Math.position(0,0,0),1),Math.position(1,0,0))"));
        assertTrue(ScriptApiRegistry.isClientOnly("UI.Measure.textWidth"));
        assertNotNull(ScriptStaticCheck.check("UI.Measure.textWidth('x')", Collections.emptySet(), true));
        assertNull(ScriptStaticCheck.check("Math.wrapDegrees(360)", Collections.emptySet(), true));
        for (ScriptApiRegistry.Entry entry : ScriptApiRegistry.entries()) for (ScriptApiRegistry.Parameter parameter : entry.parameters())
            assertFalse(entry.publicName(), parameter.name().matches("arg[0-9]+"));
    }
    @Test public void verificationScriptsHandleDefaultReporterAndRawForgeEntities() throws Exception {
        String fixtures;
        try (java.io.InputStream input = getClass().getResourceAsStream("/verification-scripts-check.js")) {
            fixtures = new String(com.google.common.io.ByteStreams.toByteArray(input), java.nio.charset.StandardCharsets.UTF_8);
        }
        String[] scripts = {"01-五类对象-tickfix-CD.js", "02-载体-timer.js", "03-NPC-数学文本布局.js", "08-NPC-事件订阅.js"};
        assertEquals(Boolean.TRUE, new jdk.nashorn.api.scripting.NashornScriptEngineFactory().getScriptEngine().eval("typeof load === 'function'"));
        for (String script : scripts) for (String owner : script.startsWith("01") ? Arrays.asList("npc", "player", "block", "item") : Collections.singletonList("npc")) {
            ScriptEngine engine = new jdk.nashorn.api.scripting.NashornScriptEngineFactory().getScriptEngine("--language=es5");
            Map<String, Object> bindings = new HashMap<>(); ScriptApiRegistry.bind(bindings);
            for (Map.Entry<String, Object> entry : bindings.entrySet()) engine.put(entry.getKey(), entry.getValue());
            ScriptApiRegistry.extendJavaScriptMath(engine);
            engine.put("testTimers", new ScriptItemTimers());
            engine.eval(fixtures);
            engine.eval(new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("验证脚本-4.2.0", script)), java.nio.charset.StandardCharsets.UTF_8));
            engine.eval("check" + script.substring(0, 2) + "('" + owner + "')");
        }
    }
    @Test public void eventSubscriptionApiIsRegisteredAndDocumented() {
        Map<String, Object> bindings = new HashMap<>(); ScriptApiRegistry.bind(bindings);
        assertTrue(bindings.get("events") instanceof ScriptEvents);
        for (String method : Arrays.asList("on", "off", "offAll", "has", "list"))
            assertTrue(method, ScriptApiRegistry.entries().stream().anyMatch(entry -> entry.publicName().equals("events." + method)));
        assertTrue(ScriptApiRegistry.entries().stream().anyMatch(entry -> entry.publicName().equals("events.Subscription.getEventClass")));
    }

    @Test public void staticCheckAggregatesFindingsAndPreservesSeverity() {
        List<bin.cnpcplus.script.ScriptDiagnostic> findings = ScriptStaticCheck.checkAll(
                "var spare = 1; while (true) { return; log('x'); } /* open", Collections.<String>emptySet(), true);
        assertTrue(findings.size() >= 4);
        assertTrue(findings.stream().anyMatch(d -> d.warningKey.equals("cnpcplus.script.possible_infinite_loop")
                && d.severity == bin.cnpcplus.script.ScriptDiagnostic.Severity.WARN));
        assertTrue(findings.stream().anyMatch(d -> d.warningKey.equals("cnpcplus.script.unreachable_code")));
        assertTrue(findings.stream().anyMatch(d -> d.warningKey.equals("cnpcplus.script.unclosed_comment")
                && d.severity == bin.cnpcplus.script.ScriptDiagnostic.Severity.ERROR));
        assertTrue(findings.stream().anyMatch(d -> d.warningKey.equals("cnpcplus.script.unused_variable")
                && d.severity == bin.cnpcplus.script.ScriptDiagnostic.Severity.INFO));
        assertFalse(ScriptStaticCheck.checkAll("var regex = /\"/; var negative = -1;", Collections.<String>emptySet(), true)
                .stream().anyMatch(d -> d.warningKey.equals("cnpcplus.script.unclosed_string")
                        || d.warningKey.equals("cnpcplus.script.invalid_expression")));
    }

    @Test public void staticCheckCoversRequestedDiagnosticKinds() {
        Set<String> keys = new HashSet<>();
        for (String source : Arrays.asList(
                "if (true] {}", "var open = (1", "var extra = 1)", "var text = 'open", "/* open", "if () {}", "var = 1", "var value = ;",
                "class Unsupported {}", "function broken return 1;", "function missingBody() return 1;", "missingApi()",
                "while (true) { log(1) }", "function unreachable() { return 1; log(2) }",
                "function unusedFunction() {}", "var unusedValue = 1;", "if (true) {} else if (true) {}",
                "if (true) {}", "function cases(value) { switch(value) { case 1: break; case 1: break; } }"))
            for (bin.cnpcplus.script.ScriptDiagnostic finding : ScriptStaticCheck.checkAll(source, Collections.<String>emptySet(), true))
                keys.add(finding.warningKey);
        for (String expected : Arrays.asList("mismatched_bracket", "unclosed_bracket", "unexpected_bracket",
                "unclosed_string", "unclosed_comment", "missing_structure", "invalid_expression",
                "invalid_declaration", "unsupported_syntax", "invalid_function",
                "undeclared", "possible_infinite_loop", "unreachable_code", "unused_function",
                "unused_variable", "duplicate_branch", "empty_block", "duplicate_case"))
            assertTrue(expected, keys.contains("cnpcplus.script." + expected));
    }

    @Test public void addonCheckersAreIsolatedAndGlobalsAreRecognized() {
        ScriptCheckApi.registerGlobal("addonApi");
        ScriptCheckApi.register("test:throws", (source, reporter) -> { throw new IllegalStateException("expected"); });
        ScriptCheckApi.register("test:reports", (source, reporter) -> reporter.report(
                ScriptCheckApi.Severity.ERROR, 0, 1, "test.finding",
                source.globals().contains("addonApi") && source.globals().contains("UI") ? source.language() : "missing globals"));
        try {
            List<bin.cnpcplus.script.ScriptDiagnostic> findings = ScriptStaticCheck.checkAll(
                    "addonApi.run();", "JavaScript", Collections.<String>emptySet(), true);
            assertTrue(findings.stream().anyMatch(d -> d.warningKey.equals("test.finding")
                    && d.token.equals("JavaScript") && d.message.contains("JavaScript")));
            assertTrue(findings.stream().anyMatch(d -> d.warningKey.equals("cnpcplus.script.addon_check_failed")));
            assertFalse(findings.stream().anyMatch(d -> d.warningKey.equals("cnpcplus.script.undeclared") && d.token.equals("addonApi")));
        } finally {
            ScriptCheckApi.unregister("test:throws");
            ScriptCheckApi.unregister("test:reports");
            ScriptCheckApi.unregisterGlobal("addonApi");
        }
    }

    @Test public void unifiedCheckUsesImmutableSnapshotsAndUtf16Ranges() throws Exception {
        ScriptCheckApi.registerLanguageChecker("contracttest:check", (source, reporter) -> {
            int offset = source.text().length() - 1;
            reporter.report(ScriptCheckApi.Severity.ERROR, offset, 1, "contracttest.last_character", "last");
            return ScriptCheckApi.Status.SUCCESS;
        });
        try {
            bin.cnpcplus.api.script.SourceBundle bundle = new bin.cnpcplus.api.script.SourceBundle(
                    new bin.cnpcplus.api.script.SourceUnit("page", "Page", "😀\r\nx"),
                    Collections.singletonList(new bin.cnpcplus.api.script.SourceUnit("lib/example", "Library", "ab")), 12L);
            bin.cnpcplus.api.script.ScriptMetadataApi.MetadataContext metadata =
                    new bin.cnpcplus.api.script.ScriptMetadataApi.MetadataContext("npc", "contracttest:check", Collections.<String>emptySet());
            ScriptCheckApi.register("contracttest:late-checker", (source, reporter) ->
                    reporter.report(ScriptCheckApi.Severity.ERROR, 0, source.text().length() + 10, "contracttest:late", "late"));
            ScriptCheckApi.CheckRequest request = new ScriptCheckApi.CheckRequest(bundle, "contracttest:check", "npc", metadata, 4L);
            ScriptCheckApi.unregister("contracttest:late-checker");
            ScriptCheckApi.CheckResult result = ScriptCheckApi.checkAsync(request).get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(ScriptCheckApi.Status.SUCCESS, result.status());
            assertEquals(4L, result.requestRevision());
            assertEquals(4, result.findings().size()); // language and addon checker each inspect main and linked sources
            assertEquals(request.registryRevision(), result.registryRevision());
            assertTrue(ScriptCheckApi.registryRevision() != result.registryRevision());
            ScriptCheckApi.register("contracttest:clamp", (source, reporter) ->
                    reporter.report(ScriptCheckApi.Severity.ERROR, 0, source.text().length() + 10, "contracttest:clamp", "clamp"));
            try {
                List<ScriptCheckApi.Finding> clamped = ScriptCheckApi.check(
                        new ScriptCheckApi.Source("abc", "tiny", Collections.<String>emptySet()));
                assertEquals(3, clamped.get(0).length());
            } finally { ScriptCheckApi.unregister("contracttest:clamp"); }
            ScriptCheckApi.Finding main = result.findings().get(0);
            assertEquals("page", main.sourceId());
            assertEquals(12L, main.revision());
            assertEquals(3, main.offset()); // astral character is two UTF-16 code units, then LF, then x
            assertEquals(1, main.length());
            assertEquals(bin.cnpcplus.api.script.ScriptDiagnosticApi.sourceHash("😀\r\nx"), main.sourceHash());
            assertEquals(12L, bundle.revision());
            try { result.findings().clear(); fail("check findings must be immutable"); }
            catch (UnsupportedOperationException expected) { }
            bin.cnpcplus.script.ScriptDiagnostic unknown = bin.cnpcplus.script.ScriptDiagnostic.at(
                    "abc", -1, 4, bin.cnpcplus.script.ScriptDiagnostic.Severity.ERROR, "test.unknown", "");
            assertEquals(-1, unknown.offset);
            assertEquals(0, unknown.line);
            assertEquals(0, unknown.column);
            assertEquals(0, unknown.length);
        } finally {
            ScriptCheckApi.unregister("contracttest:late-checker");
            ScriptCheckApi.unregisterLanguageChecker("contracttest:check");
        }
    }

    @Test public void genericAddonCheckersDoNotClaimLanguageSupportAndStatusesMerge() {
        ScriptCheckApi.register("contracttest:throwing", (source, reporter) -> { throw new IllegalStateException("expected"); });
        ScriptCheckApi.registerLanguageChecker("contracttest:status", (source, reporter) -> ScriptCheckApi.Status.DEPENDENCY_MISSING);
        try {
            bin.cnpcplus.api.script.SourceBundle bundle = new bin.cnpcplus.api.script.SourceBundle(
                    new bin.cnpcplus.api.script.SourceUnit("page", "Page", "x"), Collections.<bin.cnpcplus.api.script.SourceUnit>emptyList(), 0L);
            bin.cnpcplus.api.script.ScriptMetadataApi.MetadataContext metadata =
                    new bin.cnpcplus.api.script.ScriptMetadataApi.MetadataContext("npc", "contracttest:unsupported", Collections.<String>emptySet());
            ScriptCheckApi.CheckResult unsupported = ScriptCheckApi.check(new ScriptCheckApi.CheckRequest(
                    bundle, "contracttest:unsupported", "npc", metadata, 0L));
            assertEquals(ScriptCheckApi.Status.UNSUPPORTED, unsupported.status());
            metadata = new bin.cnpcplus.api.script.ScriptMetadataApi.MetadataContext("npc", "contracttest:status", Collections.<String>emptySet());
            ScriptCheckApi.CheckResult failed = ScriptCheckApi.check(new ScriptCheckApi.CheckRequest(
                    bundle, "contracttest:status", "npc", metadata, 0L));
            assertEquals(ScriptCheckApi.Status.FAILED, failed.status());
        } finally {
            ScriptCheckApi.unregister("contracttest:throwing");
            ScriptCheckApi.unregisterLanguageChecker("contracttest:status");
        }
    }

    @Test public void structuredRuntimeDiagnosticsAreBoundedImmutableAndPreserveUnknownLocations() {
        List<String> arguments = new ArrayList<>(Arrays.asList("one", "two"));
        bin.cnpcplus.api.script.ScriptDiagnosticApi.Diagnostic unknown =
                new bin.cnpcplus.api.script.ScriptDiagnosticApi.Diagnostic("lib/module.py", 7L, "hash",
                        bin.cnpcplus.api.script.ScriptDiagnosticApi.Severity.ERROR, "contracttest:syntax",
                        "message", "contracttest.syntax", arguments, -1, 20, 4, 9, new IllegalStateException("local only"));
        arguments.add("mutated");
        assertEquals(Arrays.asList("one", "two"), unknown.arguments());
        assertEquals(-1, unknown.offset()); assertEquals(0, unknown.length());
        assertEquals(0, unknown.line()); assertEquals(0, unknown.column());
        try { unknown.arguments().add("forbidden"); fail("diagnostic arguments must be immutable"); }
        catch (UnsupportedOperationException expected) { }

        String astral = "A\uD83D\uDE00\nB";
        bin.cnpcplus.api.script.ScriptDiagnosticApi.Diagnostic located =
                new bin.cnpcplus.api.script.ScriptDiagnosticApi.Diagnostic("page", 8L,
                        bin.cnpcplus.api.script.ScriptDiagnosticApi.sourceHash(astral),
                        bin.cnpcplus.api.script.ScriptDiagnosticApi.Severity.WARN, "contracttest:range",
                        repeat('x', 2100), repeat('k', 300), Collections.singletonList(repeat('a', 300)),
                        3, 1, 2, 1, null);
        assertEquals(3, located.offset()); // Java UTF-16 code units: A + surrogate pair + LF
        assertEquals(1024, located.message().length());
        assertEquals(128, located.translationKey().length());
        assertEquals(128, located.arguments().get(0).length());
        try {
            new bin.cnpcplus.api.script.ScriptDiagnosticApi.Diagnostic("page", 8L, "hash",
                    bin.cnpcplus.api.script.ScriptDiagnosticApi.Severity.ERROR, "contracttest:invalid",
                    "bad location", "", Collections.<String>emptyList(), 0, 1, 0, 0, null);
            fail("located diagnostics require 1-based line and column");
        } catch (IllegalArgumentException expected) { }
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count]; Arrays.fill(chars, value); return new String(chars);
    }

    @Test public void completionProfilesRouteImmutableRequestsAndProviderRanges() {
        List<String> hooks = new ArrayList<>(Collections.singletonList("init"));
        ScriptCompletionApi.Request request = new ScriptCompletionApi.Request(
                "x = pri", 7, " Python ", "NpcEvent", hooks, true);
        hooks.clear();
        assertEquals(Collections.singletonList("init"), request.hooks());
        try { request.hooks().add("tick"); fail("request hooks must be immutable"); }
        catch (UnsupportedOperationException expected) { }
        assertEquals(0, new ScriptCompletionApi.Request("", 20, "", "", null, false).cursor());
        assertTrue(ScriptCompletionApi.usesBuiltinChecks(""));

        List<ScriptCompletionApi.Member> members = ScriptCompletionApi.members(noppes.npcs.api.entity.IEntity.class);
        assertTrue(members.stream().anyMatch(member -> member.name().equals("getName")));
        try { members.clear(); fail("CNPC member metadata must be immutable"); }
        catch (UnsupportedOperationException expected) { }

        ScriptCompletionApi.register("test:python", new ScriptCompletionApi.Language(
                new HashSet<>(Arrays.asList("python", "py")), false, false, "Python provider"), (input, defaults) -> {
            assertTrue(defaults.suggestions().isEmpty());
            return new ScriptCompletionApi.Result(Collections.singletonList(
                    new ScriptCompletionApi.Suggestion("print", "print()", "built-in print", 4, 7, 6)),
                    Collections.singletonList("print(*values)"));
        });
        try {
            assertTrue(ScriptCompletionApi.supports("PY"));
            assertFalse(ScriptCompletionApi.usesBuiltinChecks("python"));
            assertTrue(ScriptStaticCheck.checkAll("var = 1", "py", Collections.<String>emptySet(), true).isEmpty());
            ScriptCompletionApi.Result result = ScriptCompletionApi.complete(request);
            assertEquals(1, result.suggestions().size());
            assertEquals(4, result.suggestions().get(0).start());
            assertEquals(7, result.suggestions().get(0).end());
            assertEquals(6, result.suggestions().get(0).caret());
            assertEquals(Collections.singletonList("print(*values)"), result.signatures());
            try { result.suggestions().clear(); fail("result suggestions must be immutable"); }
            catch (UnsupportedOperationException expected) { }
        } finally { ScriptCompletionApi.unregister("test:python"); }
    }

    @Test public void completionProviderFailuresFallBackWithoutEscaping() {
        ScriptCompletionApi.register("test:broken", new ScriptCompletionApi.Language(
                Collections.singleton("brokenlang"), false, false, ""), (request, defaults) -> {
            throw new IllegalStateException("provider failure");
        });
        try {
            ScriptCompletionApi.Result result = ScriptCompletionApi.complete(new ScriptCompletionApi.Request(
                    "", 0, "brokenlang", "NpcEvent", Collections.<String>emptyList(), true));
            assertTrue(result.suggestions().isEmpty());
            assertTrue(result.error().contains("IllegalStateException"));
        } finally { ScriptCompletionApi.unregister("test:broken"); }
    }
}
