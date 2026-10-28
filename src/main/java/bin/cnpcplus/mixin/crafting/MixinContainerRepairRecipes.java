package bin.cnpcplus.mixin.crafting;

import bin.cnpcplus.common.IAnvilRecipeAccess;
import bin.cnpcplus.crafting.AnvilResultSlot;
import bin.cnpcplus.crafting.CraftKind;
import bin.cnpcplus.crafting.CustomRecipe;
import bin.cnpcplus.crafting.RecipeStore;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerRepair;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ContainerRepair.class)
public abstract class MixinContainerRepairRecipes extends Container implements IAnvilRecipeAccess {
    @Shadow @Final private IInventory inputSlots;
    @Shadow @Final private IInventory outputSlot;
    @Shadow @Final private World world;
    @Shadow public int maximumCost;
    @Shadow public int materialCost;
    @Unique private boolean cnpcplus$customRecipe, cnpcplus$takingRecipe;
    @Unique private CustomRecipe cnpcplus$matched;
    @Unique private int cnpcplus$lastCost = Integer.MIN_VALUE, cnpcplus$lastMode = -1;

    @Inject(method = "<init>(Lnet/minecraft/entity/player/InventoryPlayer;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/player/EntityPlayer;)V", at = @At("RETURN"), require = 1)
    private void cnpcplus$installResultSlot(InventoryPlayer inventory, World world, BlockPos pos, EntityPlayer player, CallbackInfo ci) {
        ContainerRepair self = (ContainerRepair) (Object) this;
        Slot slot = self.inventorySlots.get(2);
        self.inventorySlots.set(2, new AnvilResultSlot(self, slot, world, pos));
    }

    @Inject(method = "updateRepairOutput", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$customAnvilRecipe(CallbackInfo ci) {
        if (this.cnpcplus$takingRecipe) { ci.cancel(); return; }
        if (world.isRemote) return;
        this.cnpcplus$customRecipe = false;
        this.cnpcplus$matched = null;
        ItemStack left = inputSlots.getStackInSlot(0), right = inputSlots.getStackInSlot(1);
        if (left.isEmpty() || right.isEmpty()) return;
        for (CustomRecipe recipe : RecipeStore.list(CraftKind.ANVIL)) {
            if (!CustomRecipe.matches(left, recipe.inputs[0], recipe.nameOnly)
                    || !CustomRecipe.matches(right, recipe.inputs[1], recipe.nameOnly)) continue;
            // 配方写了数量就必须凑够；否则 4 个铁锭的配方会被 1 个铁锭顶掉。
            if (left.getCount() < recipe.inputs[0].getCount()
                    || right.getCount() < recipe.inputs[1].getCount()) continue;
            this.cnpcplus$customRecipe = true;
            this.cnpcplus$matched = recipe;
            this.maximumCost = recipe.experience;
            this.materialCost = 1;
            this.outputSlot.setInventorySlotContents(0, recipe.result.copy());
            this.detectAndSendChanges();
            ci.cancel();
            return;
        }
    }

    @Override public boolean cnpcplus$isCustomRecipe() { return this.cnpcplus$customRecipe; }
    @Override public void cnpcplus$setTakingRecipe(boolean taking) { this.cnpcplus$takingRecipe = taking; }
    @Override public CustomRecipe cnpcplus$customRecipe() { return this.cnpcplus$matched; }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int mode = this.cnpcplus$customRecipe ? 1 : 0;
        if (this.cnpcplus$lastCost == this.maximumCost && this.cnpcplus$lastMode == mode) return;
        for (IContainerListener listener : this.listeners) {
            listener.sendWindowProperty(this, 0, this.maximumCost);
            listener.sendWindowProperty(this, 1, mode);
        }
        this.cnpcplus$lastCost = this.maximumCost;
        this.cnpcplus$lastMode = mode;
    }

    @Inject(method = "addListener", at = @At("TAIL"), require = 1)
    private void cnpcplus$syncModeOnOpen(IContainerListener listener, CallbackInfo ci) {
        listener.sendWindowProperty(this, 1, this.cnpcplus$customRecipe ? 1 : 0);
    }

    @Inject(method = "updateProgressBar", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$receiveMode(int id, int value, CallbackInfo ci) {
        if (id == 1) { this.cnpcplus$customRecipe = value != 0; ci.cancel(); }
    }
}
