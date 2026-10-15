package top.cnpcplus.villagertrade;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import top.cnpcplus.roles.TradeData;

/**
 * 交易数据独立存储（不进 NPC NBT）。
 * 键 = tradeListId，值 = 交易列表 NBT。
 * 服务端存文件，客户端缓存（缓冲层）。
 */
public class TradeDataStore {

    private static final Map<Integer, CompoundTag> SERVER_DATA = new ConcurrentHashMap<>();
    private static final Map<Integer, CompoundTag> CLIENT_CACHE = new ConcurrentHashMap<>();
    /** 已被某 NPC 占用但尚未写入文件的 id（构造器分配 / load 读到的 NBT id），防 nextId 撞车。 */
    private static final java.util.Set<Integer> RESERVED = ConcurrentHashMap.newKeySet();
    private static File storageFile;

    /** 存储是否就绪（setStoragePath 已在 ServerStartedEvent 调用）。 */
    public static boolean isReady() {
        return storageFile != null;
    }

    /** 预留一个已被 NPC NBT 占用的 id，防止 nextId 把它再分配出去。 */
    public static void reserve(int id) {
        RESERVED.add(id);
    }

    public static void setStoragePath(File file) {
        storageFile = file;
        loadFromFile();
    }

    /** 服务端：保存交易列表。 */
    public static void save(int id, List<TradeData> trades) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (TradeData t : trades) {
            list.add(t.save(new CompoundTag()));
        }
        tag.put("Trades", list);
        SERVER_DATA.put(id, tag);
        CLIENT_CACHE.put(id, tag);
        saveToFile();
    }

    /** 服务端：加载交易列表。 */
    public static List<TradeData> load(int id) {
        return parseTrades(SERVER_DATA.get(id));
    }

    /** 客户端：从缓存加载（缓冲层）。重启后缓存为空时回退 SERVER_DATA（单机共享）。 */
    public static List<TradeData> loadClient(int id) {
        CompoundTag tag = CLIENT_CACHE.get(id);
        if (tag == null) {
            tag = SERVER_DATA.get(id);
            if (tag != null) {
                CLIENT_CACHE.put(id, tag);
            }
        }
        return parseTrades(tag);
    }

    /** 客户端：更新缓存。 */
    public static void updateClientCache(int id, CompoundTag tag) {
        CLIENT_CACHE.put(id, tag);
    }

    private static List<TradeData> parseTrades(CompoundTag tag) {
        List<TradeData> trades = new ArrayList<>();
        if (tag != null && tag.contains("Trades")) {
            ListTag list = tag.getList("Trades", 10);
            for (int i = 0; i < list.size(); i++) {
                trades.add(TradeData.load(list.getCompound(i)));
            }
        }
        return trades;
    }

    private static void saveToFile() {
        if (storageFile == null) return;
        try {
            CompoundTag fileTag = new CompoundTag();
            for (Map.Entry<Integer, CompoundTag> e : SERVER_DATA.entrySet()) {
                fileTag.put(String.valueOf(e.getKey()), e.getValue());
            }
            storageFile.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(storageFile)) {
                NbtIo.writeCompressed(fileTag, fos);
            }
        } catch (IOException e) {
            System.out.println("[vt] store save failed: " + e); // 问题日志：仅写盘失败输出
        }
    }

    private static void loadFromFile() {
        if (storageFile == null || !storageFile.exists()) return;
        try (FileInputStream fis = new FileInputStream(storageFile)) {
            CompoundTag fileTag = NbtIo.readCompressed(fis);
            SERVER_DATA.clear();
            for (String key : fileTag.getAllKeys()) {
                SERVER_DATA.put(Integer.parseInt(key), fileTag.getCompound(key));
            }
        } catch (IOException e) {
            System.out.println("[vt] store load failed: " + e); // 问题日志：仅读盘失败输出
        }
    }

    /**
     * 分配新的 tradeListId（根因B：此前 nextId 零调用 → 所有 NPC 共享 id=0 互串数据）。
     * 从 1 起找第一个不被 SERVER_DATA（已落盘）和 RESERVED（已分配未落盘）占用的 id。
     */
    public static int nextId() {
        int id = 1;
        while (SERVER_DATA.containsKey(id) || RESERVED.contains(id)) {
            id++;
        }
        RESERVED.add(id);
        return id;
    }
}
