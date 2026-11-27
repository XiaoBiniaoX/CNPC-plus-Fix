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
import top.cnpcplus.quest.BrewingProductionAccess;
import top.cnpcplus.quest.QuestCraft;

import java.util.HashMap;
import java.util.Map;

@Mixin(BrewingStandBlockEntity.class)
public class MixinBrewingRecipes implements BrewingProductionAccess {
    @Shadow private NonNullList<ItemStack> items;
    @Shadow int brewTime;
    @Shadow int fuel;
    @Unique private int cnpcplus$recipe = -1;
    @Unique private ItemStack[] cnpcplus$produced = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    @Unique private int[] cnpcplus$producedRecipes = {-1, -1, -1};
    @Unique private long[] cnpcplus$producedBatches = {-1L, -1L, -1L};
    @Unique private long cnpcplus$brewBatchCounter;
    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true, require = 1)
    private static void cnpcplus$tick(Level level, BlockPos pos, BlockState state, BrewingStandBlockEntity block, CallbackInfo ci) {
        MixinBrewingRecipes self = (MixinBrewingRecipes)(Object)block;
        ItemStack[] inputs = {self.items.get(0), self.items.get(1), self.items.get(2), self.items.get(3)};
        boolean custom = StationRecipes.hasMatch(1, inputs);
        if (!custom && self.cnpcplus$recipe == -1) return;
        StationRecipe recipe = StationRecipes.find(1, inputs, StationOperator.player(block));
        if (recipe == null) { self.brewTime = 0; self.cnpcplus$recipe = -1; }
        else {
            if (self.fuel <= 0 && self.items.get(4).is(Items.BLAZE_POWDER)) {
                self.items.get(4).shrink(1); self.fuel = 20;
            }
            if (self.cnpcplus$recipe != recipe.id) { self.brewTime = 0; self.cnpcplus$recipe = recipe.id; }
            if (self.brewTime > 0) {
                if (--self.brewTime == 0) {
                    int[] slots = recipe.matchSlots(inputs);
                    long batch = ++self.cnpcplus$brewBatchCounter;
                    for (int i = 0; i < 3; i++) {
                        ItemStack output = recipe.outputs[slots[i]].copy();
                        self.items.set(i, output);
                        self.cnpcplus$produced[i] = output.copy();
                        self.cnpcplus$producedRecipes[i] = recipe.id;
                        self.cnpcplus$producedBatches[i] = batch;
                    }
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
    @Inject(method = "load", at = @At("TAIL"), require = 1)
    private void cnpcplus$load(CompoundTag tag, CallbackInfo ci) {
        cnpcplus$recipe = tag.contains("CNPCPlusBrewRecipe") ? tag.getInt("CNPCPlusBrewRecipe") : -1;
        if (cnpcplus$recipe >= 0) brewTime = tag.getInt("CNPCPlusBrewTime");
        cnpcplus$brewBatchCounter = tag.getLong("CNPCPlusBrewBatchCounter");
        Map<Integer, Long> legacyBatches = new HashMap<>();
        for (int i = 0; i < 3; i++) {
            cnpcplus$produced[i] = ItemStack.of(tag.getCompound("CNPCPlusBrewOutput" + i));
            cnpcplus$producedRecipes[i] = tag.contains("CNPCPlusBrewOutputRecipe" + i)
                    ? tag.getInt("CNPCPlusBrewOutputRecipe" + i) : -1;
            String batchKey = "CNPCPlusBrewOutputBatch" + i;
            cnpcplus$producedBatches[i] = tag.contains(batchKey) ? tag.getLong(batchKey)
                    : cnpcplus$producedRecipes[i] < 0 ? -1L
                    : legacyBatches.computeIfAbsent(cnpcplus$producedRecipes[i], ignored -> ++cnpcplus$brewBatchCounter);
        }
    }
    @Inject(method = "saveAdditional", at = @At("TAIL"), require = 1)
    private void cnpcplus$save(CompoundTag tag, CallbackInfo ci) {
        tag.putInt("CNPCPlusBrewRecipe", cnpcplus$recipe); tag.putInt("CNPCPlusBrewTime", brewTime);
        tag.putLong("CNPCPlusBrewBatchCounter", cnpcplus$brewBatchCounter);
        for (int i = 0; i < 3; i++) {
            if (!cnpcplus$produced[i].isEmpty()) tag.put("CNPCPlusBrewOutput" + i, cnpcplus$produced[i].save(new CompoundTag()));
            tag.putInt("CNPCPlusBrewOutputRecipe" + i, cnpcplus$producedRecipes[i]);
            tag.putLong("CNPCPlusBrewOutputBatch" + i, cnpcplus$producedBatches[i]);
        }
    }

    @Override
    public int cnpcplus$takeBrewOutput(int slot, ItemStack stack) {
        if (slot < 0 || slot >= cnpcplus$produced.length || stack.isEmpty()) return 0;
        ItemStack produced = cnpcplus$produced[slot];
        if (produced.isEmpty() || !ItemStack.isSameItemSameTags(produced, stack)) return 0;
        int count = Math.min(stack.getCount(), produced.getCount());
        produced.shrink(count);
        if (produced.isEmpty()) cnpcplus$produced[slot] = ItemStack.EMPTY;
        ((BrewingStandBlockEntity)(Object)this).setChanged();
        return count;
    }

    @Override public int cnpcplus$takeBrewRecipe(int slot) {
        if (slot < 0 || slot >= cnpcplus$produced.length) return -1;
        int recipe = QuestCraft.takeBrewRecipe(cnpcplus$producedRecipes, cnpcplus$producedBatches,
                slot, cnpcplus$produced[slot].isEmpty());
        if (recipe >= 0) ((BrewingStandBlockEntity)(Object)this).setChanged();
        return recipe;
    }
}
