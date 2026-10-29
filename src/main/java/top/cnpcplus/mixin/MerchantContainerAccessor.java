package top.cnpcplus.mixin;

import net.minecraft.world.inventory.MerchantContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MerchantContainer.class)
public interface MerchantContainerAccessor {
    @Accessor("selectionHint")
    int getSelectionHint();

    @Accessor("selectionHint")
    void setSelectionHint(int value);
}
