package top.cnpcplus.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 安全读取 {@link CnpcPlusServerConfig} 的包装。
 *
 * <h3>为什么需要它（字节码实证）</h3>
 * {@code CnpcPlusServerConfig} 注册为 {@code ModConfig.Type.SERVER}，
 * 这类配置的数据要到**世界即将加载**时才 attach 到 spec 上。
 * 而本项目有几处 mixin 落在更早、更底层的路径上，最典型的是
 * {@code EntityCustomNpc.m_6972_}（getDimensions）—— vanilla 的 {@code Entity} 构造器
 * 就会通过 {@code refreshDimensions} 调它，也就是**实体一创建就会被调到**。
 *
 * <p>{@code ForgeConfigSpec$ConfigValue.get()} 的字节码（forge 47.4.0）：
 * <pre>
 *  offset 10  if (FMLEnvironment.production) goto 36     // 生产环境跳过下面的检查
 *  offset 31  Preconditions.checkState(childConfig != null,
 *             "Cannot get config value before config is loaded. ...
 *              This error is currently only thrown in the development environment,
 *              to avoid breaking published mods. ...")
 *  offset 43  if (childConfig == null) return defaultSupplier.get();   // 回落默认值
 * </pre>
 * 也就是说：
 * <ul>
 *   <li><b>生产环境</b>（玩家的服务器/客户端）config 未加载时安全返回默认值，不崩；</li>
 *   <li><b>开发环境</b>会 {@code IllegalStateException} —— 而 mixin 里抛异常发生在
 *       实体构造/维度计算这类路径上，会直接把实体创建炸掉。</li>
 * </ul>
 * 光靠「生产不崩」就裸调 {@code get()} 是赌运气：Forge 那段注释明确写了
 * 「in a future version, this will also throw in the production environment」。
 *
 * <p>所以统一走这里：捕获任何异常并回落到默认值。开销是一个 try-catch，
 * 在未抛异常的正常路径上 JIT 会完全优化掉。
 *
 * <h3>为什么不用 spec.isLoaded()</h3>
 * 那个方法在 1.20.1 的 Forge 里存在，但它检查的是 spec 自身而非 childConfig，
 * 且需要额外持有 spec 引用。直接 try-catch 语义更直接，也不依赖 Forge 内部结构。
 *
 * <p>放在 config 包（不是 mixin 包）：mixin 包内的类不能被外部直接引用。
 */
public final class ServerConfigAccess {

    private ServerConfigAccess() {
    }

    /**
     * 读 boolean 配置；未加载或任何异常时回落到该项自己的默认值。
     */
    public static boolean bool(ForgeConfigSpec.BooleanValue value, boolean fallback) {
        if (value == null) return fallback;
        try {
            return value.get();
        } catch (Throwable t) {
            // config 尚未加载（开发环境会抛）。用声明时的默认值，语义与生产环境一致。
            try {
                Boolean d = value.getDefault();
                return d == null ? fallback : d;
            } catch (Throwable t2) {
                return fallback;
            }
        }
    }

    /** 读 int 配置；未加载或任何异常时回落到该项自己的默认值。 */
    public static int integer(ForgeConfigSpec.IntValue value, int fallback) {
        if (value == null) return fallback;
        try {
            return value.get();
        } catch (Throwable t) {
            try {
                Integer d = value.getDefault();
                return d == null ? fallback : d;
            } catch (Throwable t2) {
                return fallback;
            }
        }
    }

    /** 读 double 配置；未加载或任何异常时回落到该项自己的默认值。 */
    public static double decimal(ForgeConfigSpec.DoubleValue value, double fallback) {
        if (value == null) return fallback;
        try {
            return value.get();
        } catch (Throwable t) {
            try {
                Double d = value.getDefault();
                return d == null ? fallback : d;
            } catch (Throwable t2) {
                return fallback;
            }
        }
    }
}
