package top.cnpcplus.transport.client;

import java.nio.file.Path;
import java.util.List;

/**
 * 任务241j：文件拖放路由。
 * vanilla MouseHandler 把 onFilesDrop 发给顶层 Screen，编辑器是子页收不到，
 * 故由 MixinScreenFilesDrop 转发到当前登记的目标（编辑器打开时登记，关闭时注销）。
 */
public final class TransportDropHandler {
    private TransportDropHandler() {}

    public interface DropTarget {
        void cnpcplus$onDrop(List<Path> paths);
    }

    private static volatile DropTarget active;

    public static void setActive(DropTarget target) { active = target; }

    public static void clear(DropTarget target) {
        if (active == target) active = null;
    }

    public static void accept(List<Path> paths) {
        DropTarget t = active;
        if (t != null && paths != null && !paths.isEmpty()) {
            t.cnpcplus$onDrop(paths);
        }
    }
}
