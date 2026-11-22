package top.cnpcplus.wand;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** A global action invalidates older individual freezes, even in unloaded chunks. */
public final class WandFreezeState extends SavedData {
    private long epoch;

    public static WandFreezeState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(WandFreezeState::load,
                WandFreezeState::new, "cnpcplus_wand_freeze");
    }

    public long epoch() { return epoch; }

    public void clearIndividual() {
        epoch++;
        setDirty();
    }

    public static WandFreezeState load(CompoundTag tag) {
        var state = new WandFreezeState();
        state.epoch = tag.getLong("Epoch");
        return state;
    }

    @Override public CompoundTag save(CompoundTag tag) {
        tag.putLong("Epoch", epoch);
        return tag;
    }
}
