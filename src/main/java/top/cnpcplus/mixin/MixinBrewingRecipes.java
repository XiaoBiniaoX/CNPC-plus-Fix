package top.cnpcplus.mixin;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import top.cnpcplus.station.*;

@Mixin(value = BrewingStandBlockEntity.class, remap = false)
public class MixinBrewingRecipes {
    @Shadow private NonNullList<ItemStack> items;
    @Shadow int brewTime;
    @Shadow int fuel;
    @Unique private int cnpcplus$recipe = -1;
    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true, require = 1)
    private static void cnpcplus$tick(Level level, BlockPos pos, BlockState state, BrewingStandBlockEntity block, CallbackInfo ci) {
        MixinBrewingRecipes self = (MixinBrewingRecipes)(Object)block;
        ItemStack[] inputs = {self.items.get(0), self.items.get(1), self.items.get(2), self.items.get(3)};
        boolean custom = StationRecipes.hasMatch(1, inputs);
        if (!custom && self.cnpcplus$recipe == -1) return;
        StationRecipe recipe = StationRecipes.find(1, inputs, StationOperator.player(block));
        if (recipe != null) for (int i = 0; i < 3; i++) if (inputs[i].getCount() != recipe.inputs[i].getCount()) { recipe = null; break; }
        if (recipe == null) { self.brewTime = 0; self.cnpcplus$recipe = -1; }
        else {
            if (self.fuel <= 0 && self.items.get(4).is(Items.BLAZE_POWDER)) { self.items.get(4).shrink(1); self.fuel = 20; }
            if (self.cnpcplus$recipe != recipe.id) { self.brewTime = 0; self.cnpcplus$recipe = recipe.id; }
            if (self.brewTime > 0) {
                if (--self.brewTime == 0) {
                    for (int i = 0; i < 3; i++) self.items.set(i, recipe.outputs[i].copy());
                    self.items.get(3).shrink(recipe.inputs[3].getCount());
                    self.cnpcplus$recipe = -1;
                    level.levelEvent(1035, pos, 0);
                }
            } else if (self.fuel > 0) { --self.fuel; self.brewTime = recipe.time; }
        }
        if (state.getBlock() instanceof BrewingStandBlock) {
            BlockState next = state;
            for (int i = 0; i < 3; i++) next = next.setValue(BrewingStandBlock.HAS_BOTTLE[i], !self.items.get(i).isEmpty());
            if (next != state) level.setBlock(pos, next, 2);
        }
        block.setChanged();
        ci.cancel();
    }
    @Inject(method = "canPlaceItem", at = @At("RETURN"), cancellable = true, require = 1)
    private void cnpcplus$accept(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slot < 4 && StationRecipes.accepts(1, slot, stack, false)) cir.setReturnValue(true);
    }
    @Inject(method = "loadAdditional", at = @At("TAIL"), require = 1)
    private void cnpcplus$load(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        cnpcplus$recipe = tag.contains("CNPCPlusBrewRecipe") ? tag.getInt("CNPCPlusBrewRecipe") : -1;
        if (cnpcplus$recipe >= 0) brewTime = tag.getInt("CNPCPlusBrewTime");
    }
    @Inject(method = "saveAdditional", at = @At("TAIL"), require = 1)
    private void cnpcplus$save(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        tag.putInt("CNPCPlusBrewRecipe", cnpcplus$recipe); tag.putInt("CNPCPlusBrewTime", brewTime);
    }
}
