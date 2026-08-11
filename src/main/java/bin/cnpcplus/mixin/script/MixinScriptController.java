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
import java.util.Map;

/**
 * Also load js from customnpcs/scripts/{player,forge,NPC,Block,Door,Item_Scripts}/
 * Keys: folder/filename.js so they show in "load scripts" list.
 */
@Mixin(ScriptController.class)
public class MixinScriptController {

    private static final String[] FOLDERS = {
            "player", "forge", "NPC", "Block", "Door", "Item_Scripts"
    };

    @Shadow(remap = false)
    public Map scripts;

    @Shadow(remap = false)
    public long lastLoaded;

    @Inject(method = "loadCategories", at = @At("RETURN"), remap = false)
    private void cnpcplusLoadGlobalScripts(CallbackInfo ci) {
        try {
            File root = CustomNpcs.Dir;
            if (root == null) root = new File("customnpcs");
            File scriptsRoot = new File(root, "scripts");
            if (!scriptsRoot.exists()) scriptsRoot.mkdirs();
            for (int i = 0; i < FOLDERS.length; i++) {
                File folder = new File(scriptsRoot, FOLDERS[i]);
                if (!folder.exists()) folder.mkdirs();
                loadJsTree(folder, FOLDERS[i] + "/");
            }
            this.lastLoaded = System.currentTimeMillis();
        } catch (Throwable t) {
            CnpcPlus.LOGGER.error("[Script] global load failed", t);
        }
    }

    private void loadJsTree(File dir, String prefix) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            String name = prefix + f.getName().toLowerCase();
            if (f.isDirectory()) {
                loadJsTree(f, name + "/");
            } else if (name.endsWith(".js")) {
                try {
                    this.scripts.put(name, readUtf8(f));
                } catch (Exception e) {
                    CnpcPlus.LOGGER.warn("[Script] read failed " + f + ": " + e);
                }
            }
        }
    }

    private static String readUtf8(File file) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } finally {
            br.close();
        }
    }
}
