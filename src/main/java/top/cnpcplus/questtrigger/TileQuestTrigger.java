package top.cnpcplus.questtrigger;

import net.minecraft.core.BlockPos;
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

    public int mode = 0;          // 0=红石激活 1=始终检查
    public int interval = 20;     // 始终检查的 tick 间隔
    public int areaMode = 0;      // 0=区域(xyz) 1=范围(半径)
    public int sizeX = 3;
    public int sizeY = 3;
    public int sizeZ = 3;
    public int radius = 3;
    public int questId = -1;      // -1=未选择

    private boolean prevPowered;
    private int counter;

    public TileQuestTrigger(BlockPos pos, BlockState state) {
        super(ModRegistry.TILE_QUEST_TRIGGER.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TileQuestTrigger tile) {
        if (tile.mode == 0) {
            boolean powered = level.hasNeighborSignal(pos);
            if (powered && !tile.prevPowered) {
                tile.trigger();
            }
            tile.prevPowered = powered;
        } else {
            tile.counter++;
            if (tile.counter >= Math.max(1, tile.interval)) {
                tile.counter = 0;
                tile.trigger();
            }
        }
    }

    private void trigger() {
        if (level == null || level.isClientSide || questId <= 0) return;
        Quest quest = QuestController.instance.quests.get(questId);
        if (quest == null) return;

        double radiusCheck = 0;
        AABB box;
        if (areaMode == 1) {
            radiusCheck = Math.max(0, radius) + 0.5;
            box = new AABB(worldPosition).inflate(radiusCheck);
        } else {
            box = new AABB(worldPosition).inflate(Math.max(0, sizeX), Math.max(0, sizeY), Math.max(0, sizeZ));
        }

        double cx = worldPosition.getX() + 0.5;
        double cy = worldPosition.getY() + 0.5;
        double cz = worldPosition.getZ() + 0.5;

        for (Player player : level.getEntitiesOfClass(Player.class, box)) {
            if (areaMode == 1) {
                double dx = player.getX() - cx;
                double dy = player.getY() - cy;
                double dz = player.getZ() - cz;
                if (dx * dx + dy * dy + dz * dz > radiusCheck * radiusCheck) continue;
            }
            if (PlayerQuestController.canQuestBeAccepted(player, questId)) {
                PlayerQuestController.addActiveQuest(quest, player);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        CompoundTag data = new CompoundTag();
        data.putInt("mode", mode);
        data.putInt("interval", interval);
        data.putInt("areaMode", areaMode);
        data.putInt("sizeX", sizeX);
        data.putInt("sizeY", sizeY);
        data.putInt("sizeZ", sizeZ);
        data.putInt("radius", radius);
        data.putInt("questId", questId);
        tag.put(TAG, data);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        CompoundTag data = tag.getCompound(TAG);
        mode = data.getInt("mode");
        interval = Math.max(1, data.getInt("interval"));
        areaMode = data.getInt("areaMode");
        sizeX = Math.max(0, data.getInt("sizeX"));
        sizeY = Math.max(0, data.getInt("sizeY"));
        sizeZ = Math.max(0, data.getInt("sizeZ"));
        radius = Math.max(0, data.getInt("radius"));
        questId = data.getInt("questId");
    }
}
