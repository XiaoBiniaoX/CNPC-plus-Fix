package top.cnpcplus.mixin;

import noppes.npcs.entity.data.DataInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import top.cnpcplus.invpage.DropPageStore;

@Mixin(value = DataInventory.class, remap = false)
public class MixinDataInventoryDropPage {

    @ModifyVariable(method = {"m_8020_", "m_7407_", "m_8016_", "m_6836_"}, at = @At("HEAD"), argsOnly = true, index = 1)
    private int cnpcplus$shiftDropIndex(int index) {
        if (index >= 7) {
            return index + DropPageStore.get((DataInventory) (Object) this) * 9;
        }
        return index;
    }
}
