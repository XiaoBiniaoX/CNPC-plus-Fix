package bin.cnpcplus;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Production jars, temporary flat world, loopback only, automatic stop. Opt-in Gradle task. */
public final class ServerSmoke {
    public static void main(String[] args) throws Exception {
        Path project = Paths.get(args[0]), cache = Paths.get(args[1]).resolve("caches");
        Path root = project.resolve("build/server-smoke-420");
        Files.createDirectories(root.resolve("mods"));
        Files.copy(Paths.get(args[2]), root.resolve("mods/cnpcplus.jar"), StandardCopyOption.REPLACE_EXISTING);
        for (String jar : Arrays.asList("!mixinbooter-10.7.jar", "[自定义NPC] CustomNPCs_1.12.2-(05Jul20).jar"))
            Files.copy(project.resolve("libs").resolve(jar), root.resolve("mods").resolve(jar), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(project.resolve("eula.txt"), root.resolve("eula.txt"), StandardCopyOption.REPLACE_EXISTING);
        for (String file : Arrays.asList("01-五类对象-tickfix-CD.js", "02-载体-timer.js", "03-NPC-数学文本布局.js", "09-NPC-事件订阅.js"))
            Files.copy(project.resolve("验证脚本-4.2.0").resolve(file), root.resolve("verification-" + file.substring(0, 2) + ".js"), StandardCopyOption.REPLACE_EXISTING);
        try (InputStream script = ServerSmoke.class.getResourceAsStream("/events-smoke.js")) {
            Files.copy(script, root.resolve("events-smoke.js"), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.write(root.resolve("server.properties"), Arrays.asList("server-ip=127.0.0.1", "server-port=0", "online-mode=false", "enable-query=false",
                "enable-rcon=false", "view-distance=2", "max-players=1", "level-type=FLAT", "generate-structures=false", "level-name=smoke-world", "max-tick-time=60000"), StandardCharsets.UTF_8);
        List<String> classpath = new ArrayList<>();
        Path forge = cache.resolve("forge_gradle/maven_downloader/net/minecraftforge/forge/1.12.2-14.23.5.2860/forge-1.12.2-14.23.5.2860-universal.jar");
        classpath.add(forge.toString());
        // Use the production manifest, without development ASM or the server jar's older Log4j first.
        try (java.util.jar.JarFile archive = new java.util.jar.JarFile(forge.toFile())) {
            for (String entry : archive.getManifest().getMainAttributes().getValue("Class-Path").split(" ")) {
                if (!entry.startsWith("libraries/")) continue;
                String relative = entry.substring("libraries/".length());
                Path library = cache.resolve("forge_gradle/maven_downloader").resolve(relative);
                if (!Files.isRegularFile(library)) {
                    String[] parts = relative.split("/"); int n = parts.length;
                    Path folder = cache.resolve("modules-2/files-2.1").resolve(String.join(".", Arrays.copyOf(parts, n - 3))).resolve(parts[n - 3]).resolve(parts[n - 2]);
                    try (java.util.stream.Stream<Path> files = Files.walk(folder)) {
                        library = files.filter(path -> path.getFileName().toString().equals(parts[n - 1])).findFirst().orElseThrow(() -> new IOException("Missing cached library " + relative));
                    }
                }
                classpath.add(library.toString());
            }
        }
        classpath.add(cache.resolve("forge_gradle/minecraft_user_repo/mcp/1.12.2-20200226.224830/joined/downloadServer/server.jar").toString());
        Process process = new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin/java.exe").toString(), "-Xmx2G", "-Dfile.encoding=UTF-8",
                "-cp", String.join(File.pathSeparator, classpath), "net.minecraftforge.fml.relauncher.ServerLaunchWrapper", "nogui")
                .directory(root.toFile()).redirectErrorStream(true).start();
        ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
        watchdog.schedule(() -> process.destroyForcibly(), 180, TimeUnit.SECONDS);
        boolean ready = false, summoned = false, eventsPassed = false;
        Path log = root.resolve("smoke-output.log");
        try (BufferedReader output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter saved = Files.newBufferedWriter(log, StandardCharsets.UTF_8);
                Writer commands = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
            for (String line; (line = output.readLine()) != null;) {
                saved.write(line); saved.newLine(); saved.flush();
                if (line.contains("Done (") && !ready) {
                    ready = true;
                    net.minecraft.nbt.NBTTagCompound world;
                    try (InputStream level = Files.newInputStream(root.resolve("smoke-world/level.dat"))) {
                        world = net.minecraft.nbt.CompressedStreamTools.readCompressed(level).getCompoundTag("Data");
                    }
                    net.minecraft.nbt.NBTTagCompound npc = new net.minecraft.nbt.NBTTagCompound();
                    npc.setBoolean("ScriptEnabled", true); npc.setString("ScriptLanguage", "ECMAScript");
                    net.minecraft.nbt.NBTTagList pages = new net.minecraft.nbt.NBTTagList();
                    List<String> codes = Arrays.asList("load('events-smoke.js')",
                            "function verify(other) { if (events.list().size() !== 0 || events.off(other)) throw new Error('page isolation'); var own = events.on('net.minecraftforge.event.entity.living.LivingHurtEvent', function(e) {}); if (!events.off(own) || events.has(own)) throw new Error('page off'); var h = events.on('net.minecraftforge.event.entity.living.LivingHurtEvent', function(e) {}); if (events.offAll() !== 1 || events.has(h)) throw new Error('page offAll'); }",
                            "load('verification-01.js'); function verifyResults(e) { if (fixedCount < 100 || nativeCount < 10 || !started) throw new Error('01 native tickfix'); interact({npc: e.npc, player: {isSneaking: function() { return false }}}) }",
                            "load('verification-03.js'); function verifyResults(e) { interact(e) }",
                            "load('verification-09.js'); function verifyResults(e) { if (!events.has(hurtHandle)) throw new Error('09 production hurt callback: ' + hurtHandle.getState()) }");
                    for (String code : codes) {
                        net.minecraft.nbt.NBTTagCompound page = new net.minecraft.nbt.NBTTagCompound(); page.setString("Script", code); pages.appendTag(page);
                    }
                    npc.setTag("Scripts", pages);
                    commands.write("summon customnpcs:customnpc " + world.getInteger("SpawnX") + " 64 " + world.getInteger("SpawnZ") + " " + npc + "\n"); commands.flush();
                }
                if (line.contains("Object successfully summoned")) summoned = true;
                if (line.contains("[EVENTS_SMOKE_PASS]") && !eventsPassed) {
                    eventsPassed = true; commands.write("save-all\nstop\n"); commands.flush();
                }
                if (line.contains("[EVENTS_SMOKE_FAIL]")) { commands.write("stop\n"); commands.flush(); }
            }
        } finally { watchdog.shutdownNow(); }
        int exit = process.waitFor();
        System.out.println("Server smoke: ready=" + ready + ", summoned=" + summoned + ", events=" + eventsPassed + ", exit=" + exit + "; " + log);
        if (!ready || !summoned || !eventsPassed || exit != 0) throw new IllegalStateException("Server smoke failed; see " + log);
    }
}
