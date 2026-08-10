package bin.cnpcplus.mixin.invpage;

import bin.cnpcplus.invpage.DropPageStore;
import noppes.npcs.entity.data.DataInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = DataInventory.class, remap = false)
public class MixinDataInventoryDropPage {

    @ModifyVariable(method = {"getItem", "setItem", "removeItem", "removeItemNoUpdate"}, at = @At("HEAD"), argsOnly = true, index = 1)
    private int cnpcplus$shiftDropIndex(int index) {
        if (index >= 7) {
            return index + DropPageStore.get((DataInventory) (Object) this) * 9;
        }
        return index;
    }
}
