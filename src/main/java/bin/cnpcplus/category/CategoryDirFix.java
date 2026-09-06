package bin.cnpcplus.category;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 任务/对话分类目录与内存 title 的一致性维护。
 *
 * <p>CNPC 把分类直接存成目录，目录名就是分类名，且没有任何索引文件或元数据，
 * 启动时靠扫盘决定有哪些分类、id 是现场按遍历顺序分配的。
 * 因此「内存 title」必须始终等于「磁盘目录名」，否则重进世界就会分裂成两个分类。
 *
 * <p>原版 {@code saveCategory} 里写了 {@code olddir.renameTo(newdir)}，但有三个静默失败出口：
 * 单人/局域网时客户端与服务端共用同一个 Controller 单例，GUI 就地改了 title，
 * 服务端比较「旧名 != 新名」时两边都是新名，整个重命名分支被跳过，磁盘压根没动；
 * 另外目标目录已存在、或 Windows 上 renameTo 失败时都直接 return。
 * 三者都会让内存 title 与磁盘目录名发散，随后 saveQuest 用新 title 现建目录只写入本次改动的条目，
 * 旧目录连内容原地保留 —— 重启后旧分类连数据一起复活，新分类只剩零星条目。
 *
 * <p>本类自己记住「分类 id → 磁盘真实目录名」，在保存后把磁盘对齐到 title；
 * 对不齐就把内存 title 回滚成真实目录名，死守上面那条不变量。
 * 宁可让这次重命名不生效，也不允许旧目录带着数据复活。
 *
 * <p>刻意放在 mixin 包之外：mixin 包内的类不能被外部直接引用，否则触发 IllegalClassLoadError。
 */
public final class CategoryDirFix {

    /** 以 Controller 实例为键，换存档后旧登记自动失效。 */
    private static final Map<Object, Map<Integer, String>> DIR_NAMES = new WeakHashMap<>();

    private CategoryDirFix() {
    }

    private static Map<Integer, String> namesOf(Object controller) {
        return DIR_NAMES.computeIfAbsent(controller, c -> new HashMap<>());
    }

    public static synchronized String remembered(Object controller, int id) {
        return namesOf(controller).get(id);
    }

    public static synchronized void remember(Object controller, int id, String dirName) {
        if (dirName == null || dirName.isEmpty()) return;
        namesOf(controller).put(id, dirName);
    }

    public static synchronized void forget(Object controller, int id) {
        namesOf(controller).remove(id);
    }

    public static synchronized void forgetAll(Object controller) {
        namesOf(controller).clear();
    }

    /**
     * 保存分类后把磁盘目录对齐到 title。
     *
     * @return 最终应该生效的分类名；与传入 title 不同时调用方必须回滚内存 title
     */
    public static synchronized String syncDir(Object controller, File root, int id, String title) {
        if (root == null || title == null || title.isEmpty()) return title;

        String known = remembered(controller, id);
        if (known == null) {
            // 新建分类：原版已经 mkdirs 过，这里只登记真实目录名。
            remember(controller, id, title);
            return title;
        }
        if (known.equals(title)) return title;

        if (renameDir(root, known, title)) {
            remember(controller, id, title);
            return title;
        }
        // 搬不动就回滚内存，避免旧目录下次进世界复活成一个独立分类。
        return known;
    }

    /** 三级降级重命名：原子移动 → renameTo → 逐文件搬迁。 */
    public static boolean renameDir(File root, String oldTitle, String newTitle) {
        if (root == null || oldTitle == null || newTitle == null) return false;
        if (oldTitle.equals(newTitle)) return true;

        File olddir = new File(root, oldTitle);
        File newdir = new File(root, newTitle);

        // 旧目录不存在：可能是还没落过盘的空分类，建出新目录即可。
        if (!olddir.isDirectory()) {
            return newdir.isDirectory() || newdir.mkdirs();
        }
        // 目标已存在且不是同一个目录（Windows 上仅大小写变化会指向同一个），不合并，交调用方回滚。
        if (newdir.exists() && !sameFile(olddir, newdir)) {
            return false;
        }

        try {
            Files.move(olddir.toPath(), newdir.toPath(), StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (Exception ignored) {
            // 落到下面的降级路径。
        }
        if (olddir.renameTo(newdir)) return true;
        return moveContents(olddir, newdir);
    }

    private static boolean sameFile(File a, File b) {
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (Exception e) {
            return false;
        }
    }

    /** 逐个文件搬迁；只有全部搬完才删旧目录，遇到子目录保守放弃，宁可留残留也不丢数据。 */
    private static boolean moveContents(File olddir, File newdir) {
        if (!newdir.isDirectory() && !newdir.mkdirs()) return false;
        File[] files = olddir.listFiles();
        if (files == null) return false;

        boolean allMoved = true;
        for (File f : files) {
            if (f.isDirectory()) {
                allMoved = false;
                continue;
            }
            try {
                Files.move(f.toPath(), new File(newdir, f.getName()).toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                allMoved = false;
            }
        }
        if (allMoved) {
            olddir.delete();
        }
        return allMoved;
    }

    /** 递归删除目录，替代原版对非空目录必然失败的 {@code dir.delete()}。 */
    public static boolean deleteDir(File dir) {
        if (dir == null || !dir.exists()) return true;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    if (!deleteDir(f)) return false;
                } else if (!f.delete()) {
                    return false;
                }
            }
        }
        return dir.delete();
    }
}
