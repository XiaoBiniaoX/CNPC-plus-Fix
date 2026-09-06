package top.cnpcplus.mixin;

import noppes.npcs.controllers.DialogController;
import noppes.npcs.controllers.data.DialogCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.category.CategoryDirFix;

import java.io.File;

/**
 * 与 {@link MixinQuestControllerCategoryDir} 完全同构，修对话分类的同一个缺陷：
 * {@code DialogController.saveCategory} 重命名分支同样会被跳过或静默失败，
 * 导致旧目录连内容一起在下次进世界复原、新目录只有改名后编辑过的条目。
 */
@Mixin(value = DialogController.class, remap = false)
public abstract class MixinDialogControllerCategoryDir {

    @Shadow(remap = false)
    private native File getDir();

    @Inject(method = "saveCategory", at = @At("RETURN"), remap = false)
    private void cnpcplus$syncDir(DialogCategory category, CallbackInfo ci) {
        if (category == null) return;
        String finalTitle = CategoryDirFix.syncDir(this, this.getDir(), category.id, category.title);
        if (!finalTitle.equals(category.title)) {
            category.title = finalTitle;
            DialogCategory inMap = ((DialogController) (Object) this).categories.get(category.id);
            if (inMap != null && inMap != category) {
                inMap.title = finalTitle;
            }
        }
    }

    /** 原版用 dir.delete()，非空目录必然失败并直接 return，删了分类目录却还在。 */
    @Inject(method = "removeCategory", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$removeCategory(int categoryId, CallbackInfo ci) {
        DialogController self = (DialogController) (Object) this;
        DialogCategory cat = self.categories.get(categoryId);
        if (cat == null) return;
        String dirName = CategoryDirFix.remembered(this, categoryId);
        if (dirName == null) dirName = cat.title;
        if (!CategoryDirFix.deleteDir(new File(this.getDir(), dirName))) return;

        for (int dialogId : cat.dialogs.keySet()) {
            self.dialogs.remove(dialogId);
        }
        self.categories.remove(categoryId);
        CategoryDirFix.forget(this, categoryId);
        noppes.npcs.packets.Packets.sendAll(new noppes.npcs.packets.client.PacketSyncRemove(categoryId, 5));
        ci.cancel();
    }

    @Inject(method = "load", at = @At("HEAD"), remap = false)
    private void cnpcplus$clearDirNames(CallbackInfo ci) {
        CategoryDirFix.forgetAll(this);
    }

    @Inject(method = "load", at = @At("RETURN"), remap = false)
    private void cnpcplus$bindIds(CallbackInfo ci) {
        DialogController self = (DialogController) (Object) this;
        CategoryDirFix.forgetAll(this);
        for (java.util.Map.Entry<Integer, DialogCategory> e : self.categories.entrySet()) {
            CategoryDirFix.remember(this, e.getKey(), e.getValue().title);
        }
    }
}
