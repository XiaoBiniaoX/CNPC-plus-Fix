package top.cnpcplus.follower;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import noppes.npcs.roles.RoleFollower;
import noppes.npcs.shared.client.util.NoppesStringUtils;
import top.cnpcplus.config.CnpcPlusServerConfig;
import top.cnpcplus.config.ServerConfigAccess;

public final class FollowerMessages {
    public static final String KEY = "CNPCPlusFollowerMessages";
    public static final String LIMIT_DEFAULT = "抱歉，太多人了";
    public static final String DISMISS_DEFAULT = "祝你一路顺风";
    private static final Map<RoleFollower, Settings> VALUES = new WeakHashMap<>();
    private FollowerMessages() {}

    public record Settings(String limit, String dismiss) {
        public Settings {
            limit = limit == null ? LIMIT_DEFAULT : limit;
            dismiss = dismiss == null ? DISMISS_DEFAULT : dismiss;
        }

        public static Settings read(CompoundTag tag, String legacy) {
            CompoundTag messages = tag.getCompound(KEY);
            // Migrate an explicitly customized old global message; replace the obsolete default.
            String migrated = legacy == null || legacy.isBlank() || legacy.equals("你雇佣的人过多")
                    ? LIMIT_DEFAULT : legacy;
            return new Settings(messages.contains("Limit") ? messages.getString("Limit") : migrated,
                    messages.contains("Dismiss") ? messages.getString("Dismiss") : DISMISS_DEFAULT);
        }

        public void write(CompoundTag tag) {
            CompoundTag messages = new CompoundTag();
            messages.putString("Limit", limit);
            messages.putString("Dismiss", dismiss);
            tag.put(KEY, messages);
        }
    }

    public static synchronized Settings get(RoleFollower role) {
        return VALUES.getOrDefault(role, new Settings(LIMIT_DEFAULT, DISMISS_DEFAULT));
    }

    public static synchronized void put(RoleFollower role, Settings settings) {
        VALUES.put(role, settings);
    }

    public static void load(RoleFollower role, CompoundTag tag) {
        String legacy = role.npc != null && !role.npc.isClientSide()
                ? ServerConfigAccess.string(CnpcPlusServerConfig.HireLimitMessage, LIMIT_DEFAULT) : LIMIT_DEFAULT;
        put(role, Settings.read(tag, legacy));
    }

    /** Direct system chat: no LOS guard and no dependency on the client tracking this NPC. */
    public static void say(RoleFollower role, ServerPlayer player, String text) {
        if (text == null || text.isBlank() || player == null || role.npc == null) return;
        String formatted = NoppesStringUtils.formatText(text, player, role.npc);
        if (!formatted.isBlank()) player.sendSystemMessage(
                Component.literal(role.npc.getName().getString() + ": " + formatted));
    }
}
