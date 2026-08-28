package top.cnpcplus.linked.network;

import java.util.HashMap;
import java.util.Map;

public class LinkedSyncClientData {
    private static Map<String, Boolean> statusMap = new HashMap<>();

    public static void setStatusMap(Map<String, Boolean> map) {
        statusMap = new HashMap<>(map);
    }

    public static void setSyncScripts(String tagName, boolean state) {
        statusMap.put(tagName, state);
    }

    public static boolean getSyncScripts(String tagName) {
        return statusMap.getOrDefault(tagName, false);
    }
}