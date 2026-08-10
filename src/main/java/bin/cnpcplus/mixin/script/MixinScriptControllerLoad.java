package bin.cnpcplus.mixin.script;

import bin.cnpcplus.CnpcPlus;
import noppes.npcs.CustomNpcs;
import noppes.npcs.controllers.ScriptController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * After world scripts load, also load CustomNpcs.Dir/scripts/{player,forge,NPC,Block,Door,Item_Scripts}.
 * Keys appear in 「加载脚本」 list: e.g. player/foo.js
 */
@Mixin(ScriptController.class)
public class MixinScriptControllerLoad {

    private static final String[] GLOBAL_FOLDERS = {
            "player", "forge", "NPC", "Block", "Door", "Item_Scripts"
    };

    @Shadow(remap = false)
    public Map<String, String> scripts;

    @Shadow(remap = false)
    public Map<String, String> languages;

    @Shadow(remap = false)
    public long lastLoaded;

    @Inject(method = "loadCategories", at = @At("RETURN"), remap = false)
    private void cnpcplusLoadGlobalScripts(CallbackInfo ci) {
        File root = CustomNpcs.Dir;
        if (root == null) return;
        File scriptsRoot = new File(root, "scripts");
        if (!scriptsRoot.exists()) {
            //noinspection ResultOfMethodCallIgnored
            scriptsRoot.mkdirs();
        }
        // ensure typed subfolders exist for the player
        for (String folder : GLOBAL_FOLDERS) {
            File sub = new File(scriptsRoot, folder);
            if (!sub.exists()) {
                //noinspection ResultOfMethodCallIgnored
                sub.mkdirs();
            }
        }

        if (this.languages == null || this.languages.isEmpty()) {
            // still allow .js even if engine map empty
            loadTree(scriptsRoot, "", ".js");
        } else {
            for (String ext : this.languages.values()) {
                if (ext == null || ext.isEmpty()) continue;
                loadTree(scriptsRoot, "", ext.toLowerCase(Locale.ROOT));
            }
        }
        this.lastLoaded = System.currentTimeMillis();
    }

    private void loadTree(File dir, String prefix, String ext) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = prefix + file.getName().toLowerCase(Locale.ROOT);
            if (file.isDirectory()) {
                loadTree(file, name + "/", ext);
            } else if (name.endsWith(ext) || name.endsWith(ext.toLowerCase(Locale.ROOT))) {
                try {
                    String code = readFile(file);
                    if (this.scripts != null) {
                        this.scripts.put(name, code);
                    }
                } catch (Exception e) {
                    CnpcPlus.LOGGER.error("[ScriptGlobal] failed reading {}", file, e);
                }
            }
        }
    }

    private static String readFile(File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
