package bin.cnpcplus.quest;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntityFurnace;

/** FIFO provenance for the furnace output, persisted with the tile's Forge data. */
public final class FurnaceProduction {
    private static final String KEY = "CNPCPlusFurnaceProduction";
    private FurnaceProduction() {}

    public static NBTTagList records(TileEntityFurnace tile, int outputCount) {
        NBTTagList records = tile.getTileData().getTagList(KEY, 10);
        int total = 0;
        for (int i = 0; i < records.tagCount(); i++) total += records.getCompoundTagAt(i).getInteger("Count");
        if (total > outputCount) take(records, total - outputCount);
        else if (total < outputCount) {
            NBTTagList aligned = new NBTTagList();
            append(aligned, "", outputCount - total);
            for (int i = 0; i < records.tagCount(); i++) aligned.appendTag(records.getCompoundTagAt(i));
            records = aligned;
        }
        tile.getTileData().setTag(KEY, records);
        return records;
    }

    public static void append(NBTTagList records, String recipe, int count) {
        if (count <= 0) return;
        if (records.tagCount() > 0) {
            NBTTagCompound last = records.getCompoundTagAt(records.tagCount() - 1);
            if (last.getString("Recipe").equals(recipe)) {
                last.setInteger("Count", last.getInteger("Count") + count);
                return;
            }
        }
        NBTTagCompound record = new NBTTagCompound();
        record.setString("Recipe", recipe); record.setInteger("Count", count);
        records.appendTag(record);
    }

    public static NBTTagList take(NBTTagList records, int count) {
        NBTTagList taken = new NBTTagList();
        while (count > 0 && records.tagCount() > 0) {
            NBTTagCompound first = records.getCompoundTagAt(0);
            int available = first.getInteger("Count"), amount = Math.min(count, available);
            append(taken, first.getString("Recipe"), amount);
            count -= amount;
            if (amount == available) records.removeTag(0);
            else first.setInteger("Count", available - amount);
        }
        return taken;
    }
}
