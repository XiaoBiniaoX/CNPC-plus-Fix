package bin.cnpcplus.mixin.playerdata;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.data.PlayerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;

/**
 * loadPlayerData 回退读取 _new 文件，防止 delete+renameTo 失败导致数据丢失。
 *
 * <p>CNPC save 写 _new → 删旧 → renameTo，若 delete 成功但 renameTo 失败，
 * 旧文件已删、新文件留在 _new，loadPlayerData 只读 .json 返回空 → 数据全丢。
 * 此处 HEAD 注入：.json 不存在时回退读 .json_new。
 */
@Mixin(value = PlayerData.class, remap = false)
public class MixinPlayerDataAtomicSave {

    @Inject(method = "loadPlayerData", at = @At("HEAD"), cancellable = true, remap = false)
    private static void cnpcplus$fallbackToNewFile(String player, CallbackInfoReturnable<CompoundTag> cir) {
        String filename = (player == null || player.isEmpty()) ? "noplayername" : player;
        File saveDir = noppes.npcs.CustomNpcs.getLevelSaveDirectory("playerdata");
        File json = new File(saveDir, filename + ".json");
        File jsonNew = new File(saveDir, filename + ".json_new");
        // .json 存在则走原逻辑；不存在但 _new 存在则回退读 _new
        if (!json.exists() && jsonNew.exists()) {
            try {
                cir.setReturnValue(noppes.npcs.util.NBTJsonUtil.LoadFile(jsonNew));
            } catch (Exception ignored) {
            }
        }
    }
}
