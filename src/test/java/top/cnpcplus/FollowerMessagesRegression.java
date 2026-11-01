package top.cnpcplus;

import net.minecraft.nbt.CompoundTag;
import top.cnpcplus.follower.FollowerMessages;

public final class FollowerMessagesRegression {
    public static void main(String[] args) {
        CompoundTag tag = new CompoundTag();
        FollowerMessages.Settings defaults = FollowerMessages.Settings.read(tag, "你雇佣的人过多");
        check(defaults.limit().equals("抱歉，太多人了") && defaults.dismiss().equals("祝你一路顺风"), "obsolete defaults survived");
        check(FollowerMessages.Settings.read(tag, "已有自定义上限提示").limit().equals("已有自定义上限提示"), "custom global message lost");
        check(top.cnpcplus.config.CnpcPlusServerConfig.HireLimitMessage.getDefault().isEmpty(), "obsolete global default regenerated");
        tag.putString("DialogHire", "你好 {player}");
        tag.putString("DialogFarewell", "再见");
        FollowerMessages.Settings custom = new FollowerMessages.Settings("本NPC超限", "本NPC解雇");
        custom.write(tag);
        check(FollowerMessages.Settings.read(tag, "other global").equals(custom), "NPC settings overridden by global");
        check(tag.getString("DialogHire").equals("你好 {player}") && tag.getString("DialogFarewell").equals("再见"), "old hire/farewell lost");
        CompoundTag clone = tag.copy();
        new FollowerMessages.Settings("副本", "").write(clone);
        check(FollowerMessages.Settings.read(tag, null).equals(custom), "clone mutated original");
        new FollowerMessages.Settings("", "").write(tag);
        check(FollowerMessages.Settings.read(tag, "global").equals(new FollowerMessages.Settings("", "")), "empty message resurrected");
        System.out.println("FOLLOWER_MESSAGES_REGRESSION=PASS");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
