package top.cnpcplus.mixin;

import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.gui.mainmenu.GuiNpcDisplay;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.data.NpcHitboxStore;

@Mixin(value = GuiNpcDisplay.class, remap = false)
public abstract class MixinGuiNpcDisplayHitbox {
    @Unique private static final int CNPCPLUS_BOX_FIRST = 410;
    @Unique private static final int CNPCPLUS_SHADOW_FIRST = 430;
    @Unique private GuiLabel[] cnpcplus$hitboxLabels;

    @Inject(method = "init", at = @At("TAIL"), require = 1)
    private void cnpcplus$addHitboxFields(CallbackInfo ci) {
        GuiNpcDisplay self = (GuiNpcDisplay) (Object) this;
        GuiButtonNop button = self.getButton(13);
        if (button == null) return;
        button.setX(self.guiLeft + 198);
        button.setWidth(82);
        GuiButtonNop availability = self.getButton(16);
        if (availability != null) availability.setWidth(64);
        GuiLabel hitbox = self.wrapper.labels.get(13);
        if (hitbox != null) hitbox.setX(self.guiLeft + 158);
        NpcHitboxStore.Settings settings = NpcHitboxStore.get(self.npc.display);
        float[] values = {settings.length(), settings.width(), settings.height()};
        String[] names = {"length", "width", "height"};
        cnpcplus$hitboxLabels = new GuiLabel[3];
        for (int i = 0; i < 3; i++) {
            int id = CNPCPLUS_BOX_FIRST + i;
            int x = self.guiLeft + 282 + i * 44;
            String key = "cnpcplus.hitbox." + names[i];
            GuiLabel label = new GuiLabel(id, key, x, button.getY() + 5, key + ".tooltip");
            cnpcplus$hitboxLabels[i] = label;
            self.addLabel(label);
            GuiTextFieldNop field = new GuiTextFieldNop(id, self, x + 12, button.getY(), 30, 20, Float.toString(values[i]));
            field.setFloatsOnly().setMinMaxDefault(0, NpcHitboxStore.MAX, 0);
            field.setTooltip(Tooltip.create(Component.translatable(key + ".tooltip")));
            final int axis = i;
            field.setResponder(text -> {
                try { cnpcplus$setAxis(axis, Float.parseFloat(text)); }
                catch (NumberFormatException ignored) { /* Keep the last valid value while typing. */ }
            });
            self.addTextField(field);
        }
        int y = button.getY() + 23;
        self.addLabel(new GuiLabel(CNPCPLUS_SHADOW_FIRST, "cnpcplus.hitbox.shadow", self.guiLeft + 180, y + 5));
        for (int i = 0; i <= 4; i++) {
            GuiButtonNop shadow = new GuiButtonNop(self, CNPCPLUS_SHADOW_FIRST + i,
                    self.guiLeft + 230 + i * 36, y, 34, 20, i * 25 + "%");
            shadow.setTooltip(Tooltip.create(Component.translatable("cnpcplus.hitbox.shadow.tooltip")));
            shadow.setEnabled(Math.abs(settings.shadowScale() - i * 0.25f) > 0.001f);
            self.addButton(shadow);
        }
        cnpcplus$showHitboxFields();
    }

    @Unique private void cnpcplus$setAxis(int axis, float value) {
        GuiNpcDisplay self = (GuiNpcDisplay) (Object) this;
        NpcHitboxStore.Settings old = NpcHitboxStore.get(self.npc.display);
        NpcHitboxStore.Settings next = new NpcHitboxStore.Settings(
                axis == 0 ? value : old.length(), axis == 1 ? value : old.width(), axis == 2 ? value : old.height(), old.shadowScale());
        if (old.equals(next)) return;
        NpcHitboxStore.put(self.npc.display, next);
        self.npc.refreshDimensions();
        self.npc.setPos(self.npc.getX(), self.npc.getY(), self.npc.getZ());
    }

    @Unique private void cnpcplus$showHitboxFields() {
        GuiNpcDisplay self = (GuiNpcDisplay) (Object) this;
        boolean show = self.npc.display.getHitboxState() != 1;
        GuiTextFieldNop active = GuiTextFieldNop.getActive();
        if (!show && active != null && active.id >= CNPCPLUS_BOX_FIRST && active.id < CNPCPLUS_BOX_FIRST + 3) GuiTextFieldNop.unfocus();
        for (int i = 0; i < 3; i++) {
            GuiTextFieldNop field = self.getTextField(CNPCPLUS_BOX_FIRST + i);
            if (field != null) field.visible = field.active = field.enabled = show;
            if (cnpcplus$hitboxLabels != null) cnpcplus$hitboxLabels[i].visible = cnpcplus$hitboxLabels[i].enabled = show;
        }
    }

    @Inject(method = "unFocused", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$saveHitboxField(GuiTextFieldNop field, CallbackInfo ci) {
        int axis = field.id - CNPCPLUS_BOX_FIRST;
        if (axis < 0 || axis >= 3) return;
        cnpcplus$setAxis(axis, field.isFloat() ? field.getFloat() : 0);
        NpcHitboxStore.Settings values = NpcHitboxStore.get(((GuiNpcDisplay) (Object) this).npc.display);
        field.setValue(Float.toString(axis == 0 ? values.length() : axis == 1 ? values.width() : values.height()));
        ci.cancel();
    }

    @Inject(method = "buttonEvent", at = @At("TAIL"), require = 1)
    private void cnpcplus$toggleHitboxFields(GuiButtonNop button, CallbackInfo ci) {
        if (button.id == 13) cnpcplus$showHitboxFields();
        int index = button.id - CNPCPLUS_SHADOW_FIRST;
        if (index < 0 || index > 4) return;
        GuiNpcDisplay self = (GuiNpcDisplay) (Object) this;
        NpcHitboxStore.Settings old = NpcHitboxStore.get(self.npc.display);
        NpcHitboxStore.put(self.npc.display, new NpcHitboxStore.Settings(old.length(), old.width(), old.height(), index * 0.25f));
        for (int i = 0; i <= 4; i++) self.getButton(CNPCPLUS_SHADOW_FIRST + i).setEnabled(i != index);
    }
}
