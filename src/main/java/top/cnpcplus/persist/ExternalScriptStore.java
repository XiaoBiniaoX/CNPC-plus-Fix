package top.cnpcplus.persist;

import noppes.npcs.CustomNpcs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Game-root customnpcs/scripts/{player,forge,NPC,Block,Door,Item_Scripts}/*.js
 * Keys go into ScriptController.scripts so they appear in 加载脚本.
 */
public final class ExternalScriptStore {

    public static final String[] FOLDERS = {
            "player", "forge", "NPC", "Block", "Door", "Item_Scripts"
    };

    private ExternalScriptStore() {}

    public static File root() {
        File dir = CustomNpcs.Dir != null ? CustomNpcs.Dir : new File("customnpcs");
        File scripts = new File(dir, "scripts");
        if (!scripts.exists()) scripts.mkdirs();
        for (String name : FOLDERS) {
            File sub = new File(scripts, name);
            if (!sub.exists()) sub.mkdirs();
        }
        return scripts;
    }

    /** filename (relative, lower) → source */
    public static Map<String, String> loadAll() {
        Map<String, String> out = new LinkedHashMap<>();
        File scripts = root();
        for (String folder : FOLDERS) {
            File sub = new File(scripts, folder);
            if (!sub.isDirectory()) continue;
            collect(sub, folder.toLowerCase() + "/", out);
        }
        return out;
    }

    private static void collect(File dir, String prefix, Map<String, String> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = prefix + file.getName().toLowerCase();
            if (file.isDirectory()) {
                collect(file, name + "/", out);
            } else if (name.endsWith(".js")) {
                String code = read(file);
                if (code != null) out.put(name, code);
            }
        }
    }

    private static String read(File file) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("cnpcplus").error("读取外部脚本文件失败: {}", file, e);
            return null;
        }
    }
}
