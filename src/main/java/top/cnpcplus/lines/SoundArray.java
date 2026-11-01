package top.cnpcplus.lines;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;

/** Editable values are preserved; only valid, nonempty resources participate in playback. */
public final class SoundArray {
    public final List<String> sounds = new ArrayList<>();
    public boolean randomPitch;

    public String first() { return sounds.isEmpty() ? "" : sounds.get(0); }

    public String choose() {
        List<String> playable = sounds.stream().filter(s -> s != null && !s.isBlank()).map(ResourceLocation::tryParse)
                .filter(java.util.Objects::nonNull).map(ResourceLocation::toString).toList();
        return playable.isEmpty() ? "" : playable.get(ThreadLocalRandom.current().nextInt(playable.size()));
    }

    public float pitch() {
        return randomPitch ? 1F + (ThreadLocalRandom.current().nextFloat()
                - ThreadLocalRandom.current().nextFloat()) * 0.2F : 1F;
    }

    public ListTag save() {
        ListTag out = new ListTag();
        sounds.forEach(s -> out.add(StringTag.valueOf(s == null ? "" : s)));
        return out;
    }

    public void read(ListTag list) {
        sounds.clear();
        for (int i = 0; i < list.size(); i++) sounds.add(list.getString(i));
    }

    public void single(String sound) {
        sounds.clear();
        if (sound != null && !sound.isEmpty()) sounds.add(sound);
    }

    public SoundArray copy() {
        SoundArray copy = new SoundArray();
        copy.sounds.addAll(sounds);
        copy.randomPitch = randomPitch;
        return copy;
    }
}
