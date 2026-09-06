package bin.cnpcplus.mixin.category;

import bin.cnpcplus.category.CategoryRenameFix;
import noppes.npcs.controllers.QuestController;
import noppes.npcs.controllers.data.QuestCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复「全局任务分类重命名后，重进游戏旧目录被复原」。
 *
 * 根因、字节码证据与整体取舍见 {@link CategoryRenameFix} 的类注释。
 *
 * 本混入只做一件事：在 {@code saveCategory} 的 HEAD 把目录改名先做掉，
 * 并让「传入对象」与「内存旧对象」的 title 达成一致。
 *
 * 这样原版第 188 行的 {@code !currentCategory.title.equals(category.title)}
 * 判断为 false，整段重命名分支被跳过，它内部那两个会提前 return 的判断
 * （newdir.exists / renameTo 失败）自然都不会执行，方法必定走到
 * {@code categories.put(...)} 与 {@code Server.sendToAll(SYNC_UPDATE, ...)}。
 *
 * 改名成功 → 内存、客户端、磁盘三者都是新名字。
 * 改名失败 → 三者都保持旧名字，不再出现「字段已改名、磁盘没改名、
 *            下次启动 loadCategoriesOld 又把旧目录写回来」的错位。
 *
 * 只注入 HEAD，不改原版任何一行既有逻辑；不新增 NBT、不新增网络包。
 * QuestController 是服务端类，本混入注册在 common 侧，不引用客户端类。
 */
@Mixin(value = QuestController.class, remap = false)
public class MixinQuestControllerCategoryRename {

    @Inject(method = "saveCategory", at = @At("HEAD"), remap = false, require = 1)
    private void cnpcplus$renameBeforeSave(QuestCategory category, CallbackInfo ci) {
        QuestController self = (QuestController) (Object) this;
        if (category == null || self.categories == null) {
            return;
        }
        QuestCategory current = self.categories.get(category.id);
        // 新建分类走原版 else 分支，本修复只针对已存在分类的改名。
        if (current == null || current.title == null) {
            return;
        }
        String cleaned = CategoryRenameFix.clean(category.title);
        if (cleaned == null || cleaned.equals(current.title)) {
            return;
        }
        // 与原版第 189-191 行同样的重名去重，避免撞上别的分类目录。
        String target = cleaned;
        while (cnpcplus$nameTaken(self, category.id, target)) {
            target = target + "_";
        }
        if (CategoryRenameFix.renameDir(CategoryRenameFix.questDir(), current.title, target)) {
            category.title = target;
            current.title = target;
        } else {
            category.title = current.title;
        }
    }

    /** 与原版 {@code containsCategoryName} 同义：同名但不同 id 即视为占用。 */
    private boolean cnpcplus$nameTaken(QuestController self, int selfId, String title) {
        for (QuestCategory cat : self.categories.values()) {
            if (cat != null && cat.id != selfId && title.equalsIgnoreCase(cat.title)) {
                return true;
            }
        }
        return false;
    }
}
