package bin.cnpcplus.quest;

public enum CraftStation {
    WORKBENCH("workbench"), CARPENTRY("carpentry"), ANVIL("anvil"), SMITHING("smithing"),
    BREWING("brewing"), CAMPFIRE("campfire"), FURNACE("furnace"), DROP("drop"), GRINDSTONE("grindstone");

    public final String key;
    CraftStation(String key) { this.key = key; }

    public static CraftStation byKey(String key) {
        for (CraftStation station : values()) if (station.key.equals(key)) return station;
        return null;
    }
}
