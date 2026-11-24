package bin.cnpcplus;

import bin.cnpcplus.script.tools.*;
import bin.cnpcplus.script.ScriptItemTimers;
import bin.cnpcplus.script.ScriptStaticCheck;
import java.util.*;
import javax.script.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScriptToolsTest {
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
}
