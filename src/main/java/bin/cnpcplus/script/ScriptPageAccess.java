package bin.cnpcplus.script;

import javax.script.ScriptEngine;
import noppes.npcs.controllers.IScriptHandler;

/** Internal mixin seam. Addons use ScriptPageApi instead. */
public interface ScriptPageAccess {
    ScriptPageState cnpcplus$pageState();
    IScriptHandler cnpcplus$handler();
    ScriptEngine cnpcplus$engine();
    void cnpcplus$resetExecutor();
}
