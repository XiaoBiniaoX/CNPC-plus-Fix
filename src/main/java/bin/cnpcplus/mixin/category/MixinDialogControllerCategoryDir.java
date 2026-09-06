package bin.cnpcplus.mixin.category;

import bin.cnpcplus.category.CategoryDirFix;
import net.minecraft.core.HolderLookup;
import noppes.npcs.controllers.DialogController;
import noppes.npcs.controllers.data.DialogCategory;
import noppes.npcs.packets.Packets;
import noppes.npcs.packets.client.PacketSyncRemove;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.Map;

/**
 * 修复对话分类重命名后旧目录复活、以及非空分类删不掉的问题。
 * 与任务分类同构，根因见 {@link CategoryDirFix}。对话分类的同步类型是 5，任务是 3。
 */
@Mixin(value = DialogController.class, remap = false)
public abstract class MixinDialogControllerCategoryDir {

    @Shadow
    private native File getDir();

    @Inject(
            method = "saveCategory(Lnet/minecraft/core/HolderLookup$Provider;Lnoppes/npcs/controllers/data/DialogCategory;)V",
            at = @At("RETURN"),
            require = 1
    )
    private void cnpcplus$syncDir(HolderLookup.Provider lookupProvider, DialogCategory category, CallbackInfo ci) {
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

    @Inject(method = "removeCategory(I)V", at = @At("HEAD"), cancellable = true, require = 1)
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
        Packets.sendAll(new PacketSyncRemove(categoryId, 5));
        ci.cancel();
    }

    @Inject(method = "load(Lnet/minecraft/core/HolderLookup$Provider;)V", at = @At("HEAD"), require = 1)
    private void cnpcplus$clearDirNames(HolderLookup.Provider lookupProvider, CallbackInfo ci) {
        CategoryDirFix.forgetAll(this);
    }

    @Inject(method = "load(Lnet/minecraft/core/HolderLookup$Provider;)V", at = @At("RETURN"), require = 1)
    private void cnpcplus$bindIds(HolderLookup.Provider lookupProvider, CallbackInfo ci) {
        DialogController self = (DialogController) (Object) this;
        CategoryDirFix.forgetAll(this);
        for (Map.Entry<Integer, DialogCategory> e : self.categories.entrySet()) {
            CategoryDirFix.remember(this, e.getKey(), e.getValue().title);
        }
    }
}
