package bin.cnpcplus.mixin.recipe;

import net.minecraft.world.inventory.MenuType;
import noppes.npcs.CustomContainer;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.constants.EnumGuiType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Restores ManageRecipes MenuType mapping intentionally missing in upstream 1.21.1 NeoForge getType().
 * Original: after ManageBanks branch, all other types including ManageRecipes return null.
 */
@Mixin(NoppesUtilServer.class)
public class MixinNoppesUtilServerGetType {

    @Inject(method = "getType", at = @At("HEAD"), cancellable = true, remap = false)
    private static void cnpcplus$getType(EnumGuiType gui, CallbackInfoReturnable<MenuType<?>> cir) {
        if (gui == EnumGuiType.ManageRecipes) {
            cir.setReturnValue(CustomContainer.container_managerecipes);
        }
    }
}
