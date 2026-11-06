package top.cnpcplus.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.stats.Stats;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.station.*;

@Mixin(value = CampfireBlock.class, remap = false)
public class MixinCampfireRecipeUse {
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$use(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (!(level.getBlockEntity(pos) instanceof CampfireBlockEntity block)
                || !StationRecipes.list(2, level.isClientSide).stream().anyMatch(r -> r.matchesInputs(new ItemStack[]{stack}))) return;
        StationRecipe recipe = StationRecipes.find(2, new ItemStack[]{stack}, player);
        if (recipe == null) { cir.setReturnValue(ItemInteractionResult.FAIL); return; }
        if (!level.isClientSide && block.placeFood(player, player.getAbilities().instabuild ? stack.copy() : stack, recipe.time)) {
            player.awardStat(Stats.INTERACT_WITH_CAMPFIRE);
            cir.setReturnValue(ItemInteractionResult.SUCCESS);
        } else cir.setReturnValue(ItemInteractionResult.CONSUME);
    }
}
