package top.cnpcplus.wand;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** A global action invalidates older individual freezes, even in unloaded chunks. */
public final class WandFreezeState extends SavedData {
    private long epoch;

    public static WandFreezeState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WandFreezeState::new, WandFreezeState::load), "cnpcplus_wand_freeze");
    }

    public long epoch() { return epoch; }
    public void clearIndividual() { epoch++; setDirty(); }
    public static WandFreezeState load(CompoundTag tag, HolderLookup.Provider registries) {
        var state = new WandFreezeState();
        state.epoch = tag.getLong("Epoch");
        return state;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("Epoch", epoch);
        return tag;
    }
}
