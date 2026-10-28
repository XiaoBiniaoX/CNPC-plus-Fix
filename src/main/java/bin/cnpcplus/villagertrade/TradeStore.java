package bin.cnpcplus.villagertrade;

import bin.cnpcplus.data.NbtFiles;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.CustomNpcs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** NPC 只存 TradeListId；每个交易表独立文件，缓存与有序异步写盘共用 NbtFiles。 */
public final class TradeStore {
    private static final Map<Integer, Record> RECORDS = new HashMap<>();
    private static int nextId;
    private static final class Record {
        UUID owner;
        List<TradeData> trades = new ArrayList<>();
        long revision;
    }
    private TradeStore() { }
    private static Path directory() { return CustomNpcs.getWorldSaveDirectory().toPath().resolve("data/cnpcplus/villagertrade"); }
    private static Path file(int id) { return directory().resolve(id + ".dat"); }

    private static Record record(int id) {
        Record record = RECORDS.get(id);
        if (record != null) return record;
        try {
            if (id <= 0 || !Files.isRegularFile(file(id))) throw new IOException("Missing trade list " + id);
            NBTTagCompound tag = NbtFiles.read(file(id));
            record = new Record();
            record.owner = UUID.fromString(tag.getString("Owner"));
            NBTTagList list = tag.getTagList("Trades", 10);
            for (int i = 0; i < list.tagCount() && i < 64; i++) record.trades.add(TradeData.fromNBT(list.getCompoundTagAt(i)));
            RECORDS.put(id, record);
            return record;
        } catch (Exception error) { throw new IllegalStateException("Cannot load trade list " + id, error); }
    }

    public static int ensure(int id, UUID owner) {
        if (id > 0) {
            Record old = record(id);
            if (owner.equals(old.owner)) return id;
            return allocate(owner, list(id));
        }
        return allocate(owner, new ArrayList<>());
    }

    private static int allocate(UUID owner, List<TradeData> trades) {
        try {
            if (nextId == 0) nextId = Math.max(1, NbtFiles.read(directory().resolve("index.dat")).getInteger("NextId"));
            while (Files.exists(file(nextId)) || RECORDS.containsKey(nextId)) nextId++;
            int id = nextId++;
            Record record = new Record(); record.owner = owner; record.trades = trades;
            NBTTagCompound index = new NBTTagCompound(); index.setInteger("NextId", nextId);
            NbtFiles.writeNow(directory().resolve("index.dat"), index);
            // 首次分配必须成功落盘，之后 NPC 才能安全只存 ID。
            NbtFiles.writeNow(file(id), tag(record));
            RECORDS.put(id, record);
            return id;
        } catch (IOException error) { throw new IllegalStateException("Cannot allocate trade list", error); }
    }

    private static NBTTagCompound tag(Record record) {
        NBTTagCompound tag = new NBTTagCompound(); tag.setString("Owner", record.owner.toString());
        NBTTagList list = new NBTTagList(); for (TradeData trade : record.trades) list.appendTag(trade.toNBT());
        tag.setTag("Trades", list); return tag;
    }

    public static List<TradeData> list(int id) {
        Record record = record(id);
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (TradeData trade : record.trades) changed |= trade.refresh(now);
        if (changed) { record.revision++; NbtFiles.write(file(id), tag(record)); }
        List<TradeData> copy = new ArrayList<>(); for (TradeData trade : record.trades) copy.add(trade.copy());
        return copy;
    }

    public static void save(int id, List<TradeData> trades) {
        if (trades.size() > 64) return;
        Record record = record(id);
        record.trades = new ArrayList<>();
        for (TradeData trade : trades) record.trades.add(trade.copy());
        record.revision++;
        NbtFiles.write(file(id), tag(record));
    }
    public static TradeData get(int id, int index) {
        Record record = record(id);
        if (index < 0 || index >= record.trades.size()) return null;
        TradeData trade = record.trades.get(index);
        if (trade.refresh(System.currentTimeMillis())) { record.revision++; NbtFiles.write(file(id), tag(record)); }
        return trade.copy();
    }
    public static long revision(int id) { return record(id).revision; }
    public static void saveRow(int id, int index, TradeData trade) {
        Record record = record(id);
        if (index < 0 || index >= record.trades.size()) return;
        record.trades.set(index, trade.copy()); record.revision++;
        NbtFiles.write(file(id), tag(record));
    }
    public static void clear() { NbtFiles.flush(); RECORDS.clear(); nextId = 0; }
}
