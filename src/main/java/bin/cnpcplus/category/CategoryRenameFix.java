package bin.cnpcplus.category;

import noppes.npcs.CustomNpcs;
import noppes.npcs.NoppesStringUtils;

import java.io.File;

/**
 * 全局任务 / 对话分类重命名的公共修复逻辑。
 *
 * 现象（哈基彬来自 1.20.1 / 1.21.1 的上报，经核实 1.12.2 同样存在）：
 * 全局 NPC 任务与对话分类重命名后，再次进入游戏会把改名前的目录复原
 * （连内容一起），而新命名的目录里只剩一部分内容
 * （疑似只有上次改动过的那些）。
 *
 * 根因（1.12.2 反编译 + 字节码双重实证，两个 Controller 结构完全一致）：
 *  - {@code QuestController.saveCategory}（反编译第 184-217 行）与
 *    {@code DialogController.saveCategory}（第 218-251 行）在重命名分支里有
 *    两个**提前 return**：
 *      第 194/228 行  {@code if (newdir.exists())          return;}
 *      第 197/231 行  {@code if (!olddir.renameTo(newdir))  return;}
 *  - 字节码核实这两个 return 的位置（QuestController.saveCategory）：
 *      offset 132  File.exists    ordinal 0 → 135 ifeq → 138 return
 *      offset 142  File.renameTo  ordinal 0 → 145 ifne → 148 return
 *      offset 240  File.exists    ordinal 1 （新建分支的 mkdirs 判断）
 *      offset 263  categories.put(...)
 *      offset 298  Server.sendToAll(SYNC_UPDATE, ...)
 *    即两个 return 都排在 put 与同步发包**之前**。
 *  - 于是重命名一旦失败（Windows 下目录句柄被占用是常态，或同名目录已存在），
 *    内存里的 categories 不更新、客户端收不到 SYNC_UPDATE，
 *    但此时传入对象的 title 已经是新名字了（第 185/219 行 cleanFileName
 *    之后、第 190/224 行的去重循环里都在写它）。
 *  - 再次进入游戏时 {@code loadCategoriesOld}（Quest 第 147 行 / Dialog 第 156 行）
 *    对每个分类**无条件**调用 {@code saveCategory}，把仍是旧标题的分类重新落盘，
 *    旧目录就这样被重建出来。
 *  - 「新目录只有部分内容」同源：{@code saveQuest}（第 239-256 行）按单个任务
 *    写文件，只有本次实际改动过的条目才会写进新目录，其余仍留在旧目录。
 *
 * 修法（最小且不改存档格式）：在 saveCategory 的 HEAD 先把目录改名做掉，
 * 并让传入对象与内存旧对象的 title 达成一致，使原版那段重命名分支整体不再进入
 * （它的前提是两者标题不相等），从而绕开两个提前 return，保证方法一定跑到
 * put 与 SYNC_UPDATE。改不动目录时则把 title 回退成旧名字，
 * 绝不让「字段已改名、磁盘没改名」的错位留到下次启动。
 *
 * 不做的事（避免画蛇添足）：
 *  - 不重试 renameTo、不做 copy+delete 兜底：那是改存档搬运策略，风险高于收益。
 *  - 不改 loadCategoriesOld 的无条件 saveCategory：它同时承担旧格式迁移职责。
 *  - 不动 removeCategory、saveQuest、saveDialog 的任何逻辑。
 *
 * 本类是普通工具类，**不在 mixin 包内**，避免「mixin 包内的非 mixin 类被目标类
 * 直接引用」导致的 {@code IllegalClassLoadError}（阶段 22 已踩过并记入 findings）。
 * 全部为服务端可达逻辑，不引用任何客户端类。
 */
public final class CategoryRenameFix {

    private CategoryRenameFix() {
    }

    /** 任务分类根目录，与原版私有 {@code QuestController.getDir()} 一致。 */
    public static File questDir() {
        return new File(CustomNpcs.getWorldSaveDirectory(), "quests");
    }

    /** 对话分类根目录，与原版私有 {@code DialogController.getDir()} 一致。 */
    public static File dialogDir() {
        return new File(CustomNpcs.getWorldSaveDirectory(), "dialogs");
    }

    /** 与原版第 185/219 行同样的文件名清洗。 */
    public static String clean(String title) {
        if (title == null) {
            return null;
        }
        String cleaned = NoppesStringUtils.cleanFileName(title);
        // 清洗后为空白说明这个名字无法作为目录名，交回原版按它自己的规则处理。
        if (cleaned == null || cleaned.trim().isEmpty()) {
            return null;
        }
        return cleaned;
    }

    /**
     * 执行目录改名。
     *
     * @return true 表示磁盘上已是新名字（含「旧目录本来不存在」这种情形 ——
     *         分类还没落过盘，后续 saveQuest / saveDialog 会用新标题建目录，
     *         此时若按失败回退，反而会把用户刚输入的名字改掉）。
     */
    public static boolean renameDir(File root, String oldTitle, String newTitle) {
        if (root == null || oldTitle == null || newTitle == null) {
            return false;
        }
        File olddir = new File(root, oldTitle);
        File newdir = new File(root, newTitle);
        if (!olddir.exists()) {
            return true;
        }
        if (newdir.exists()) {
            return false;
        }
        return olddir.renameTo(newdir);
    }
}
