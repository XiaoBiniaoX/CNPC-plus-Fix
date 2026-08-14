package top.cnpcplus.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.CustomNpcs;
import noppes.npcs.client.controllers.MusicController;
import noppes.npcs.roles.JobBard;
import noppes.npcs.roles.JobInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.bard.SongListStore;

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
    private boolean cnpcplus$lastActive = false;

    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$bardAiStep(CallbackInfo ci) {
        JobBard self = (JobBard) (Object) this;
        if (!((JobInterface) self).npc.isClientSide()) return;
        List<String[]> songs = SongListStore.get(self);
        boolean fallback = false;
        if (songs == null || songs.isEmpty()) {
            if (self.song.isEmpty()) {
                return;
            }
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
        boolean mine = c.playing != null && c.playingEntity == self.npc;
        boolean inStartRange = self.npc.level().getEntitiesOfClass(
                Player.class,
                self.npc.getBoundingBox().inflate(self.minRange, self.minRange / 2.0, self.minRange)
        ).contains(player);

        // 原版语义：minRange 只决定是否开始播放，不负责停止已经开始的音乐。
        // 只有开启 hasOffRange 后，玩家离开 maxRange 才停止。
        if (mine && self.hasOffRange && !self.npc.level().getEntitiesOfClass(
                Player.class,
                self.npc.getBoundingBox().inflate(self.maxRange, self.maxRange / 2.0, self.maxRange)
        ).contains(player)) {
            c.stopMusic();
            this.cnpcplus$lastPlay = 0L;
            this.cnpcplus$lastPicked = "";
            return;
        }

        if (mine) {
            if (active) {
                return;
            }
            // 自然播放完成：释放旧实例，允许在起始范围内立即按权重选择下一首。
            c.stopMusic();
            this.cnpcplus$lastPlay = 0L;
            this.cnpcplus$lastPicked = "";
        }

        // 尚未播放时必须进入 minRange 才能开始；离开 minRange 不会终止已在播放的音乐。
        if (!inStartRange) return;

        // 其他 NPC 正在播放：仅本 NPC 更近玩家时才接管
        if (c.playing != null && c.playingEntity != null && c.playingEntity != self.npc) {
            if (!self.npc.closerThan(player, c.playingEntity.distanceTo(player))) return;
        }

        if (active != this.cnpcplus$lastActive) {
            this.cnpcplus$lastActive = active;
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
            ((noppes.npcs.mixin.MusicManagerMixin) Minecraft.getInstance().getMusicManager()).nextSongDelay(12000);
        } catch (Exception ignored) {
        }
    }
}
