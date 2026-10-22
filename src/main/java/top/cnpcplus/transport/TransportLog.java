package top.cnpcplus.transport;

/**
 * 任务241t：传送子系统日志。
 *
 * <p>用户要求"滚去加日志"——本类提供：①启动横幅（确认客户端/服务端跑的是哪一版 jar）
 * ②统一的日志出口（CNPC 的 LogWriter，落在 CNPC 日志文件里；客户端用 slf4j 也能看到）。
 */
public final class TransportLog {
    private TransportLog() {}

    /** 构建号：每次改传送 UI 就 +1，日志里能直接看出跑的是哪一版。 */
    public static final String BUILD = "241w";

    public static void banner() {
        info("[cnpcplus] transport panel build=" + BUILD + " loaded");
    }

    public static void info(String msg) {
        try {
            noppes.npcs.shared.common.util.LogWriter.warn(msg);
        } catch (Throwable ignored) {
        }
        try {
            org.slf4j.LoggerFactory.getLogger("cnpcplus").warn(msg);
        } catch (Throwable ignored) {
        }
    }
}
