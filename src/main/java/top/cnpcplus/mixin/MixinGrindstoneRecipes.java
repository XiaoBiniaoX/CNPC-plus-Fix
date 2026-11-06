package top.cnpcplus.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import top.cnpcplus.station.*;

@Mixin(value = GrindstoneMenu.class, remap = false)
public abstract class MixinGrindstoneRecipes extends AbstractContainerMenu {
    @Shadow @Final private Container resultSlots;
    @Shadow @Final private Container repairSlots;
    @Shadow @Final private ContainerLevelAccess access;
    @Unique private Player cnpcplus$player;
    protected MixinGrindstoneRecipes(MenuType<?> type, int id) { super(type, id); }
    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("TAIL"), require = 1)
    private void cnpcplus$slots(int id, Inventory inventory, ContainerLevelAccess levelAccess, CallbackInfo ci) {
        cnpcplus$player = inventory.player;
        for (int i = 0; i < 2; i++) {
            Slot old = slots.get(i);
            Slot slot = new Slot(repairSlots, i, old.x, old.y);
            slot.index = i;
            slots.set(i, slot);
        }
        Slot old = slots.get(2);
        Slot slot = new Slot(resultSlots, 0, old.x, old.y) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) {
                ItemStack[] inputs = {repairSlots.getItem(0), repairSlots.getItem(1)};
                boolean custom = StationRecipes.list(0, player.level().isClientSide).stream().anyMatch(r -> r.matchesInputs(inputs));
                StationRecipe recipe = StationRecipes.find(0, inputs, player);
                return !custom || recipe != null && (player.getAbilities().instabuild || player.experienceLevel >= recipe.experience);
            }
            @Override public void onTake(Player player, ItemStack stack) {
                StationRecipe recipe = StationRecipes.find(0, new ItemStack[]{repairSlots.getItem(0), repairSlots.getItem(1)}, player);
                if (recipe == null) { old.onTake(player, stack); return; }
                if (!player.level().isClientSide) {
                    if (!player.getAbilities().instabuild) player.giveExperienceLevels(-recipe.experience);
                    ItemStack top = repairSlots.getItem(0).copy(), bottom = repairSlots.getItem(1).copy();
                    top.shrink(recipe.inputs[0].getCount()); bottom.shrink(recipe.inputs[1].getCount());
                    repairSlots.setItem(0, top); repairSlots.setItem(1, bottom);
                    access.execute((level, pos) -> level.levelEvent(1042, pos, 0));
                }
            }
        };
        slot.index = 2;
        slots.set(2, slot);
    }
    @Inject(method = "createResult", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$result(CallbackInfo ci) {
        ItemStack[] inputs = {repairSlots.getItem(0), repairSlots.getItem(1)};
        if (cnpcplus$player == null || !StationRecipes.list(0, cnpcplus$player.level().isClientSide).stream().anyMatch(r -> r.matchesInputs(inputs))) return;
        StationRecipe recipe = StationRecipes.find(0, inputs, cnpcplus$player);
        resultSlots.setItem(0, recipe == null ? ItemStack.EMPTY : recipe.outputs[0].copy());
        broadcastChanges();
        ci.cancel();
    }
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$quickTake(Player player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot == 2 && !slots.get(2).mayPickup(player)) cir.setReturnValue(ItemStack.EMPTY);
    }
}
