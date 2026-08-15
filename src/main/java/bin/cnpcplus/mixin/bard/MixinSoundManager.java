package bin.cnpcplus.mixin.bard;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.MathHelper;
import noppes.npcs.client.controllers.MusicController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SoundManager.class)
public class MixinSoundManager {
    @Redirect(method = {"playSound", "setVolume", "updateAllSounds"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/audio/SoundManager;getClampedVolume(Lnet/minecraft/client/audio/ISound;)F"))
    private float cnpcplus$bardVolume(SoundManager self, ISound sound) {
        MusicController controller = MusicController.Instance;
        if (controller != null && sound == controller.playing) {
            return MathHelper.clamp(sound.getVolume() * CnpcPlusConfig.getBardVolume(), 0.0F, 1.0F);
        }
        SoundCategory category = sound.getCategory();
        float categoryVolume = category == null || category == SoundCategory.MASTER
                ? 1.0F : Minecraft.getMinecraft().gameSettings.getSoundLevel(category);
        return MathHelper.clamp(sound.getVolume() * categoryVolume, 0.0F, 1.0F);
    }
}
