package bin.cnpcplus.wand;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/** A native global toggle also clears individual freezes in unloaded chunks. */
public final class WandFreezeState extends WorldSavedData {
    private long epoch;
    public WandFreezeState(String name) { super(name); }
    public static WandFreezeState get(MinecraftServer server) {
        MapStorage storage = server.getWorld(0).getMapStorage();
        WandFreezeState state = (WandFreezeState) storage.getOrLoadData(WandFreezeState.class, "cnpcplus_wand_freeze");
        if (state == null) {
            state = new WandFreezeState("cnpcplus_wand_freeze");
            storage.setData("cnpcplus_wand_freeze", state);
        }
        return state;
    }
    public long epoch() { return epoch; }
    public void clearIndividual() { epoch++; markDirty(); }
    @Override public void readFromNBT(NBTTagCompound tag) { epoch = tag.getLong("Epoch"); }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) { tag.setLong("Epoch", epoch); return tag; }
}
