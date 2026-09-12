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

        builder.push("交互优先级");
        builder.comment(
                "准星对准 NPC 时右键，是否让「使用手上物品」优先于「与 NPC 交互」（默认true）。",
                "解决的问题：对着 NPC 举弓拉不开、吃不了食物、喝不了药水。",
                "判定顺序：仅当该物品自身有使用行为（弓/弩/食物/药水/望远镜等，即 getUseAnimation 非 NONE）",
                "时才让物品优先；其余物品仍照原样交互，所以对话、商店、任务全不受影响。",
                "潜行(Shift)右键始终视为「要交互」，给你一个不改配置也能强制交互的手段。",
                "设为 false 则完全恢复原版行为。");
        InteractItemPriority = builder.define("InteractItemPriority", true);
        builder.pop();

        builder.push("返回起点");
        builder.comment(
                "NPC 返回起点时，快到起点却「停顿一下再瞬移过去」的修复开关（默认true）。",
                "原版机制：到达判定是硬编码的 ±0.2 格，而寻路精度远粗于此，于是路径走完却仍判「没到」，",
                "被当成卡住 → 冻结 10 刻（就是那个停顿）→ 重新寻路 → 累计 5 次后直接 setPos 瞬移。",
                "开启后：接近起点时不再瞬移、不再冻结，改为继续寻路走完最后一段。",
                "真的卡住（离起点很远）时仍会照原样瞬移，保底机制不拆。");
        ReturnHomeSmoothArrival = builder.define("ReturnHomeSmoothArrival", true);
        builder.comment(
                "「已经足够接近起点」的水平距离容差（格，默认3，范围1-16）。",
                "只比水平距离：竖直差异通常来自站在台阶/半砖上，不代表没走到。",
                "原版 0.2 格太严寻路达不到；3 格能覆盖「路径已到但差最后一两步」的情形。");
        ReturnHomeArrivalTolerance = builder.defineInRange("ReturnHomeArrivalTolerance", 3.0, 1.0, 16.0);
        builder.comment(
                "返回起点的超时瞬移时间（秒，默认30，范围1-600）。",
                "原版硬编码 600 ticks 即 30 秒（不是 1 分钟），这里保持同一默认值。",
                "超过该时间仍没回到起点就强制瞬移，这是防止 NPC 永久卡死的保底机制。");
        ReturnHomeTimeoutSeconds = builder.defineInRange("ReturnHomeTimeoutSeconds", 30, 1, 600);
        builder.pop();

        builder.push("死亡尸体");
        builder.comment(
                "NPC 尸体是否不再推挤玩家与其他实体（默认true）。",
                "原版缺陷：推挤逻辑只看「石像模式」开关，完全不看死亡状态，",
                "所以固定点位重生的 NPC 死后尸体仍会每 tick 把附近玩家推开。");
        KilledBodyNoPush = builder.define("KilledBodyNoPush", true);
        builder.comment(
                "「固定点位重生」的 NPC 尸体是否完全没有碰撞箱，连箭都挡不住（默认true）。",
                "只对 AI 设置里「返回起点」为开的 NPC 生效 —— 那类 NPC 死在哪都会传回起点重生，",
                "尸体必然留在与重生点无关的位置，挡路且无意义。",
                "实现方式与原版「隐藏尸体」一致：只把宽度压到 1e-5，包围盒退化成一条竖线，",
                "保留高度让渲染不受影响。设为 false 恢复原版行为（尸体是 0.8 宽的实心障碍）。");
        KilledBodyNoHitbox = builder.define("KilledBodyNoHitbox", true);
        builder.pop();

        builder.push("骑乘");
        builder.comment(
                "玩家骑乘 NPC 且「骑乘控制」为开、NPC 导航为陆地时，按空格让 NPC 跳跃（默认true）。",
                "走原版跳跃管线，因此尊重跳跃高度属性、跳跃提升药水与跳跃间隔。",
                "飞行导航的升降仍由原有逻辑处理，不受本项影响。");
        MountJumpEnabled = builder.define("MountJumpEnabled", true);
        builder.pop();

        builder.push("队伍共享");
        builder.comment(
                "击杀任务是否在原版计分板队伍（/team）内共享进度（默认false）。",
                "开启后：同队玩家击杀目标时，队内其他人的击杀任务进度同样推进。",
                "例：A B C 同队，只有 A 接了「击杀 aNPC」，B 击杀 aNPC 也能让 A 的进度 +1。",
                "默认关闭的原因：这改变了原版任务语义，服主应显式选择开启。",
                "只覆盖在线玩家；离线队友不会被推进（避免与登录时的存档读取冲突）。",
                "一次击杀对每个玩家只推进 1 点，与原版「范围击杀」的距离共享不会重复计数。");
        TeamShareKillQuest = builder.define("TeamShareKillQuest", false);
        builder.pop();

        CONFIG_SPEC = builder.build();
    }

    public static ForgeConfigSpec getConfig() { return CONFIG_SPEC; }

    public static ForgeConfigSpec.IntValue FollowerTeleportRange;
    public static ForgeConfigSpec.BooleanValue SmeltingRegisterOnServer;
    public static ForgeConfigSpec.BooleanValue InteractItemPriority;
    public static ForgeConfigSpec.BooleanValue ReturnHomeSmoothArrival;
    public static ForgeConfigSpec.DoubleValue ReturnHomeArrivalTolerance;
    public static ForgeConfigSpec.IntValue ReturnHomeTimeoutSeconds;
    public static ForgeConfigSpec.BooleanValue KilledBodyNoPush;
    public static ForgeConfigSpec.BooleanValue KilledBodyNoHitbox;
    public static ForgeConfigSpec.BooleanValue MountJumpEnabled;
    public static ForgeConfigSpec.BooleanValue TeamShareKillQuest;

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
