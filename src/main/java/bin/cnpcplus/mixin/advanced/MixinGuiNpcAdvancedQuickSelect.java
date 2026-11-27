package bin.cnpcplus.mixin.advanced;

import bin.cnpcplus.client.gui.SubGuiRoleJobSelect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.text.translation.I18n;
import noppes.npcs.client.gui.mainmenu.GuiNpcAdvanced;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNpcAdvanced.class, remap = false)
public abstract class MixinGuiNpcAdvancedQuickSelect extends GuiNPCInterface2 {
    @Shadow protected abstract void func_146284_a(GuiButton button);
    protected MixinGuiNpcAdvancedQuickSelect(EntityNPCInterface npc) { super(npc); }
    @Inject(method = "func_73866_w_", at = @At("TAIL"), require = 1)
    private void cnpcplus$buttons(CallbackInfo ci) {
        int quickWidth = Math.max(44, Minecraft.getMinecraft().fontRenderer
                .getStringWidth(I18n.translateToLocal("cnpcplus.select.quick")) + 12);
        int labelWidth = 60;
        for (int id : new int[]{8, 5}) {
            GuiNpcButton button = getButton(id);
            String current = button.displayString;
            labelWidth = Math.max(labelWidth, Minecraft.getMinecraft().fontRenderer.getStringWidth(current));
        }
        labelWidth = Math.min(120, labelWidth);
        for (int id : new int[]{8, 5}) {
            GuiNpcButton button = getButton(id);
            button.setVisible(false);
            String current = button.displayString;
            int textWidth = Minecraft.getMinecraft().fontRenderer.getStringWidth(current);
            if (textWidth > labelWidth) {
                current = Minecraft.getMinecraft().fontRenderer.trimStringToWidth(current,
                        labelWidth - Minecraft.getMinecraft().fontRenderer.getStringWidth("…")) + "…";
                textWidth = Minecraft.getMinecraft().fontRenderer.getStringWidth(current);
            }
            GuiNpcButton edit = getButton(id == 8 ? 3 : 4);
            int rowWidth = quickWidth + 6 + labelWidth + 6 + edit.getWidth();
            int x = guiLeft + (xSize - rowWidth) / 2;
            int valueX = x + quickWidth + 6 + (labelWidth - textWidth) / 2;
            addLabel(new GuiNpcLabel(800 + id, current, valueX, button.y + 6));
            addButton(new GuiNpcButton(720 + id, x, button.y, quickWidth, 20, "cnpcplus.select.quick"));
            edit.x = x + quickWidth + 6 + labelWidth + 6;
        }
    }
    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$select(GuiButton button, CallbackInfo ci) {
        if (getSubGui() instanceof SubGuiRoleJobSelect) { getSubGui().buttonEvent(button); ci.cancel(); return; }
        if (button.id != 728 && button.id != 725) return;
        int id = button.id - 720;
        setSubGui(new SubGuiRoleJobSelect(getButton(id), id == 8, value -> {
            GuiNpcButton original = getButton(id);
            if (value != original.getValue()) { original.setDisplay(value); func_146284_a(original); func_73866_w_(); }
        })); ci.cancel();
    }
}
