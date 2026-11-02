package bin.cnpcplus.lines;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.controllers.data.Line;
import noppes.npcs.controllers.data.Lines;
import noppes.npcs.entity.data.DataAdvanced;

public final class LinesData {
    private LinesData() { }
    public static Lines category(DataAdvanced advanced, int type) {
        switch (type) {
            case 0: return advanced.interactLines;
            case 1: return advanced.attackLines;
            case 2: return advanced.worldLines;
            case 3: return advanced.killedLines;
            case 4: return advanced.killLines;
            case 5: return advanced.npcInteractLines;
            case 6: return MeleeLineStore.get(advanced);
            default: return null;
        }
    }
    public static List<Integer> slots(Map<Integer, ?> values) {
        return values.keySet().stream().filter(k -> k != null && k >= 0 && values.get(k) != null)
                .sorted().collect(Collectors.toList());
    }
    public static NBTTagCompound save(Map<Integer, Line> lines) {
        NBTTagList list = new NBTTagList();
        for (int slot : slots(lines)) {
            ArrayLine line = ArrayLine.of(lines.get(slot));
            NBTTagCompound item = new NBTTagCompound();
            item.setInteger("Slot", slot);
            item.setString("Line", line.getText());
            item.setString("Spans", line.spans);
            item.setString("Song", line.getSound());
            item.setTag("Sounds", line.sounds.save());
            item.setBoolean("ShowText", line.getShowText());
            list.appendTag(item);
        }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("Lines", list);
        return tag;
    }
    public static HashMap<Integer, Line> read(NBTTagCompound tag) {
        HashMap<Integer, Line> lines = new HashMap<>();
        NBTTagList list = tag.getTagList("Lines", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound item = list.getCompoundTagAt(i);
            int slot = item.getInteger("Slot");
            if (slot < 0) continue;
            ArrayLine line = new ArrayLine();
            line.setText(item.getString("Line"));
            line.spans = item.getString("Spans");
            if (item.hasKey("Sounds", 9)) line.sounds.read(item.getTagList("Sounds", 8));
            else line.setSound(item.getString("Song"));
            line.setShowText(!item.hasKey("ShowText") || item.getBoolean("ShowText"));
            lines.put(slot, line);
        }
        return lines;
    }
}
