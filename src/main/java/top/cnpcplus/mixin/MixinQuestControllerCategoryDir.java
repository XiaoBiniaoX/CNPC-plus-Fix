package top.cnpcplus.mixin;

import noppes.npcs.controllers.QuestController;
import noppes.npcs.controllers.data.QuestCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.category.CategoryDirFix;

import java.io.File;

/**
 * 修复全局任务分类重命名后「旧目录连内容一起复原、新目录只有部分内容」。
 *
 * <p>原版分类目录名就是分类名、id 不落盘，重启时反过来用目录名当分类名。
 * 而 {@code saveCategory} 有三条路会让内存 title 与磁盘目录名发散：
 * ①单人/开房时 GUI 改的就是服务端那个 QuestCategory 对象，导致
 * {@code currentCategory.title.equals(category.title)} 恒真、重命名分支整段被跳过；
 * ②{@code newdir.exists()} 直接 return；③{@code renameTo} 失败直接 return。
 * 发散之后 {@code saveQuest} 用新名 mkdirs，只把改名后又编辑过的条目写进新目录。
 *
 * <p>这里在 RETURN 处把磁盘目录名对齐到 title；对不齐就把 title 回滚成真实目录名，
 * 保证「内存 title == 磁盘目录名」这条不变量永不破。
 */
@Mixin(value = QuestController.class, remap = false)
public abstract class MixinQuestControllerCategoryDir {

    @Shadow(remap = false)
    private native File getDir();

    @Inject(method = "saveCategory", at = @At("RETURN"), remap = false)
    private void cnpcplus$syncDir(QuestCategory category, CallbackInfo ci) {
        if (category == null) return;
        String finalTitle = CategoryDirFix.syncDir(this, this.getDir(), category.id, category.title);
        if (!finalTitle.equals(category.title)) {
            // 目录搬不动：回滚内存，避免旧目录带着数据在下次进世界复活。
            category.title = finalTitle;
            QuestCategory inMap = ((QuestController) (Object) this).categories.get(category.id);
            if (inMap != null && inMap != category) {
                inMap.title = finalTitle;
            }
        }
    }

    /** 原版用 dir.delete()，非空目录必然失败并直接 return，删了分类目录却还在。 */
    @Inject(method = "removeCategory", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$removeCategory(int categoryId, CallbackInfo ci) {
        QuestController self = (QuestController) (Object) this;
        QuestCategory cat = self.categories.get(categoryId);
        if (cat == null) return;
        String dirName = CategoryDirFix.remembered(this, categoryId);
        if (dirName == null) dirName = cat.title;
        if (!CategoryDirFix.deleteDir(new File(this.getDir(), dirName))) return;

        for (int questId : cat.quests.keySet()) {
            self.quests.remove(questId);
        }
        self.categories.remove(categoryId);
        CategoryDirFix.forget(this, categoryId);
        noppes.npcs.packets.Packets.sendAll(new noppes.npcs.packets.client.PacketSyncRemove(categoryId, 3));
        ci.cancel();
    }

    /** 换存档重新 load 时清掉上一份目录名登记。 */
    @Inject(method = "load", at = @At("HEAD"), remap = false)
    private void cnpcplus$clearDirNames(CallbackInfo ci) {
        CategoryDirFix.forgetAll(this);
    }

    /**
     * load 结束后把最终分配到的 id 与目录名绑定。
     * 此时 title 一定等于目录名（loadCategoryDir 就是用目录名赋的 title）。
     */
    @Inject(method = "load", at = @At("RETURN"), remap = false)
    private void cnpcplus$bindIds(CallbackInfo ci) {
        QuestController self = (QuestController) (Object) this;
        CategoryDirFix.forgetAll(this);
        for (java.util.Map.Entry<Integer, QuestCategory> e : self.categories.entrySet()) {
            CategoryDirFix.remember(this, e.getKey(), e.getValue().title);
        }
    }
}
