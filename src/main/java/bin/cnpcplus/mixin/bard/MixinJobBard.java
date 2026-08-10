package bin.cnpcplus.mixin.bard;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import noppes.npcs.roles.JobBard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import bin.cnpcplus.bard.SongListStore;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = JobBard.class, remap = false)
public class MixinJobBard {

    @Shadow(remap = false)
    public String song;

    @Inject(method = "load", at = @At("RETURN"))
    private void cnpcplus$loadSongs(CompoundTag tag, CallbackInfo ci) {
        List<String[]> songs = new ArrayList<>();
        ListTag list = tag.getList("BardSongs", 10);
        for (Tag t : list) {
            CompoundTag e = (CompoundTag) t;
            String s = e.getString("song");
            if (s.isEmpty()) continue;
            songs.add(new String[]{s, String.valueOf(e.getInt("weight"))});
        }
        SongListStore.set((JobBard) (Object) this, songs);
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void cnpcplus$saveSongs(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        List<String[]> songs = SongListStore.get((JobBard) (Object) this);
        if (songs == null || songs.isEmpty()) {
            return;
        }
        ListTag list = new ListTag();
        for (String[] e : songs) {
            CompoundTag t = new CompoundTag();
            t.putString("song", e[0]);
            t.putInt("weight", SongListStore.parseWeight(e[1]));
            list.add(t);
        }
        tag.put("BardSongs", list);
    }
}
