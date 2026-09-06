package bin.cnpcplus.mixin.category;

import bin.cnpcplus.category.CategoryDirFix;
import net.minecraft.core.HolderLookup;
import noppes.npcs.controllers.QuestController;
import noppes.npcs.controllers.data.QuestCategory;
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
 * 修复任务分类重命名后旧目录复活、以及非空分类删不掉的问题。
 * 根因与整体思路见 {@link CategoryDirFix}。
 */
@Mixin(value = QuestController.class, remap = false)
public abstract class MixinQuestControllerCategoryDir {

    @Shadow
    private native File getDir();

    /** 保存分类后把磁盘目录对齐到 title；对不齐则回滚内存 title，保持两者恒等。 */
    @Inject(
            method = "saveCategory(Lnet/minecraft/core/HolderLookup$Provider;Lnoppes/npcs/controllers/data/QuestCategory;)V",
            at = @At("RETURN"),
            require = 1
    )
    private void cnpcplus$syncDir(HolderLookup.Provider lookupProvider, QuestCategory category, CallbackInfo ci) {
        if (category == null) return;
        String finalTitle = CategoryDirFix.syncDir(this, this.getDir(), category.id, category.title);
        if (!finalTitle.equals(category.title)) {
            category.title = finalTitle;
            QuestCategory inMap = ((QuestController) (Object) this).categories.get(category.id);
            if (inMap != null && inMap != category) {
                inMap.title = finalTitle;
            }
        }
    }

    /** 原版用 dir.delete()，非空目录必然失败并直接 return，结果分类删不掉、下次进世界又被扫回来。 */
    @Inject(method = "removeCategory(I)V", at = @At("HEAD"), cancellable = true, require = 1)
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
        Packets.sendAll(new PacketSyncRemove(categoryId, 3));
        ci.cancel();
    }

    @Inject(method = "load(Lnet/minecraft/core/HolderLookup$Provider;)V", at = @At("HEAD"), require = 1)
    private void cnpcplus$clearDirNames(HolderLookup.Provider lookupProvider, CallbackInfo ci) {
        CategoryDirFix.forgetAll(this);
    }

    /**
     * load 结束时 title 必然等于目录名（loadCategoryDir 就是用目录名赋的 title），
     * 这是唯一可信的锚点，用它给本次分配到的 id 建立目录名绑定。
     */
    @Inject(method = "load(Lnet/minecraft/core/HolderLookup$Provider;)V", at = @At("RETURN"), require = 1)
    private void cnpcplus$bindIds(HolderLookup.Provider lookupProvider, CallbackInfo ci) {
        QuestController self = (QuestController) (Object) this;
        CategoryDirFix.forgetAll(this);
        for (Map.Entry<Integer, QuestCategory> e : self.categories.entrySet()) {
            CategoryDirFix.remember(this, e.getKey(), e.getValue().title);
        }
    }
}
