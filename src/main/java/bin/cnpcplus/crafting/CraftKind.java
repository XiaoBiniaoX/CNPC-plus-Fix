package bin.cnpcplus.crafting;

public enum CraftKind {
    SMITHING("smithing", new String[]{"Template", "Base", "Addition"}, new int[][]{{8, 48}, {26, 48}, {44, 48}, {98, 48}}),
    ANVIL("anvil", new String[]{"InputA", "InputB"}, new int[][]{{8, 55}, {57, 55}, {115, 55}}),
    DROP("drop", new String[]{"Input0", "Input1", "Input2", "Input3"}, new int[][]{{8, 51}, {26, 51}, {8, 69}, {26, 69}, {92, 59}});

    public final String id;
    public final String[] inputKeys;
    public final int[][] slots;
    CraftKind(String id, String[] inputKeys, int[][] slots) { this.id = id; this.inputKeys = inputKeys; this.slots = slots; }
    public int guiId() { return 72 + ordinal(); }
    public String title() { return "cnpcplus." + id + ".title"; }
    public static CraftKind fromGui(int id) { return id >= 72 && id <= 74 ? values()[id - 72] : null; }
}
