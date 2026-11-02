package top.cnpcplus.lines;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import bin.cnpcplus.lines.MeleeHitLinesAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import noppes.npcs.controllers.data.Line;
import noppes.npcs.controllers.data.Lines;
import noppes.npcs.entity.data.DataAdvanced;

public final class LinesData {
    private LinesData() {}
    public static Lines category(DataAdvanced advanced, int type) {
        return switch (type) {
            case 0 -> advanced.interactLines;
            case 1 -> advanced.attackLines;
            case 2 -> advanced.worldLines;
            case 3 -> advanced.killedLines;
            case 4 -> advanced.killLines;
            case 5 -> advanced.npcInteractLines;
            case 6 -> ((MeleeHitLinesAccess) advanced).cnpcplus$getMeleeHitLines();
            default -> null;
        };
    }
    public static List<Integer> slots(Map<Integer, ?> lines) {
        return lines.keySet().stream().filter(k -> k != null && k >= 0 && lines.get(k) != null).sorted().toList();
    }
    public static CompoundTag save(Map<Integer, Line> lines) {
        ListTag list = new ListTag();
        for (int slot : slots(lines)) {
            ArrayLine line = ArrayLine.of(lines.get(slot));
            CompoundTag item = new CompoundTag();
            item.putInt("Slot", slot);
            item.putString("Line", line.getText());
            item.putString("Spans", line.styled().spans());
            item.putString("Song", line.getSound());
            item.put("Sounds", line.sounds.save());
            item.putBoolean("ShowText", line.getShowText());
            list.add(item);
        }
        CompoundTag out = new CompoundTag();
        out.put("Lines", list);
        return out;
    }
    public static HashMap<Integer, Line> read(CompoundTag tag) {
        HashMap<Integer, Line> lines = new HashMap<>();
        ListTag list = tag.getList("Lines", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            int slot = item.getInt("Slot");
            if (slot < 0) continue;
            ArrayLine line = new ArrayLine();
            line.setText(item.getString("Line"));
            line.styled(new top.cnpcplus.text.StyledText(line.getText(), item.getString("Spans")).legacy());
            if (item.contains("Sounds", Tag.TAG_LIST)) line.sounds.read(item.getList("Sounds", Tag.TAG_STRING));
            else line.setSound(item.getString("Song"));
            line.setShowText(!item.contains("ShowText") || item.getBoolean("ShowText"));
            lines.put(slot, line);
        }
        return lines;
    }
    public static int nextSlot(List<Integer> slots, int previous) {
        return slots.stream().filter(slot -> slot > previous).findFirst().orElse(slots.get(0));
    }
}
