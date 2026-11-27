package top.cnpcplus.questtrigger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import noppes.npcs.controllers.PlayerQuestController;
import noppes.npcs.controllers.QuestController;
import noppes.npcs.controllers.data.Quest;

public class TileQuestTrigger extends BlockEntity {
    public static final String TAG = "TriggerData";
    public int mode;
    public int interval = 20;
    public int areaMode;
    public int sizeX = 3, sizeY = 3, sizeZ = 3;
    public int radius = 3;
    public int questId = -1;
    private boolean prevPowered;
    private int counter;

    public TileQuestTrigger(BlockPos pos, BlockState state) { super(ModRegistry.TILE_QUEST_TRIGGER.get(), pos, state); }

    public static void tick(Level level, BlockPos pos, BlockState state, TileQuestTrigger tile) {
        if (tile.mode == 0) {
            boolean powered = level.hasNeighborSignal(pos);
            if (powered && !tile.prevPowered) tile.trigger();
            tile.prevPowered = powered;
        } else if (++tile.counter >= tile.interval) {
            tile.counter = 0;
            tile.trigger();
        }
    }

    private void trigger() {
        if (level == null || level.isClientSide || questId <= 0) return;
        Quest quest = QuestController.instance.quests.get(questId);
        if (quest == null) return;
        double radiusCheck = Math.max(0, radius) + 0.5;
        AABB box = areaMode == 1 ? new AABB(worldPosition).inflate(radiusCheck)
                : new AABB(worldPosition).inflate(sizeX, sizeY, sizeZ);
        double cx = worldPosition.getX() + 0.5, cy = worldPosition.getY() + 0.5, cz = worldPosition.getZ() + 0.5;
        for (Player player : level.getEntitiesOfClass(Player.class, box)) {
            if (areaMode == 1 && player.distanceToSqr(cx, cy, cz) > radiusCheck * radiusCheck) continue;
            if (PlayerQuestController.canQuestBeAccepted(player, questId)) PlayerQuestController.addActiveQuest(quest, player);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CompoundTag data = new CompoundTag();
        data.putInt("mode", mode);
        data.putInt("interval", interval);
        data.putInt("areaMode", areaMode);
        data.putInt("sizeX", sizeX); data.putInt("sizeY", sizeY); data.putInt("sizeZ", sizeZ);
        data.putInt("radius", radius);
        data.putInt("questId", questId);
        tag.put(TAG, data);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        CompoundTag data = tag.getCompound(TAG);
        mode = data.getInt("mode") == 0 ? 0 : 1;
        interval = Math.max(1, Math.min(600, data.getInt("interval")));
        areaMode = data.getInt("areaMode") == 0 ? 0 : 1;
        sizeX = Math.max(0, Math.min(100, data.getInt("sizeX")));
        sizeY = Math.max(0, Math.min(100, data.getInt("sizeY")));
        sizeZ = Math.max(0, Math.min(100, data.getInt("sizeZ")));
        radius = Math.max(0, Math.min(100, data.getInt("radius")));
        questId = data.getInt("questId");
    }
}
