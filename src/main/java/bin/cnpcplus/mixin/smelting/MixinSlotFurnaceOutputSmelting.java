package bin.cnpcplus.mixin.smelting;

import bin.cnpcplus.smelting.SmeltingFuelRules;
import bin.cnpcplus.smelting.SmeltingRecipeData;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.SlotFurnaceOutput;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a recipe grant an experience value of 1 or more.
 *
 * SlotFurnaceOutput.onCrafting only scales by the experience value when it is
 * below 1.0 (bytecode offsets 56..59 jump straight past the scaling when
 * xp >= 1.0f), leaving the orb count equal to the number of items taken. Vanilla
 * gets away with that because every vanilla smelting recipe awards a fraction,
 * so any custom value of 1 or more collapsed to "one point per item" no matter
 * how large it was.
 *
 * Only recipes from this mod are touched; anything else falls through to the
 * vanilla path untouched.
 */
@Mixin(SlotFurnaceOutput.class)
public abstract class MixinSlotFurnaceOutputSmelting {
    @Shadow private EntityPlayer player;
    @Shadow private int removeCount;

    @Inject(method = "onCrafting(Lnet/minecraft/item/ItemStack;)V", at = @At("HEAD"),
            cancellable = true)
    private void cnpcplus$customExperience(ItemStack stack, CallbackInfo ci) {
        if (stack == null || stack.isEmpty() || this.removeCount <= 0) {
            return;
        }
        SmeltingRecipeData data = findByOutput(stack);
        // Below 1 the vanilla maths is already correct, so leave it alone.
        if (data == null || data.xp < 1.0F) {
            return;
        }
        int count = this.removeCount;
        this.removeCount = 0;
        stack.onCrafting(this.player.world, this.player, count);
        if (this.player.world.isRemote) {
            ci.cancel();
            return;
        }
        int total = (int) Math.floor((double) count * (double) data.xp);
        while (total > 0) {
            int split = EntityXPOrb.getXPSplit(total);
            total -= split;
            this.player.world.spawnEntity(new EntityXPOrb(this.player.world,
                    this.player.posX, this.player.posY + 0.5D, this.player.posZ + 0.5D, split));
        }
        net.minecraftforge.fml.common.FMLCommonHandler.instance()
                .firePlayerSmeltedEvent(this.player, stack);
        ci.cancel();
    }

    private static SmeltingRecipeData findByOutput(ItemStack output) {
        for (SmeltingRecipeData data : bin.cnpcplus.smelting.SmeltingRecipeRegistry.list()) {
            if (data != null && SmeltingFuelRules.stackMatches(output, data.output)) {
                return data;
            }
        }
        return null;
    }
}
