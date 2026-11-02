package bin.cnpcplus.mixin.lifecycle;

import bin.cnpcplus.data.NpcHitboxStore;
import java.util.Collections;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.client.config.GuiUtils;
import noppes.npcs.client.gui.mainmenu.GuiNpcDisplay;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNpcDisplay.class, remap = false)
public abstract class MixinGuiNpcDisplayHitbox extends GuiNPCInterface2 {
    protected MixinGuiNpcDisplayHitbox(noppes.npcs.entity.EntityNPCInterface npc) { super(npc); }
    @Inject(method = "func_73866_w_", at = @At("TAIL"), require = 1)
    private void cnpcplus$fields(CallbackInfo ci) {
        getButton(7).width = 42;
        GuiButton button = getButton(13);
        buttonList.remove(button);
        addButton(new GuiNpcButton(13, guiLeft + 208, button.y, 65, 20,
                new String[]{"gui.yes", "gui.no", "cnpcplus.hitbox.solid"}, NpcHitboxStore.mode(npc.display)));
        addLabel(new GuiNpcLabel(13, "display.interactable", guiLeft + 171, button.y + 5));
        if (!npc.display.getHasHitbox()) return;
        NpcHitboxStore.Settings box = NpcHitboxStore.get(npc.display);
        float[] values = {box.length, box.width, box.height};
        String[] axes = {"length", "width", "height"};
        for (int i = 0; i < 3; i++) {
            int x = guiLeft + 277 + i * 45;
            addLabel(new GuiNpcLabel(410 + i, "cnpcplus.hitbox." + axes[i], x, button.y + 5));
            addTextField(new GuiNpcTextField(410 + i, this, fontRenderer, x + 13, button.y, 29, 20, Float.toString(values[i])));
        }
    }
    @Inject(method = "unFocused", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$dimension(GuiNpcTextField field, CallbackInfo ci) {
        int axis = field.getId() - 410;
        if (axis < 0 || axis > 2) return;
        NpcHitboxStore.Settings old = NpcHitboxStore.get(npc.display);
        float value;
        try { value = Float.parseFloat(field.getText()); }
        catch (NumberFormatException ignored) { value = axis == 0 ? old.length : axis == 1 ? old.width : old.height; }
        NpcHitboxStore.Settings next = new NpcHitboxStore.Settings(axis == 0 ? value : old.length,
                axis == 1 ? value : old.width, axis == 2 ? value : old.height);
        NpcHitboxStore.put(npc.display, next); npc.updateHitbox();
        field.setText(Float.toString(axis == 0 ? next.length : axis == 1 ? next.width : next.height)); ci.cancel();
    }
    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$toggle(GuiButton button, CallbackInfo ci) {
        if (button.id == 13) {
            GuiNpcTextField.unfocus();
            NpcHitboxStore.setMode(npc, ((GuiNpcButton) button).getValue());
            initGui(); ci.cancel();
        }
    }
    @Override public void drawScreen(int mx, int my, float partial) {
        super.drawScreen(mx, my, partial);
        if (hasSubGui() || !npc.display.getHasHitbox()) return;
        GuiButton button = getButton(13);
        if (mx >= guiLeft + 277 && mx < guiLeft + 412 && my >= button.y && my < button.y + 20) {
            String[] axes = {"length", "width", "height"};
            int axis = (mx - guiLeft - 277) / 45;
            GuiUtils.drawHoveringText(Collections.singletonList(I18n.format("cnpcplus.hitbox." + axes[axis] + ".tooltip")),
                    mx, my, width, height, 230, getFontRenderer());
        }
    }
}
