package top.cnpcplus;

import java.util.List;
import java.util.Set;
import javax.script.ScriptException;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.constants.EnumScriptType;
import top.cnpcplus.script.ScriptApiCompletion;
import top.cnpcplus.script.ScriptDiagnostic;
import top.cnpcplus.script.ScriptDiagnostics;
import top.cnpcplus.script.ScriptStaticCheck;

/** Headless checks for completion inference and persisted diagnostics; not a Mixin/game smoke test. */
public final class ScriptEditorRegression {
    public static void main(String[] args) {
        completion();
        hooks();
        diagnostics();
        staticChecks();
        System.out.println("SCRIPT_EDITOR_REGRESSION=PASS");
    }

    private static void completion() {
        String code = "function interact(e) { e.pl";
        ScriptApiCompletion.Result event = ScriptApiCompletion.complete(code, code.length(), "NpcEvent");
        check(event.entries.stream().anyMatch(entry -> entry.name.equals("player")),
                "interact event did not infer NpcEvent.InteractEvent");

        code = "function interact(e) { var p = e.player; p.getN";
        ScriptApiCompletion.Result local = ScriptApiCompletion.complete(code, code.length(), "NpcEvent");
        check(local.entries.stream().anyMatch(entry -> entry.name.equals("getName")),
                "local CNPC API chain did not infer IPlayer");

        code = "function interact(e) { e.player.setPosition(";
        List<ScriptApiCompletion.Entry> hints = ScriptApiCompletion.hint(code, code.length(), "NpcEvent");
        check(hints.stream().anyMatch(entry -> entry.signature.startsWith("setPosition(")),
                "method signature hint missing");

        code = "EntitiesT";
        ScriptApiCompletion.Result constants = ScriptApiCompletion.complete(code, code.length(), "NpcEvent");
        check(constants.entries.stream().anyMatch(entry -> entry.name.equals("EntitiesType")),
                "1.20.1 EntitiesType root missing");

        code = "// e.player.getN";
        check(ScriptApiCompletion.complete(code, code.length(), "NpcEvent").entries.isEmpty(),
                "completion appeared inside a comment");
        code = "'e.player.getN'";
        check(ScriptApiCompletion.complete(code, code.length() - 1, "NpcEvent").entries.isEmpty(),
                "completion appeared inside a string");
        System.out.println("PASS: event/local API inference, constants, filtering and signature hints");
    }

    private static void diagnostics() {
        String script = "function init(e) {\n  var x = 1;\n  broken(;\n}";
        ScriptException failure = new ScriptException("Expected expression", "<eval>", 3, 10);
        ScriptDiagnostic diagnostic = ScriptDiagnostic.capture(failure, script);
        check(diagnostic.line == 3 && diagnostic.column == 10, "ScriptException location lost");
        check(diagnostic.matches(script), "matching script hash rejected");
        check(!diagnostic.matches(script + "\n"), "changed script hash accepted");
        int[] range = ScriptDiagnostic.range(script, 3);
        check(script.substring(range[0], range[1]).equals("  broken(;"), "source line range incorrect");
        check(ScriptDiagnostic.lineFromLog("at <eval>:17:2") == 17, "stack-text location fallback failed");

        CompoundTag nbt = new CompoundTag();
        ScriptDiagnostics.write(diagnostic, script, nbt);
        check(nbt.contains("CNPCPlusDiagnostic"), "diagnostic NBT was not written");

        ScriptDiagnostic roundTrip = ScriptDiagnostics.read(nbt, script);
        check(roundTrip != null && roundTrip.line == 3 && roundTrip.matches(script),
                "diagnostic NBT round trip failed");

        check(ScriptDiagnostics.read(nbt, script + " ") == null,
                "stale diagnostic survived hash validation");
        System.out.println("PASS: error location, SHA-256 guard and ScriptContainer NBT round trip");
    }

