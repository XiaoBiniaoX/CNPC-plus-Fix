package top.cnpcplus.mixin;

import java.math.BigDecimal;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import noppes.npcs.client.gui.custom.GuiCreationNewParts;
import noppes.npcs.client.gui.custom.GuiCustom;
import noppes.npcs.client.parts.MpmPartData;
import noppes.npcs.containers.ContainerCustomGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.client.gui.NativeSearchField;
import top.cnpcplus.model.PartTransform;

@Mixin(targets = "noppes.npcs.client.gui.custom.GuiCreationNewParts$TexturePart", remap = false)
public abstract class MixinGuiPartTransform extends GuiCustom {
    @Shadow private MpmPartData data;
    @Shadow @Final private GuiCreationNewParts this$0;
    @Unique private static final Pattern cnpcplus$decimal = Pattern.compile("-?[0-9]*(?:\\.[0-9]*)?");
    @Unique private final NativeSearchField[] cnpcplus$fields = new NativeSearchField[6];
    @Unique private boolean cnpcplus$labelsAdded;
    @Unique private boolean cnpcplus$changed;

    protected MixinGuiPartTransform(ContainerCustomGui menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "m_7856_", at = @At("HEAD"), require = 1)
    private void cnpcplus$addLabels(CallbackInfo ci) {
        if (cnpcplus$labelsAdded) return;
        cnpcplus$labelsAdded = true;
        guiWrapper.addLabel(191, "cnpcplus.parts.scale", 84, 28, 130, 10);
        guiWrapper.addLabel(192, "cnpcplus.parts.offset", 84, 63, 130, 10);
        for (int i = 0; i < 6; i++) {
            int x = 84 + i % 3 * 70;
            int y = i < 3 ? 39 : 74;
            guiWrapper.addLabel(193 + i, "XYZ".substring(i % 3, i % 3 + 1), x, y + 5, 10, 10);
        }
    }

    @Inject(method = "m_7856_", at = @At("TAIL"), require = 1)
    private void cnpcplus$addTransformFields(CallbackInfo ci) {
        for (var component : components.components.values()) {
            if (component instanceof GuiPartPreviewAccess preview) preview.cnpcplus$partData(data);
        }
        PartTransform transform = ((PartTransform.Access) data).cnpcplus$partTransform();
        for (int i = 0; i < 6; i++) {
            int axis = i;
            int x = getGuiLeft() + 96 + i % 3 * 70;
            int y = getGuiTop() + (i < 3 ? 39 : 74);
            if (cnpcplus$fields[i] != null) removeWidget(cnpcplus$fields[i]);
            String initial = new BigDecimal(Float.toString(transform.get(i))).stripTrailingZeros().toPlainString();
            var field = new NativeSearchField(199 + i, this, x, y, 48, initial);
            field.setMaxLength(20);
            field.setFilter(value -> cnpcplus$decimal.matcher(value).matches());
            field.setResponder(value -> {
                if (value.chars().anyMatch(Character::isDigit)) {
                    transform.set(axis, Float.parseFloat(value));
                    cnpcplus$changed = true;
                    cnpcplus$applyTransform();
                }
            });
            cnpcplus$fields[i] = addRenderableWidget(field);
        }
    }

    @Unique private void cnpcplus$applyTransform() {
        if (!cnpcplus$changed) return;
        var edited = ((PartTransform.Access) data).cnpcplus$partTransform();
        // Native model acknowledgements replace the list while this subpage keeps its original part.
        for (var part : ((GuiCreationPartsAccess) this$0).cnpcplus$modelData().mpmParts) {
            if (!part.partId.equals(data.partId)) continue;
            var current = ((PartTransform.Access) part).cnpcplus$partTransform();
            for (int axis = 0; axis < 6; axis++) current.set(axis, edited.get(axis));
            break;
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        cnpcplus$applyTransform();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Inject(method = "m_7379_", at = @At("HEAD"), require = 1)
    private void cnpcplus$flushTransform(CallbackInfo ci) {
        cnpcplus$applyTransform();
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (getFocused() instanceof NativeSearchField field && !field.isMouseOver(x, y)) {
            setFocused((GuiEventListener) null);
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        // AbstractContainerScreen handles slot dragging without forwarding to the focused box.
        if (isDragging() && getFocused() instanceof NativeSearchField field && field.mouseDragged(x, y, button, dx, dy)) {
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
}
