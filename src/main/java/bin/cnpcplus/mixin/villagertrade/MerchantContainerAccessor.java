package bin.cnpcplus.mixin.villagertrade;

import net.minecraft.world.inventory.MerchantContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = MerchantContainer.class, remap = false)
public interface MerchantContainerAccessor {
    @Accessor("selectionHint")
    int getSelectionHint();

    @Accessor("selectionHint")
    void setSelectionHint(int value);
}
