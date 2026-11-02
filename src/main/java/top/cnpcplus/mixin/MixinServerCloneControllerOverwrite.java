package top.cnpcplus.mixin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.ServerCloneController;
import noppes.npcs.util.NBTJsonUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.clone.ClonePreviewData;

/** Replace delete-then-rename: disk and cache change only after a verified replacement succeeds. */
@Mixin(value = ServerCloneController.class, remap = false)
public abstract class MixinServerCloneControllerOverwrite {
    @Shadow private Map<String, CompoundTag> cloneCache;
    @Shadow public long lastLoaded;
    @Shadow public abstract File getDir();
    @Shadow public abstract void saveClone(int tab, String name, CompoundTag compound);
    @Shadow private Map<Integer, Map<String, CompoundTag>> loadOldClones(File file) throws Exception { throw new AssertionError(); }

    @Inject(method = "loadClones", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$migrateWithoutDeletingSource(CallbackInfo ci) {
        ci.cancel();
        File source = new File(getDir(), "../clonednpcs.dat");
        if (!source.isFile()) return;
        try {
            var clones = loadOldClones(source);
            for (var tab : clones.entrySet()) for (var entry : tab.getValue().entrySet()) {
                // A retry must not replace clones already saved or subsequently edited.
                if (new File(new File(getDir(), tab.getKey().toString()), entry.getKey() + ".json").isFile()) continue;
                long before = lastLoaded;
                saveClone(tab.getKey(), entry.getKey(), entry.getValue());
                if (lastLoaded == before) return; // verifiedSave already reports failure; the legacy source survives.
            }
            File archive = new File(source.getParentFile(), "clonednpcs.dat_old");
            if (archive.exists()) archive = new File(source.getParentFile(), "clonednpcs-" + java.util.UUID.randomUUID() + ".dat_old");
            Files.move(source.toPath(), archive.toPath());
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("cnpcplus").error("[clone] 旧克隆迁移未完成；原文件保留", e);
        }
    }

    @Inject(method = "saveClone", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$verifiedSave(int tab, String name, CompoundTag compound, CallbackInfo ci) {
        ci.cancel();
        try {
            if (!ClonePreviewData.validSelection(tab, name) || compound == null) throw new IllegalArgumentException("invalid clone name/tab/data");
            var root = getDir().toPath().toRealPath();
            var directory = Files.createDirectories(root.resolve(Integer.toString(tab))).toRealPath();
            if (!directory.getParent().equals(root)) throw new java.io.IOException("clone tab outside directory");
            var file = directory.resolve(name + ".json");
            var temp = Files.createTempFile(directory, "cnpcplus-clone-", ".tmp");
            CompoundTag saved = compound.copy();
            try {
                NBTJsonUtil.SaveFile(temp.toFile(), saved);
                if (!ClonePreviewData.readSaved(temp.toFile()).equals(saved)) throw new java.io.IOException("clone verification failed");
                try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (java.nio.file.AtomicMoveNotSupportedException unsupported) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
            } finally {
                Files.deleteIfExists(temp);
            }
            cloneCache.put(tab + "|" + name, saved);
            lastLoaded = Math.max(System.currentTimeMillis(), lastLoaded + 1);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("cnpcplus").error("[clone] 克隆保存失败；旧文件和缓存保留", e);
        }
    }

    @Redirect(method = "getCloneData", at = @At(value = "INVOKE", target = "Lnoppes/npcs/util/NBTJsonUtil;LoadFile(Ljava/io/File;)Lnet/minecraft/nbt/CompoundTag;"), require = 1)
    private CompoundTag cnpcplus$readWithoutTruncation(File file) throws java.io.IOException {
        return ClonePreviewData.readSaved(file);
    }
}
