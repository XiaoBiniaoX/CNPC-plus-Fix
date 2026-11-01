package top.cnpcplus.lines;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import noppes.npcs.entity.data.DataAdvanced;

public final class NpcSoundArrays {
    public static final String KEY = "CNPCPlusSoundArrays";
    private static final Map<DataAdvanced, NpcSoundArrays> DATA = new WeakHashMap<>();
    public final SoundArray[] slots = new SoundArray[5];

    private NpcSoundArrays(DataAdvanced advanced) {
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new SoundArray();
            slots[i].single(advanced.getSound(i));
            // Old footsteps always used pitch=1; the other four inherited DisablePitch.
            slots[i].randomPitch = i != 4 && !advanced.disablePitch;
        }
    }

    public static synchronized NpcSoundArrays get(DataAdvanced advanced) {
        NpcSoundArrays value = DATA.get(advanced);
        if (value == null) {
            value = new NpcSoundArrays(advanced);
            DATA.put(advanced, value);
        }
        return value;
    }

    public static synchronized NpcSoundArrays peek(DataAdvanced advanced) { return DATA.get(advanced); }

    public static synchronized void read(DataAdvanced advanced, CompoundTag tag) {
        DATA.remove(advanced);
        NpcSoundArrays value = get(advanced);
        ListTag list = tag.getList(KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            int slot = item.getInt("Slot");
            if (slot < 0 || slot >= 5) continue;
            value.slots[slot].read(item.getList("Sounds", Tag.TAG_STRING));
            value.slots[slot].randomPitch = item.getBoolean("RandomPitch");
        }
    }

    public void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (int i = 0; i < slots.length; i++) {
            CompoundTag item = new CompoundTag();
            item.putInt("Slot", i);
            item.put("Sounds", slots[i].save());
            item.putBoolean("RandomPitch", slots[i].randomPitch);
            list.add(item);
        }
        tag.put(KEY, list);
    }
}
