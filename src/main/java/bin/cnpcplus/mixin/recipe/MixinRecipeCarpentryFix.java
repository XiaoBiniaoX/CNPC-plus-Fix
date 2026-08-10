package bin.cnpcplus.mixin.recipe;

import noppes.npcs.controllers.data.RecipeCarpentry;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 1.12 RecipeCarpentry already returns field_77576_b / field_77577_c correctly
 * for width/height. Kept as marker mixin for parity with 1.21.
 */
@Mixin(RecipeCarpentry.class)
public class MixinRecipeCarpentryFix {
}
