package top.cnpcplus.quest;

import java.util.Arrays;

/** Workstation keys used by recipe references and production events. */
public enum CraftStation {
    WORKBENCH("workbench", "cnpcplus.quest.source.workbench"),
    CARPENTRY("carpentry", "cnpcplus.quest.source.carpentry"),
    ANVIL("anvil", "cnpcplus.quest.source.anvil"),
    SMITHING("smithing", "cnpcplus.quest.source.smithing"),
    FURNACE("furnace", "cnpcplus.quest.source.furnace"),
    BLAST_FURNACE("blast_furnace", "cnpcplus.quest.source.blast_furnace"),
    SMOKER("smoker", "cnpcplus.quest.source.smoker"),
    GRINDSTONE("grindstone", "cnpcplus.quest.source.grindstone"),
    BREWING("brewing", "cnpcplus.quest.source.brewing"),
    CAMPFIRE("campfire", "cnpcplus.quest.source.campfire"),
    DROP("drop", "cnpcplus.quest.source.drop");

    public final String key;
    public final String translationKey;

    CraftStation(String key, String translationKey) {
        this.key = key;
        this.translationKey = translationKey;
    }

    public static CraftStation byKey(String key) {
        return Arrays.stream(values()).filter(station -> station.key.equals(key)).findFirst().orElse(null);
    }
}
