package top.cnpcplus.category;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 全局「任务 / 对话」分类目录的重命名与删除修复。
 *
 * <p>原版把分类名直接当目录名（`world/customnpcs/quests/<title>`），`id` 完全不落盘，
 * 重启时 {@code loadCategoryDir} 反过来用目录名当分类名。于是重命名一旦没能真正改到
 * 磁盘目录，内存与磁盘就发散：GUI 显示新名 → 后续 {@code saveQuest} 用新名 {@code mkdirs()}
 * 只把「改名之后又编辑过的条目」写进新目录，旧目录连其余条目原地不动 →
 * 重进世界时两个目录各自被当成一个分类，表现为「旧目录连内容一起复原、新目录只有部分内容」。
 *
 * <p>原版有三条会造成发散的路径：
 * <ol>
 *   <li>单人/开房时客户端与服务端共用 {@code QuestController.instance} 单例，GUI 重命名
 *       直接改的就是服务端那个 {@code QuestCategory} 对象，所以服务端
 *       {@code saveCategory} 里 {@code currentCategory.title.equals(category.title)}
 *       恒为真 —— 整个重命名分支被跳过，磁盘目录压根没人改。</li>
 *   <li>{@code newdir.exists()} 时直接 return，内存已改磁盘未改。</li>
 *   <li>{@code File.renameTo} 在 Windows 上目录非空/有句柄/大小写差异时返回 false，
 *       同样直接 return。</li>
 * </ol>
 *
 * <p>本类只负责把目录搬到新名字，保证「内存 title 与磁盘目录名一致」这一条不变量。
 * 放在 mixin 包外：普通 mixin 类不能被外部引用。
 */
public final class CategoryDirFix {

    /**
     * 每个 Controller 各自记录「分类 id → 磁盘上真实目录名」。
     *
     * <p>必须自己记：单人/开房时 GUI 改的就是服务端那个分类对象，
     * 等 {@code saveCategory} 执行时 {@code title} 已经是新名，旧名（= 真实目录名）
     * 在原版数据结构里已经无处可查。key 用 Controller 实例，换存档自然失效。
     */
    private static final java.util.Map<Object, java.util.Map<Integer, String>> DIR_NAMES =
            new java.util.WeakHashMap<>();
    private static final Object LOCK = new Object();

    private CategoryDirFix() {
    }

    /** 记录某分类当前在磁盘上的目录名。 */
    public static void remember(Object controller, int id, String dirName) {
        if (controller == null || dirName == null) return;
        synchronized (LOCK) {
            DIR_NAMES.computeIfAbsent(controller, k -> new java.util.HashMap<>()).put(id, dirName);
        }
    }

    /** 取某分类在磁盘上的目录名；没记录过返回 null。 */
    public static String remembered(Object controller, int id) {
        synchronized (LOCK) {
            java.util.Map<Integer, String> m = DIR_NAMES.get(controller);
            return m == null ? null : m.get(id);
        }
    }

    public static void forget(Object controller, int id) {
        synchronized (LOCK) {
            java.util.Map<Integer, String> m = DIR_NAMES.get(controller);
            if (m != null) m.remove(id);
        }
    }

    public static void forgetAll(Object controller) {
        synchronized (LOCK) {
            DIR_NAMES.remove(controller);
        }
    }

    /**
     * 保存分类后把磁盘目录名对齐到 {@code title}。
     *
     * @return 最终应当生效的 title（搬运失败时返回旧目录名，调用方据此回滚内存，
     *         保证内存与磁盘永不发散）。
     */
    public static String syncDir(Object controller, File root, int id, String title) {
        String known = remembered(controller, id);
        if (known == null) {
            // 新建分类：原版已经 mkdirs 过，这里只登记。
            remember(controller, id, title);
            return title;
        }
        if (known.equals(title)) return title;
        if (renameDir(root, known, title)) {
            remember(controller, id, title);
            return title;
        }
        // 搬不动就回滚内存，宁可重命名不生效，也不能让旧目录带着数据复活。
        return known;
    }

    /**
     * 把 {@code oldTitle} 目录搬成 {@code newTitle}。
     *
     * @return true 表示磁盘上现在已经是 newTitle（含「本来就不需要搬」的情况）；
     *         false 表示搬运失败，调用方应把内存 title 回滚，绝不能让两边发散。
     */
    public static boolean renameDir(File root, String oldTitle, String newTitle) {
        if (root == null || oldTitle == null || newTitle == null) return false;
        if (oldTitle.equals(newTitle)) return true;

        File olddir = new File(root, oldTitle);
        File newdir = new File(root, newTitle);

        // 旧目录不存在：可能是还没落过盘的空分类，直接建新目录即可。
        if (!olddir.isDirectory()) {
            return newdir.isDirectory() || newdir.mkdirs();
        }
        // 目标已存在且不是同一个目录（Windows 大小写重命名会指向同一个），不能合并，交给调用方回滚。
        if (newdir.exists() && !sameFile(olddir, newdir)) {
            return false;
        }

        // 先试原子移动，失败再退化成「建目录 + 逐个搬文件 + 删空旧目录」。
        try {
            Files.move(olddir.toPath(), newdir.toPath(), StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (Exception ignored) {
            // 落到下面的降级路径。
        }
        if (olddir.renameTo(newdir)) return true;
        return moveContents(olddir, newdir);
    }

    /** 逐个搬运目录内容，用于 renameTo/ATOMIC_MOVE 都失败时（Windows 句柄、跨卷等）。 */
    private static boolean moveContents(File olddir, File newdir) {
        if (!newdir.isDirectory() && !newdir.mkdirs()) return false;
        File[] files = olddir.listFiles();
        if (files == null) return false;
        boolean allMoved = true;
        for (File f : files) {
            if (!f.isFile()) {
                // 分类目录里原版只放 <id>.json，出现子目录时保守放弃，不做递归删除。
                allMoved = false;
                continue;
            }
            Path target = new File(newdir, f.getName()).toPath();
            try {
                Files.move(f.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                allMoved = false;
            }
        }
        // 只有全部搬完才删旧目录；没搬完就保留，宁可留残留也不丢数据。
        if (allMoved) {
            olddir.delete();
        }
        return allMoved;
    }

    /**
     * 递归删除分类目录。原版 {@code removeCategory} 用 {@code dir.delete()}，
     * 对非空目录必然失败并直接 return，导致「删了分类但目录带着 json 还在」，
     * 下次进世界又被 load 成一个分类。
     */
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

    private static boolean sameFile(File a, File b) {
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException e) {
            return false;
        }
    }
}
