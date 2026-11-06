package top.cnpcplus;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.network.FriendlyByteBuf;
import top.cnpcplus.config.NpcInteractionConfig;
import top.cnpcplus.config.network.PacketNpcInteractionConfig;

/** Config/codec execution and source wiring only; no game GUI or packet delivery claim. */
public final class NpcInteractionConfigRegression {
    public static void main(String[] args) throws Exception {
        String screen = Files.readString(Path.of("src/main/java/top/cnpcplus/config/client/CnpcPlusConfigScreen.java"));
        check(screen.indexOf("addHeader(tr(\"cnpcplus.config.client.header.interaction\"))")
                < screen.indexOf("addHeader(tr(\"cnpcplus.config.client.header.main\"))"), "interaction section not first");
        check(screen.contains("addServerBool(tr(\"cnpcplus.config.interaction.allowInvisibleNpc\"), allowInvisibleInteraction"), "toggle not wired");
        check(screen.contains("serverEditable && interactionChanged")
                && screen.contains("sendToServer(new PacketNpcInteractionConfig(allowInvisibleInteraction))"), "server edit path missing");
        check(!screen.contains("NpcInteractionConfig.ALLOW_INVISIBLE_NPC_INTERACTION.set(")
                && !screen.contains("NpcInteractionConfig.SPEC.save()"), "client writes server config directly");
        for (String language : new String[]{"zh_cn", "en_us", "ru_ru"}) {
            var json = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/cnpcplus/lang", language + ".json"))).getAsJsonObject();
            for (String key : new String[]{"cnpcplus.config.client.header.interaction", "cnpcplus.config.interaction.allowInvisibleNpc", "cnpcplus.config.interaction.allowInvisibleNpc.tooltip"}) {
                check(json.has(key) && !json.get(key).getAsString().isBlank(), "missing translation " + language + ": " + key);
            }
        }
        for (boolean enabled : new boolean[]{false, true}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                var packet = new PacketNpcInteractionConfig(enabled);
                packet.encode(buffer);
                check(buffer.readableBytes() == 1, "boolean wire size changed");
                check(packet.equals(PacketNpcInteractionConfig.decode(buffer)) && !buffer.isReadable(), "codec mismatch");
            } finally {
                buffer.release();
            }
        }
        Path directory = Files.createTempDirectory(Path.of("Temp"), "interaction-config-");
        Path path = directory.resolve("cnpcplus-interaction.toml");
        try (var file = CommentedFileConfig.builder(path).sync().build()) {
            file.load();
            NpcInteractionConfig.SPEC.setConfig(file);
            try {
                check(!NpcInteractionConfig.ALLOW_INVISIBLE_NPC_INTERACTION.getDefault(), "default must be false");
                for (boolean enabled : new boolean[]{true, false}) {
                    NpcInteractionConfig.ALLOW_INVISIBLE_NPC_INTERACTION.set(enabled);
                    NpcInteractionConfig.ALLOW_INVISIBLE_NPC_INTERACTION.save();
                    check(NpcInteractionConfig.ALLOW_INVISIBLE_NPC_INTERACTION.get() == enabled, "hot read mismatch");
                    try (var saved = CommentedFileConfig.builder(path).sync().build()) {
                        saved.load();
                        check(Boolean.valueOf(enabled).equals(saved.get("allow_invisible_npc_interaction")), "disk save mismatch");
                    }
                }
            } finally {
                NpcInteractionConfig.SPEC.setConfig(null);
            }
        }
        System.out.println("NPC_INTERACTION_CONFIG_REGRESSION=PASS (default false; true/false codecs, hot read and disk save; GUI source wiring and three languages; no game/delivery test)");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
