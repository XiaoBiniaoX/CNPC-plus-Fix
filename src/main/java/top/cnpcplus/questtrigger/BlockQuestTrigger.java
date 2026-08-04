package top.cnpcplus.questtrigger;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.PacketDistributor;
import top.cnpcplus.questtrigger.network.PacketOpenTriggerGui;
import top.cnpcplus.questtrigger.network.TriggerPacketHandler;

public class BlockQuestTrigger extends Block implements EntityBlock {

    public BlockQuestTrigger() {
        super(Properties.copy(Blocks.STONE).strength(3.0F, 10.0F));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.getAbilities().instabuild) {
                BlockEntity tile = level.getBlockEntity(pos);
                if (tile instanceof TileQuestTrigger) {
                    TriggerPacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
                            new PacketOpenTriggerGui(pos, tile.saveWithFullMetadata()));
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileQuestTrigger(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModRegistry.TILE_QUEST_TRIGGER.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<?>) (l, p, s, tile) -> TileQuestTrigger.tick(l, p, s, (TileQuestTrigger) tile);
    }
}
