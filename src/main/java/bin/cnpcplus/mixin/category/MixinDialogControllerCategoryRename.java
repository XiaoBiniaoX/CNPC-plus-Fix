package bin.cnpcplus.mixin.category;

import bin.cnpcplus.category.CategoryRenameFix;
import noppes.npcs.controllers.DialogController;
import noppes.npcs.controllers.data.DialogCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复「全局对话分类重命名后，重进游戏旧目录被复原」。
 *
 * {@code DialogController.saveCategory}（反编译第 218-251 行）与
 * {@code QuestController.saveCategory} 结构逐行对应，缺陷完全同源：
 *   第 228 行 {@code if (newdir.exists())         return;}
 *   第 231 行 {@code if (!olddir.renameTo(newdir)) return;}
 * 两个 return 都排在第 249 行 {@code categories.put(...)} 与
 * 第 250 行 {@code Server.sendToAll(SYNC_UPDATE, ...)} 之前；
 * 而第 156 行 {@code loadCategoriesOld} 又会无条件 {@code saveCategory}，
 * 把旧标题重新落盘、重建旧目录。
 *
 * 根因全文与取舍见 {@link CategoryRenameFix} 的类注释，
 * 修法与 {@link MixinQuestControllerCategoryRename} 完全一致：
 * HEAD 前置改名并统一 title，使原版重命名分支不再进入，
 * 从而绕开两个提前 return，保证 put 与 SYNC_UPDATE 一定执行。
 *
 * DialogController 是服务端类，本混入注册在 common 侧，不引用客户端类。
 */
@Mixin(value = DialogController.class, remap = false)
public class MixinDialogControllerCategoryRename {

    @Inject(method = "saveCategory", at = @At("HEAD"), remap = false, require = 1)
    private void cnpcplus$renameBeforeSave(DialogCategory category, CallbackInfo ci) {
        DialogController self = (DialogController) (Object) this;
        if (category == null || self.categories == null) {
            return;
        }
        DialogCategory current = self.categories.get(category.id);
        // 新建分类走原版 else 分支，本修复只针对已存在分类的改名。
        if (current == null || current.title == null) {
            return;
        }
        String cleaned = CategoryRenameFix.clean(category.title);
        if (cleaned == null || cleaned.equals(current.title)) {
            return;
        }
        // 与原版第 223-225 行同样的重名去重。
        String target = cleaned;
        while (cnpcplus$nameTaken(self, category.id, target)) {
            target = target + "_";
        }
        if (CategoryRenameFix.renameDir(CategoryRenameFix.dialogDir(), current.title, target)) {
            category.title = target;
            current.title = target;
        } else {
            category.title = current.title;
        }
    }

    /** 与原版 {@code containsCategoryName} 同义：同名但不同 id 即视为占用。 */
    private boolean cnpcplus$nameTaken(DialogController self, int selfId, String title) {
        for (DialogCategory cat : self.categories.values()) {
            if (cat != null && cat.id != selfId && title.equalsIgnoreCase(cat.title)) {
                return true;
            }
        }
        return false;
    }
}
