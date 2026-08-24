package top.cnpcplus.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 服务端配置（cnpcplus-server.toml）。服务端逻辑（随从 AI、熔炼配方等）读取。
 * 不注册 EventBusSubscriber：见文件末尾说明，监听 ModConfigEvent 会导致玩家连服被踢。
 */
public class CnpcPlusServerConfig {

    private static final ForgeConfigSpec CONFIG_SPEC;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("随从");
        builder.comment("随从/雇佣兵距离玩家超过该格数（平方距离开方）后强制传送到玩家身边，避免被困在坑洞/墙壁中无法跟随（默认12，范围1-64）");
        FollowerTeleportRange = builder.defineInRange("FollowerTeleportRange", 12, 1, 64);
        builder.pop();

        builder.push("自定义熔炼");
        builder.comment("熔炼配方文件路径下的数据是否在服务端加载时重新注册到 Minecraft RecipeManager（默认true）");
        SmeltingRegisterOnServer = builder.define("SmeltingRegisterOnServer", true);
        builder.pop();

        CONFIG_SPEC = builder.build();
    }

    public static ForgeConfigSpec getConfig() { return CONFIG_SPEC; }

    public static ForgeConfigSpec.IntValue FollowerTeleportRange;
    public static ForgeConfigSpec.BooleanValue SmeltingRegisterOnServer;

    /*
     * 刻意不再监听 ModConfigEvent 去调 event.getConfig().save()。
     *
     * 那样写会让玩家连服时被踢，客户端提示「此服务器发送了一个无效的数据包」。原因：
     * SERVER 类型的配置会在握手阶段由服务端同步给客户端（ConfigSync → acceptSyncedConfig →
     * fireEvent），此时客户端侧持有的 configData 是内存里的 SimpleCommentedConfig，
     * 而 ModConfig.save() 内部无条件 cast 成 CommentedFileConfig，于是抛
     * ClassCastException。异常发生在登录期的 ClientboundCustomQueryPacket 处理链上，
     * Forge 把它当成握手包解析失败，直接判定为无效数据包并断开连接。
     *
     * 而且这个 save() 本来就是多余的：ForgeConfigSpec 在文件被修改时会自行回写，
     * 我们从未在代码里改过配置值，没有任何需要主动落盘的场景。
     * 已用本地专用服务器实测复现并验证（客户端 latest.log 完整栈实证）。
     */
}
