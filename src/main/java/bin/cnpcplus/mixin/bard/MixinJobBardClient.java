package bin.cnpcplus.mixin.bard;

import bin.cnpcplus.bard.SongListStore;
import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.CustomNpcs;
import noppes.npcs.client.controllers.MusicController;
import noppes.npcs.mixin.MusicManagerMixin;
import noppes.npcs.roles.JobBard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = JobBard.class, remap = false)
public class MixinJobBardClient {

    @Shadow(remap = false)
    public String song;

    @Unique
    private long cnpcplus$lastPlay = 0L;

    @Unique
    private String cnpcplus$lastPicked = "";

    @Unique
    private String cnpcplus$lastSong = "";

    @Unique
    private boolean cnpcplus$warnedEmpty = false;

    @Unique
    private boolean cnpcplus$lastActive = false;

    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$bardAiStep(CallbackInfo ci) {
        JobBard self = (JobBard) (Object) this;
        if (!self.npc.isClientSide()) return;
        List<String[]> songs = SongListStore.get(self);
        boolean fallback = false;
        if (songs == null || songs.isEmpty()) {
            if (self.song.isEmpty()) return;
            fallback = true;
            songs = new ArrayList<>();
            songs.add(new String[]{self.song, "1"});
        }
        ci.cancel();

        Minecraft mc = Minecraft.getInstance();
        MusicController c = MusicController.Instance;
        SoundManager sm = mc.getSoundManager();
        Player player = CustomNpcs.proxy.getPlayer();
        if (player == null) return;

        boolean active = c.playing != null && sm.isActive(c.playing);
        boolean mine = c.playingEntity == self.npc && c.playing != null;
        double minRange = self.minRange;
        double maxRange = self.maxRange;
        boolean inStartRange = self.npc.distanceToSqr(player) <= minRange * minRange;
        boolean inStopRange = self.npc.distanceToSqr(player) <= maxRange * maxRange;
        if (mine && active && self.hasOffRange && !inStopRange) {
            c.stopMusic();
            this.cnpcplus$lastPlay = 0L;
            this.cnpcplus$lastPicked = "";
            return;
        }
        if (mine) {
            if (active) {
                if (System.currentTimeMillis() - this.cnpcplus$lastPlay < CnpcPlusConfig.BARD_WATCHDOG_SECONDS.get() * 1000L) return;
                c.stopMusic();
            }
            this.cnpcplus$lastPlay = 0L;
            this.cnpcplus$lastPicked = "";
        }
        if (!inStartRange) return;
        if (c.playing != null && c.playingEntity != null && c.playingEntity != self.npc) {
            if (!self.npc.closerThan(player, c.playingEntity.distanceTo(player))) return;
        }

        String picked = fallback ? self.song : SongListStore.pick(self, this.cnpcplus$lastSong);
        if (picked == null || picked.isEmpty()) return;
        if (self.isStreamer) {
            c.playStreaming(picked, self.npc, false);
        } else {
            c.playMusic(picked, self.npc, false);
        }
        this.cnpcplus$lastPicked = picked;
        this.cnpcplus$lastSong = picked;
        this.cnpcplus$lastPlay = System.currentTimeMillis();
        try {
            ((MusicManagerMixin) Minecraft.getInstance().getMusicManager()).nextSongDelay(12000);
        } catch (Exception ignored) {
        }
    }
}
