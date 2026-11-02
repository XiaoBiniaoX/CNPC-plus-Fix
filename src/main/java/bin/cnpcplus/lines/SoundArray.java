package bin.cnpcplus.lines;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

public final class SoundArray {
    public final List<String> sounds = new ArrayList<>();
    public boolean randomPitch;

    public String first() { return sounds.isEmpty() ? "" : sounds.get(0); }
    public String choose() {
        List<String> playable = new ArrayList<>();
        for (String sound : sounds) {
            if (sound != null && sound.matches("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+")) playable.add(sound);
        }
        return playable.isEmpty() ? "" : playable.get(ThreadLocalRandom.current().nextInt(playable.size()));
    }
    public float pitch() {
        return randomPitch ? 1F + (ThreadLocalRandom.current().nextFloat()
                - ThreadLocalRandom.current().nextFloat()) * .2F : 1F;
    }
    public NBTTagList save() {
        NBTTagList list = new NBTTagList();
        for (String sound : sounds) list.appendTag(new NBTTagString(sound == null ? "" : sound));
        return list;
    }
    public void read(NBTTagList list) {
        sounds.clear();
        for (int i = 0; i < list.tagCount(); i++) sounds.add(list.getStringTagAt(i));
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
