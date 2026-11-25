package top.cnpcplus;

import java.util.List;
import java.util.Set;
import javax.script.ScriptException;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.constants.EnumScriptType;
import top.cnpcplus.api.script.ScriptCheckApi;
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
                "function damaged(e){\n'哈哈哈'; // 哈哈哈\n/* 哈哈哈 */\n}",
                "function damaged(e){ if (e) { return; }\nelse\n{ debugger; } }",
                "function damaged(e){\nMath;\nAnimationType\n}",
                "function damaged(e){ load('globals.js');\n哈哈哈\n}",
                "function damaged(e){ var regex = /{哈哈哈}/;\nregex\n}")) {
            var unexpected = ScriptStaticCheck.checkAll(valid, Set.of(), true, Set.of());
            check(unexpected.stream().noneMatch(item -> item.severity != ScriptDiagnostic.Severity.INFO),
                    "false static alarm: " + valid + " -> " + unexpected.stream()
                            .map(item -> item.severity + "/" + item.hintKey + "/" + item.subject).toList());
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
                "function damaged(e) { var good = {} / 2; return good; }",
                "function damaged(e) { try { log('ok'); } catch(err) { log(err); } }",
                "function damaged(e) { for(var i=0; i<3; i++) { log(i); } }",
                "function damaged(e) { { var visible=1; } return visible; }",
                "function damaged(e) { var obj={key:1}; return obj.key; }",
                "function damaged(e) { outer: for(var i=0; i<3;i++) { break outer; } }",
                "var fun = function privateName(e) {return privateName(e)}; fun(1);",
                "function damaged(e) { var key='x'; var value={}; return value[key]; }")) {
            var unexpected = ScriptStaticCheck.checkAll(valid, Set.of(), true, Set.of());
            check(unexpected.stream().noneMatch(item -> item.severity != ScriptDiagnostic.Severity.INFO),
                    "false reference/bracket alarm: " + valid + " -> " + unexpected.stream()
                            .map(item -> item.severity + "/" + item.hintKey + "/" + item.subject).toList());
        }
        List<ScriptDiagnostic> unsupported = ScriptStaticCheck.checkAll(
                "function damaged(e, x=e.damage) { return x; }", Set.of(), true, Set.of());
        check(unsupported.stream().anyMatch(item -> item.hintKey.endsWith("unsupported_syntax")),
                "Nashorn-incompatible default parameter was not identified");
        unsupported = ScriptStaticCheck.checkAll("function damaged(e) { for(let i=0; i<3; i++) log(i); }",
                Set.of(), true, Set.of());
        check(unsupported.stream().anyMatch(item -> item.hintKey.endsWith("unsupported_syntax")),
                "Nashorn-incompatible let declaration was not identified");
        List<ScriptDiagnostic> distinctCases = ScriptStaticCheck.checkAll(
                "function damaged(e) { switch(e) { case 'a b': break; case 'ab': break; } }",
                Set.of(), true, Set.of());
        check(distinctCases.stream().noneMatch(item -> item.hintKey.endsWith("duplicate_case")),
                "different string switch cases were treated as duplicates");
        List<ScriptDiagnostic> distinctConditions = ScriptStaticCheck.checkAll(
                "function damaged(e) { if(e.name === 'a b') {} else if(e.name === 'ab') {} }",
                Set.of(), true, Set.of());
        check(distinctConditions.stream().noneMatch(item -> item.hintKey.endsWith("duplicate_condition")),
                "different string conditions were treated as duplicates");
        List<ScriptDiagnostic> incompleteLoop = ScriptStaticCheck.checkAll(
                "function damaged(e) { while(true) {", Set.of(), true, Set.of());
        check(incompleteLoop.stream().anyMatch(item -> item.hintKey.endsWith("unclosed_bracket")),
                "incomplete loop did not retain bracket recovery");
        try {
            for (String sample : List.of(
                    "游戏验收脚本/14-脚本简单自检/01-Error-多项词法错误.js",
                    "游戏验收脚本/14-脚本简单自检/02-Warn-可疑代码.js",
                    "游戏验收脚本/14-脚本简单自检/03-Info-改进建议.js",
                    "游戏验收脚本/14-脚本简单自检/04-Error-Nashorn不兼容语法.js",
                    "游戏验收脚本/14-脚本简单自检/05-Error-注释未闭合.js",
                    "游戏验收脚本/14-脚本简单自检/06-Error-括号错配.js",
                    "游戏验收脚本/14-脚本简单自检/07-Warn-空分支.js",
                    "游戏验收脚本/14-脚本简单自检/08-Warn-循环风险.js",
                    "游戏验收脚本/14-脚本简单自检/09-Info-多项未使用.js",
                    "游戏验收脚本/14-脚本简单自检/10-Info-throw后不可达.js")) {
                String source = java.nio.file.Files.readString(java.nio.file.Path.of(sample));
                List<ScriptDiagnostic> result = ScriptStaticCheck.checkAll(source, Set.of(), true,
                        Set.of("interact"));
                if (sample.contains("01-")) {
                    check(result.stream().anyMatch(item -> item.hintKey.endsWith("unclosed_string"))
                                    && result.stream().anyMatch(item -> item.hintKey.endsWith("unclosed_bracket")),
                            "Error acceptance sample did not report both lexical issues");
                } else if (sample.contains("02-")) {
                    check(result.stream().anyMatch(item -> item.hintKey.endsWith("undeclared"))
                                    && result.stream().anyMatch(item -> item.hintKey.endsWith("duplicate_condition"))
                                    && result.stream().anyMatch(item -> item.hintKey.endsWith("duplicate_case")),
                            "Warn acceptance sample did not report all issues");
                } else if (sample.contains("03-")) {
                    check(result.stream().anyMatch(item -> item.hintKey.endsWith("unused_function"))
                                    && result.stream().anyMatch(item -> item.hintKey.endsWith("unused_variable"))
                                    && result.stream().anyMatch(item -> item.hintKey.endsWith("unreachable"))
                                    && result.stream().noneMatch(item -> item.subject.equals("interact")
                                            && item.hintKey.endsWith("unused_function")),
                            "Info acceptance sample did not report expected suggestions");
                } else if (sample.contains("04-")) {
                    check(result.stream().anyMatch(item -> item.hintKey.endsWith("unsupported_syntax")),
                            "Nashorn compatibility sample did not report unsupported syntax");
                } else if (sample.contains("05-")) {
                    check(result.stream().anyMatch(item -> item.hintKey.endsWith("unclosed_comment")),
                            "unclosed comment sample was not reported");
                } else if (sample.contains("06-")) {
                    check(result.stream().anyMatch(item -> item.hintKey.endsWith("unclosed_bracket")
                                    || item.hintKey.endsWith("unexpected_bracket")),
                            "mismatched bracket sample was not reported");
                } else if (sample.contains("07-")) {
                    check(result.stream().anyMatch(item -> item.hintKey.endsWith("empty_block")),
                            "empty block sample was not reported");
                } else if (sample.contains("08-")) {
                    check(result.stream().anyMatch(item -> item.hintKey.endsWith("possible_infinite_loop")),
                            "non-terminating loop sample was not reported");
                } else if (sample.contains("09-")) {
                    check(result.stream().filter(item -> item.hintKey.endsWith("unused_function")).count() >= 2
                                    && result.stream().filter(item -> item.hintKey.endsWith("unused_variable")).count() >= 2,
                            "multiple unused declarations were not all reported");
                } else {
                    check(result.stream().anyMatch(item -> item.hintKey.endsWith("unreachable")),
                            "throw/unreachable sample was not reported");
                }
            }
        } catch (java.io.IOException error) {
            throw new AssertionError("could not read game acceptance samples", error);
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

        List<ScriptDiagnostic> multiple = ScriptStaticCheck.checkAll(
                "function damaged(e) { var unused = 1; while (true) {} return; after(); }", Set.of(), true, Set.of());
        check(multiple.stream().anyMatch(item -> item.hintKey.endsWith("possible_infinite_loop")
                        && item.severity == ScriptDiagnostic.Severity.WARN), "possible infinite loop was not reported");
        check(multiple.stream().anyMatch(item -> item.hintKey.endsWith("unused_variable")
                        && item.severity == ScriptDiagnostic.Severity.INFO), "unused variable suggestion was not reported");
        check(multiple.stream().anyMatch(item -> item.hintKey.endsWith("unreachable")
                        && item.severity == ScriptDiagnostic.Severity.INFO), "code after return was not reported");

        List<ScriptDiagnostic> broken = ScriptStaticCheck.checkAll("function damaged(e) { e.npc.say('open;\n}", Set.of(), true, Set.of());
        check(broken.stream().anyMatch(item -> item.hintKey.endsWith("unclosed_string"))
                        && broken.stream().anyMatch(item -> item.hintKey.endsWith("unclosed_bracket")),
                "multiple recoverable lexical errors were not retained");

        ScriptCheckApi.registerGlobal("addonGlobal");
        try {
            check(ScriptStaticCheck.checkAll("addonGlobal;", ScriptCheckApi.registeredGlobals(), true, Set.of()).stream()
                    .noneMatch(item -> item.hintKey.endsWith("undeclared")), "registered addon global was diagnosed");
        } finally {
            ScriptCheckApi.unregisterGlobal("addonGlobal");
        }
        ScriptCheckApi.register("test:language", (source, report) -> {
            if (source.language().equals("ExampleLanguage"))
                report.report(ScriptCheckApi.Severity.ERROR, 0, 1, "test.example.error", "custom grammar");
        });
        try {
            var addon = ScriptCheckApi.check(new ScriptCheckApi.Source("x", "ExampleLanguage", Set.of()));
            check(addon.size() == 1 && addon.get(0).severity() == ScriptCheckApi.Severity.ERROR
                            && addon.get(0).offset() == 0, "custom-language addon checker was not invoked");
        } finally {
            ScriptCheckApi.unregister("test:language");
        }
        System.out.println("PASS: multiple severities, recovery, addon globals and custom-language checker API");
    }
}
