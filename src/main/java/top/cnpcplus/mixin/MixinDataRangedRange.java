package top.cnpcplus.mixin;

import noppes.npcs.entity.data.DataRanged;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = DataRanged.class, remap = false)
public class MixinDataRangedRange {

    @ModifyConstant(method = "setRange", constant = @Constant(intValue = 100))
    private int modifySetRangeMax(int value) {
        return 256;
    }
}
