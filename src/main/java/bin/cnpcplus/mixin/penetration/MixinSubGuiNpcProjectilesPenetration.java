package bin.cnpcplus.mixin.penetration;

import bin.cnpcplus.common.IRangedPenetration;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import noppes.npcs.client.gui.SubGuiNpcProjectiles;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the projectile penetration field to the vanilla CNPC projectile editor.
 * Text field id 6 and label id 9 are unused by the vanilla screen.
 * SubGuiNpcProjectiles.stats is private on a noppes class, so @Shadow is not
 * reliable here (see findings.md); it is read through reflection instead.
 */
@Mixin(value = SubGuiNpcProjectiles.class, remap = false)
public class MixinSubGuiNpcProjectilesPenetration {
    private static final int FIELD_ID = 6;

    @Inject(method = "func_73866_w_", at = @At("TAIL"), remap = false)
    private void cnpcplus$addPenetrationField(CallbackInfo ci) {
        SubGuiNpcProjectiles gui = (SubGuiNpcProjectiles) (Object) this;
        IRangedPenetration stats = cnpcplus$stats(gui);
        if (stats == null) {
            return;
        }
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        gui.addLabel(new GuiNpcLabel(9, "cnpcplus.penetration", gui.guiLeft + 210, gui.guiTop + 135));
        GuiNpcTextField field = new GuiNpcTextField(FIELD_ID, (GuiScreen) gui, font,
                gui.guiLeft + 210, gui.guiTop + 145, 40, 18,
                stats.cnpcplus$getPenetration() + "");
        field.numbersOnly = true;
        field.setMinMaxDefault(0, 16, 0);
        gui.addTextField(field);
    }

    // GuiTextField.id is private in MC, so compare the field object instead.
    @Inject(method = "unFocused", at = @At("HEAD"), remap = false)
    private void cnpcplus$savePenetration(GuiNpcTextField textfield, CallbackInfo ci) {
        SubGuiNpcProjectiles self = (SubGuiNpcProjectiles) (Object) this;
        if (textfield == null || self.getTextField(FIELD_ID) != textfield) {
            return;
        }
        IRangedPenetration stats = cnpcplus$stats((SubGuiNpcProjectiles) (Object) this);
        if (stats != null) {
            stats.cnpcplus$setPenetration(textfield.getInteger());
        }
    }

    private static IRangedPenetration cnpcplus$stats(SubGuiNpcProjectiles gui) {
        try {
            Field field = SubGuiNpcProjectiles.class.getDeclaredField("stats");
            field.setAccessible(true);
            Object stats = field.get(gui);
            return stats instanceof IRangedPenetration ? (IRangedPenetration) stats : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
