package bin.cnpcplus.mixin.bard;

import bin.cnpcplus.bard.SongListStore;
import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.MusicTicker;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.entity.player.EntityPlayer;
import noppes.npcs.CustomNpcs;
import noppes.npcs.client.controllers.MusicController;
import noppes.npcs.roles.JobBard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Rewrite of the bard client-side playback (1.12.2 onLivingUpdate = 1.20.1 aiStep):
 * weighted playlist picking, watchdog (force-switch a song that plays too long),
 * distance rules (min/maxRange, nearest bard wins) and fallback to the vanilla
 * single song when the playlist is empty.
 */
@Mixin(value = JobBard.class, remap = false)
public class MixinJobBardClient {

    @Unique
    private long cnpcplus$lastPlay = 0L;

    @Unique
    private String cnpcplus$lastPicked = "";

    @Unique
    private String cnpcplus$lastSong = "";

    @Inject(method = "onLivingUpdate", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$bardTick(CallbackInfo ci) {
        JobBard self = (JobBard) (Object) this;
        if (!self.npc.isRemote()) return;
        List<String[]> songs = SongListStore.get(self);
        boolean fallback = false;
        if (songs == null || songs.isEmpty()) {
            if (self.song.isEmpty()) return;
            fallback = true;
            songs = new ArrayList<String[]>();
            songs.add(new String[]{self.song, "1"});
        }
        ci.cancel();

        MusicController c = MusicController.Instance;
        SoundHandler sm = Minecraft.getMinecraft().getSoundHandler();
        EntityPlayer player = CustomNpcs.proxy.getPlayer();
        if (player == null) return;

        String current = c.playingResource == null ? "" : c.playingResource.toString();
        boolean active = c.playing != null && sm.isSoundPlaying(c.playing);
        if (!current.isEmpty() && current.equals(this.cnpcplus$lastPicked)) {
            if (active && System.currentTimeMillis() - this.cnpcplus$lastPlay < CnpcPlusConfig.getBardWatchdogSeconds() * 1000L) return;
            if (!active && System.currentTimeMillis() - this.cnpcplus$lastPlay < 500L) return;
            if (active) {
                c.stopMusic();
                this.cnpcplus$lastPlay = 0L;
                this.cnpcplus$lastPicked = "";
            }
        }
        boolean mine = c.playingEntity == self.npc && c.playing != null;
        if (mine && active) {
            if (self.hasOffRange && self.npc.getDistanceSq(player) > (double) (self.maxRange * self.maxRange)) {
                c.stopMusic();
            }
            return;
        }
        if (c.playing != null && c.playingEntity != null && c.playingEntity != self.npc && active) {
            if (self.npc.getDistanceSq(player) > player.getDistanceSq(c.playingEntity)) return;
        }
        if (self.npc.getDistanceSq(player) > (double) (self.minRange * self.minRange)) {
            if (mine) c.stopMusic();
            return;
        }

        String picked = fallback ? self.song : SongListStore.pick(self, this.cnpcplus$lastSong);
        if (picked == null || picked.isEmpty()) return;
        if (self.isStreamer) {
            c.playStreaming(picked, self.npc);
        } else {
            c.playMusic(picked, self.npc);
        }
        this.cnpcplus$lastPicked = picked;
        this.cnpcplus$lastSong = picked;
        this.cnpcplus$lastPlay = System.currentTimeMillis();
        try {
            Field f = MusicTicker.class.getDeclaredField("field_147676_d");
            f.setAccessible(true);
            f.setInt(Minecraft.getMinecraft().getMusicTicker(), 12000);
        } catch (Exception ignored) {
        }
    }
}