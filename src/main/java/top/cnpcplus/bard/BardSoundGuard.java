package top.cnpcplus.bard;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import noppes.npcs.client.controllers.MusicController;
import noppes.npcs.roles.JobBard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Keeps malformed or missing bard sounds from reaching CNPC's unsafe playback methods. */
@OnlyIn(Dist.CLIENT)
public final class BardSoundGuard {
    private static final Logger LOGGER = LoggerFactory.getLogger("cnpcplus");
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private BardSoundGuard() {
    }

    public static ResourceLocation parseLocation(String song) {
        if (song == null || song.isBlank() || !song.equals(song.trim())) return null;
        return ResourceLocation.tryParse(song);
    }

    /**
     * Checks the sound event and the selected event's real .ogg resource.
     * Minecraft filters missing FILE entries while loading sounds.json, so a zero-weight event is unavailable.
     */
    public static boolean isPlayable(String song) {
        ResourceLocation id = parseLocation(song);
        if (id == null) return reject(song, "invalid sound id");

        try {
            Minecraft minecraft = Minecraft.getInstance();
            SoundManager manager = minecraft.getSoundManager();
            WeighedSoundEvents event = manager.getSoundEvent(id);
            if (event == null) return reject(song, "unknown sound event");
            if (event == SoundManager.INTENTIONALLY_EMPTY_SOUND_EVENT || event.getWeight() <= 0) {
                return reject(song, "sound event has no available audio file");
            }

            Sound sound = event.getSound(RandomSource.create(0L));
            if (sound == SoundManager.EMPTY_SOUND || sound == SoundManager.INTENTIONALLY_EMPTY_SOUND) {
                return reject(song, "sound event resolved to no audio file");
            }
            if (minecraft.getResourceManager().getResource(sound.getPath()).isEmpty()) {
                return reject(song, "audio file " + sound.getPath() + " is missing");
            }
            return true;
        } catch (Throwable error) {
            return reject(song, "sound validation failed: " + error.getClass().getSimpleName());
        }
    }

    /** Final race-safe boundary around CNPC MusicController. */
    public static boolean play(MusicController controller, JobBard bard, String song) {
        if (controller == null || bard == null || !isPlayable(song)) return false;
        try {
            if (bard.isStreamer) {
                controller.playStreaming(song, bard.npc, false);
            } else {
                controller.playMusic(song, bard.npc, false);
            }
            return true;
        } catch (Throwable error) {
            failed(controller, song, error);
            return false;
        }
    }

    public static void failed(MusicController controller, String song, Throwable error) {
        warnOnce(song, "playback failed: " + error.getClass().getSimpleName());
        if (controller == null) return;
        try {
            controller.stopMusic();
        } catch (Throwable ignored) {
            controller.playing = null;
            controller.playingResource = null;
            controller.playingEntity = null;
        }
    }

    private static boolean reject(String song, String reason) {
        warnOnce(song, reason);
        return false;
    }

    private static void warnOnce(String song, String reason) {
        String key = song == null ? "<null>" : song;
        if (WARNED.add(key)) {
            LOGGER.warn("[bard] Skipping unavailable BGM '{}': {}", key, reason);
        }
    }
}