    private static void hooks() {
        var npcHooks = java.util.Arrays.stream(EnumScriptType.npcScripts).map(t -> t.function).toList();
        var itemHooks = java.util.Arrays.stream(EnumScriptType.itemScripts).map(t -> t.function).toList();
        var playerHooks = java.util.Arrays.stream(EnumScriptType.playerScripts).map(t -> t.function).toList();
        var blockHooks = java.util.Arrays.stream(EnumScriptType.blockScripts).map(t -> t.function).toList();
        check(npcHooks.containsAll(List.of("timer", "damaged")), "native NPC hook baseline missing");
        check(!itemHooks.contains("timer"), "unexpected native item timer baseline");
        for (var context : java.util.Map.of("NpcEvent", npcHooks, "PlayerEvent", playerHooks,
                "BlockEvent", blockHooks, "ItemEvent", itemHooks).entrySet()) {
            var names = ScriptApiCompletion.complete("function ", 9, context.getKey(), context.getValue()).entries;
            check(context.getValue().stream().allMatch(h -> names.stream().anyMatch(e -> e.name.equals(h))),
                    "native hook list incompletely exposed: " + context.getKey());
        }
        String code = "function ti";
        check(ScriptApiCompletion.complete(code, code.length(), "ItemEvent", List.of("init", "tick"))
                .entries.stream().noneMatch(e -> e.name.equals("timer")), "NPC timer leaked into vanilla item");
        var completed = ScriptApiCompletion.complete(code, code.length(), "ItemEvent", List.of("init", "tick", "timer"));
        check(completed.entries.stream().anyMatch(e -> e.name.equals("timer") && e.insert.equals("timer")),
                "server addon item timer or function-name insertion lost");
        code = "function addonH";
        check(ScriptApiCompletion.complete(code, code.length(), "ForgeEvent", List.of("addonHook"))
                .entries.stream().anyMatch(e -> e.name.equals("addonHook")), "non-enum addon hook ignored");
        code = "function myHelper(x) { return x; }\nmyH";
        check(ScriptApiCompletion.complete(code, code.length(), "NpcEvent", List.of("timer"))
                .entries.stream().anyMatch(e -> e.name.equals("myHelper")), "custom function hidden by hook list");
        code = "function timer(event) {}\nfunction ti";
        check(ScriptApiCompletion.complete(code, code.length(), "NpcEvent", List.of("timer", "timer", "bad-name"))
                .entries.stream().filter(e -> e.name.equals("timer")).count() == 1, "duplicate hook/function entries");
        for (String hidden : List.of("// function ti", "'function ti", "/function ti", "`function ti")) {
            check(ScriptApiCompletion.complete(hidden, hidden.length(), "ItemEvent", List.of("timer")).entries.isEmpty(),
                    "hook completion escaped literal/comment: " + hidden);
        }
        System.out.println("PASS: server Methods hook names, item timer registration, context isolation and custom functions");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void staticChecks() {
        String script = "function damaged(e){\n哈哈哈\n}";
        ScriptDiagnostic diagnostic = ScriptStaticCheck.check(script, Set.of());
        check(diagnostic != null && diagnostic.line == 2 && diagnostic.column == 1
                && diagnostic.matches(script), "user's Chinese identifier was not diagnosed");
        check(ScriptStaticCheck.check("function damaged(e){ 哈哈哈 }", Set.of()) != null,
                "same-line identifier was incorrectly treated as a declaration");
        check(ScriptStaticCheck.check("function damaged(e){ known; 哈哈哈; }", Set.of("known")) != null,
                "adjacent identifier statement was skipped");
        for (String valid : List.of(
                "function damaged(哈哈哈){\n哈哈哈\n}",
                "function damaged(e){ var 哈哈哈 = 1;\n哈哈哈\n}",
                "var a = 1, 哈哈哈;\nfunction damaged(e){\n哈哈哈\n}",
                "var source = {}; var {哈哈哈} = source;\nfunction damaged(e){\n哈哈哈\n}",
                "var f = (哈哈哈) => {\n哈哈哈\n};",
                "function damaged(e){\n'哈哈哈'; // 哈哈哈\n/* 哈哈哈 */\n}",
                "function damaged(e){ if (e) { return; }\nelse\n{ debugger; } }",
                "function damaged(e){\nMath;\nAnimationType\n}",
                "function damaged(e){ load('globals.js');\n哈哈哈\n}",
                "function damaged(e){ var regex = /{哈哈哈}/;\nregex\n}")) {
            check(ScriptStaticCheck.check(valid, Set.of()) == null, "false static alarm: " + valid);
        }
        CompoundTag saved = new CompoundTag();
        ScriptDiagnostics.write(diagnostic, script, saved);
        ScriptDiagnostic reopened = ScriptDiagnostics.read(saved, script);
        check(reopened == null && !saved.contains("CNPCPlusDiagnostic"),
                "manual suspicion leaked into persisted runtime diagnostics");
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("Line", diagnostic.line);
        legacy.putInt("Column", diagnostic.column);
        legacy.putString("Hash", diagnostic.hash);
        legacy.putString("Message", diagnostic.message);
        saved.put("CNPCPlusDiagnostic", legacy);
        check(ScriptDiagnostics.read(saved, script) == null, "legacy static error restored as runtime error");
        check(ScriptDiagnostics.read(saved, script + " ") == null, "stale static highlight survived editing");
        System.out.println("PASS: non-executing Unicode suspicion, false-alarm guards and runtime/NBT isolation");
        for (String invalid : List.of(
                "function damaged(e) { e.npc.say(missing); }",
                "function damaged(e) { var value = missing + 1; }",
                "function damaged(e) { missing = 1; }",
                "function damaged(e) { missing++; }",
                "function damaged(e) { missing.call(); }",
                "function damaged(e) { var good = 4 / missing; }",
                "function damaged(e) { var good = {} / missing; }",
                "function damaged(e) { var good = e.return / missing; }",
                "function damaged(e) { return `${missing}`; }",
                "function damaged(e) { return {missing}; }",
                "function damaged(e) { return e ? missing : 0; }",
                "function first(e) { var privateName = 1; } function second(e) { missing(privateName); }",
                "function first(e) { var privateName = 1; } function second(e) { return privateName; }",
                "function damaged(e) { { let inner = 1; } return inner; }",
                "function damaged(e) { for(let inner=0; inner<3; inner++) {} return inner; }",
                "var fun = function privateName(e) {return e}; privateName();",
                "var {key: value} = {}; key;")) {
            check(ScriptStaticCheck.check(invalid, Set.of()) != null, "missed reference: " + invalid);
        }
        for (String valid : List.of(
                "function damaged(e) { e.npc.say(e.damage); var n = 4 / 2; return n; }",
                "function damaged(e) { var n=1, m=n+1; return m; }",
                "function damaged(e) { var n = /[{}()\\[\\]]+/g; return n.test('}'); }",
                "function damaged(e) { if(e) /[)]/.test('a'); }",
                "function damaged(e) { return `${e.npc.name} ${({key: e}).key}`; }",
                "function damaged(e) { var good = {} / 2; return good; }",
                "function damaged(e) { return `literal } ) [ ${`nested ${e.damage}`}`; }",
                "function damaged(e) { var source={}; var {key: value=1, ...rest}=source; return value+rest.key; }",
                "function damaged(e) { var source=[1,2]; var [x,y]=source; return x+y; }",
                "function damaged(e) { try {} catch(err) { log(err); } }",
                "function damaged(e) { var f = x => x+1; return f(2); }",
                "function damaged(e) { var f = (x,y=x) => y+1; return f(2); }",
                "function damaged(e, x=e.damage) { return x; }",
                "function damaged(e) { for(let i=0; i<3; i++) { log(i); } }",
                "function damaged(e) { for(const x of [1,2]) { log(x); } }",
                "function damaged(e) { { var visible=1; } return visible; }",
                "function damaged(e) { var obj={key:1, method(x) { return x; }}; return obj.key; }",
                "function damaged(e) { outer: for(var i=0; i<3;i++) { break outer; } }",
                "var fun = function privateName(e) {return privateName(e)}; fun(1);",
                "function damaged(e) { var key='x'; var {[key]: value}={}; return value; }")) {
            check(ScriptStaticCheck.check(valid, Set.of()) == null, "false reference/bracket alarm: " + valid);
        }
        for (String broken : List.of("function damaged(e) {\n e.npc.say('x');",
                "function damaged(e) { e.npc.say(1; }", "var x = [1,2;", "var x = (1];", "}")) {
            var warning = ScriptStaticCheck.check(broken, Set.of());
            check(warning != null && warning.hintKey.contains("bracket") && warning.length == 1,
                    "missing localized bracket diagnostic: " + broken);
        }
        check(ScriptStaticCheck.check("load('file.js'); missing;", Set.of()) == null, "dynamic binding accused");
        check(ScriptStaticCheck.check("load('file.js'); function f(e) {", Set.of()) != null,
                "dynamic bindings incorrectly disabled bracket checks");
        check(ScriptStaticCheck.check("missing;", Set.of(), false) == null, "external bindings accused");
        check(ScriptStaticCheck.check("function f(e) {", Set.of(), false) != null, "external bracket check skipped");
        System.out.println("PASS: expression/assignment references, lexical scope, bracket pairing, regex/templates and dynamic guards");
    }
}
