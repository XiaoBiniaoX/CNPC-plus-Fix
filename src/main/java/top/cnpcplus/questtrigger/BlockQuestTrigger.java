package top.cnpcplus.questtrigger;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.neoforge.network.PacketDistributor;
import top.cnpcplus.questtrigger.network.PacketOpenTriggerGui;

public class BlockQuestTrigger extends Block implements EntityBlock {
    public BlockQuestTrigger() { super(Properties.ofFullCopy(Blocks.STONE).strength(3.0F, 10.0F)); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && player.getAbilities().instabuild
                && level.getBlockEntity(pos) instanceof TileQuestTrigger tile) {
            PacketDistributor.sendToPlayer(serverPlayer, new PacketOpenTriggerGui(pos, tile.saveWithFullMetadata(level.registryAccess())));
        }
        return InteractionResult.SUCCESS;
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TileQuestTrigger(pos, state); }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModRegistry.TILE_QUEST_TRIGGER.get()) return null;
        return (l, p, s, tile) -> TileQuestTrigger.tick(l, p, s, (TileQuestTrigger)tile);
    }
}
