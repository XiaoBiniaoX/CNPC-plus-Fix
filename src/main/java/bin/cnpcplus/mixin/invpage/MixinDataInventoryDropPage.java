package bin.cnpcplus.mixin.invpage;

import bin.cnpcplus.invpage.DropPageStore;
import noppes.npcs.entity.data.DataInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Shift container slot indices 7..15 (drop slots) by page*9, so the drop
 * inventory map (keys 0..26) is addressed per page. DataInventory methods
 * are IInventory implementations, reobf-renamed to func_70xxx SRG names.
 */
@Mixin(DataInventory.class)
public class MixinDataInventoryDropPage {

    @ModifyVariable(method = {"func_70301_a", "func_70298_a", "func_70304_b", "func_70299_a"},
            at = @At("HEAD"), argsOnly = true, index = 1, remap = false)
    private int cnpcplus$shiftDropIndex(int index) {
        if (index >= 7) {
            return index + DropPageStore.get((DataInventory) (Object) this) * 9;
        }
        return index;
    }
}
