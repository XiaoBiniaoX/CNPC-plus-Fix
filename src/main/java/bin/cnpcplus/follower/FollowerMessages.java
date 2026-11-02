package bin.cnpcplus.follower;

import bin.cnpcplus.config.CnpcPlusConfig;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentString;
import noppes.npcs.NoppesStringUtils;
import noppes.npcs.roles.RoleFollower;

public final class FollowerMessages {
    public static final String KEY = "CNPCPlusFollowerMessages";
    public static final String LIMIT_DEFAULT = "抱歉，太多人了", DISMISS_DEFAULT = "祝你一路顺风";
    private static final Map<RoleFollower, Settings> VALUES = new WeakHashMap<>();
    private FollowerMessages() { }
    public static final class Settings {
        public final String limit, dismiss;
        public Settings(String limit, String dismiss) { this.limit = limit; this.dismiss = dismiss; }
        public static Settings read(NBTTagCompound tag, String legacy) {
            NBTTagCompound data = tag.getCompoundTag(KEY);
            String migrated = legacy == null || legacy.trim().isEmpty() || legacy.equals("你雇佣的人过多") ? LIMIT_DEFAULT : legacy;
            return new Settings(data.hasKey("Limit", 8) ? data.getString("Limit") : migrated,
                    data.hasKey("Dismiss", 8) ? data.getString("Dismiss") : DISMISS_DEFAULT);
        }
        public void write(NBTTagCompound tag) {
            NBTTagCompound data = new NBTTagCompound(); data.setString("Limit", limit); data.setString("Dismiss", dismiss); tag.setTag(KEY, data);
        }
    }
    public static Settings get(RoleFollower role) { return VALUES.getOrDefault(role, new Settings(LIMIT_DEFAULT, DISMISS_DEFAULT)); }
    public static void put(RoleFollower role, Settings value) { VALUES.put(role, value); }
    public static void load(RoleFollower role, NBTTagCompound tag) {
        put(role, Settings.read(tag, role.npc.world.isRemote ? LIMIT_DEFAULT : CnpcPlusConfig.getHireLimitMessage()));
    }
    public static void say(RoleFollower role, EntityPlayerMP player, String text) {
        if (text.trim().isEmpty()) return;
        player.sendMessage(new TextComponentString(role.npc.getName() + ": " + NoppesStringUtils.formatText(text, player, role.npc)));
    }
}
