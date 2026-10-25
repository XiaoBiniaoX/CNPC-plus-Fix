package top.cnpcplus.transport;

/**
 * 任务241t：传送子系统日志。
 *
 * <p>241z-7：只保留一行启动版本横幅和真实错误；操作/测试日志全部移除，避免刷屏。
 */
public final class TransportLog {
    private TransportLog() {}

    /** 构建号：每次改传送 UI 就 +1，日志里能直接看出跑的是哪一版。 */
    public static final String BUILD = "4.0.5-hotfix2";

    public static void banner() {
        String msg = "[cnpcplus] transport panel build=" + BUILD + " loaded";
        try {
            noppes.npcs.shared.common.util.LogWriter.info(msg);
        } catch (Throwable ignored) {
        }
        try {
            org.slf4j.LoggerFactory.getLogger("cnpcplus").info(msg);
        } catch (Throwable ignored) {
        }
    }

    /** 仅用于真实异常，不要拿来做操作日志。 */
    public static void error(String msg) {
        try {
            noppes.npcs.shared.common.util.LogWriter.error(msg);
        } catch (Throwable ignored) {
        }
        try {
            org.slf4j.LoggerFactory.getLogger("cnpcplus").error(msg);
        } catch (Throwable ignored) {
        }
    }
}
