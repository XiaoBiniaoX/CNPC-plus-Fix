package bin.cnpcplus.mixin.clone;

import noppes.npcs.controllers.ServerCloneController;
import noppes.npcs.shared.common.util.LogWriter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * 官方 saveClone 把 xxx.json_new 重命名为 xxx.json 时忽略 renameTo 的返回值。
 * Windows 上目标文件被占用或杀软扫描时 renameTo 会静默失败：新数据滞留 _new、
 * 旧 .json 仍在，克隆覆盖保存表现为「只有没克隆过时才生效」。
 * 这里重定向 renameTo：失败先删目标重试，仍失败则直接拷贝内容覆盖。
 */
@Mixin(value = ServerCloneController.class, remap = false)
public class MixinServerCloneControllerSaveClone {

    @Redirect(
            method = "saveClone",
            at = @At(value = "INVOKE", target = "Ljava/io/File;renameTo(Ljava/io/File;)Z", remap = false)
    )
    private boolean cnpcplus$robustRename(File from, File to) {
        if (from.renameTo(to)) {
            return true;
        }
        //noinspection ResultOfMethodCallIgnored
        to.delete();
        if (from.renameTo(to)) {
            return true;
        }
        try {
            Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
            //noinspection ResultOfMethodCallIgnored
            from.delete();
            return true;
        } catch (Exception e) {
            LogWriter.error("Clone save fallback copy failed: " + to.getAbsolutePath(), e);
            return false;
        }
    }
}
