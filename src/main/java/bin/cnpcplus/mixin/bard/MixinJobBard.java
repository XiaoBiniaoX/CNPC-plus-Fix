package bin.cnpcplus.mixin.bard;

import bin.cnpcplus.bard.BardLoopStore;
import bin.cnpcplus.bard.SongListStore;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.roles.JobBard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Persist the multi-song playlist via JobBard's own NBT pipeline:
 * "BardSongs" = NBTTagList of {song, weight}. No custom network packets.
 */
@Mixin(value = JobBard.class, remap = false)
public class MixinJobBard {

    @Inject(method = "readFromNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$loadSongs(NBTTagCompound tag, CallbackInfo ci) {
        List<String[]> songs = new ArrayList<String[]>();
        NBTTagList list = tag.getTagList("BardSongs", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            String s = e.getString("song");
            if (s.isEmpty()) continue;
            songs.add(new String[]{s, String.valueOf(e.getInteger("weight"))});
        }
        SongListStore.set((JobBard) (Object) this, songs);
        // 循环播放开关。1.12.2 原版没有该字段，键名沿用高版本的 BardLoops。
        BardLoopStore.set((JobBard) (Object) this, tag.getBoolean(BardLoopStore.NBT_KEY));
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$saveSongs(NBTTagCompound tag, CallbackInfoReturnable<NBTTagCompound> cir) {
        // 循环开关先写：它与歌单是否为空无关，不能被下面的 early return 吞掉。
        if (BardLoopStore.isLooping((JobBard) (Object) this)) {
            tag.setBoolean(BardLoopStore.NBT_KEY, true);
        }
        List<String[]> songs = SongListStore.get((JobBard) (Object) this);
        if (songs == null || songs.isEmpty()) return;
        NBTTagList list = new NBTTagList();
        for (String[] e : songs) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("song", e[0]);
            t.setInteger("weight", SongListStore.parseWeight(e[1]));
            list.appendTag(t);
        }
        tag.setTag("BardSongs", list);
    }
}