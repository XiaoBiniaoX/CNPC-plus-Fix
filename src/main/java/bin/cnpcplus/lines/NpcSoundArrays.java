package bin.cnpcplus.lines;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.entity.data.DataAdvanced;

public final class NpcSoundArrays {
    public static final String KEY = "CNPCPlusSoundArrays";
    private static final Map<DataAdvanced, NpcSoundArrays> DATA = new WeakHashMap<>();
    public final SoundArray[] slots = new SoundArray[5];
    private NpcSoundArrays(DataAdvanced advanced) {
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new SoundArray();
            slots[i].single(advanced.getSound(i));
            slots[i].randomPitch = i != 4 && !advanced.disablePitch;
        }
    }
    public static NpcSoundArrays get(DataAdvanced advanced) {
        NpcSoundArrays value = DATA.get(advanced);
        if (value == null) { value = new NpcSoundArrays(advanced); DATA.put(advanced, value); }
        return value;
    }
    public static NpcSoundArrays peek(DataAdvanced advanced) { return DATA.get(advanced); }
    public static void read(DataAdvanced advanced, NBTTagCompound tag) {
        DATA.remove(advanced);
        NpcSoundArrays value = get(advanced);
        NBTTagList list = tag.getTagList(KEY, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound item = list.getCompoundTagAt(i);
            int slot = item.getInteger("Slot");
            if (slot < 0 || slot >= 5) continue;
            value.slots[slot].read(item.getTagList("Sounds", 8));
            value.slots[slot].randomPitch = item.getBoolean("RandomPitch");
        }
    }
    public void save(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < slots.length; i++) {
            NBTTagCompound item = new NBTTagCompound();
            item.setInteger("Slot", i);
            item.setTag("Sounds", slots[i].save());
            item.setBoolean("RandomPitch", slots[i].randomPitch);
            list.appendTag(item);
        }
        tag.setTag(KEY, list);
    }
}
