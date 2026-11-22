package top.cnpcplus.transport.client;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import top.cnpcplus.transport.TransportFare;

/** Template slots never modify the player's real inventory. */
final class TransportFareScreen extends Screen {
    private static final String KEY = "cnpcplus.transporter.fare.";
    private final Screen parent;
    private final Consumer<CompoundTag> save;
    private final List<ItemStack> costs;
    private boolean enabled;
    private ItemStack carried = ItemStack.EMPTY;

    TransportFareScreen(Screen parent, CompoundTag fare, Consumer<CompoundTag> save) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        this.save = save;
        this.costs = TransportFare.items(fare);
        this.enabled = fare.getBoolean("Enabled");
    }

    @Override protected void init() {
        addRenderableWidget(Button.builder(toggleLabel(), button -> {
            this.enabled = !this.enabled;
            button.setMessage(toggleLabel());
        }).bounds(width / 2 + 38, top() + 45, 85, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            if (enabled && costs.stream().allMatch(ItemStack::isEmpty)) return;
            save.accept(TransportFare.save(enabled, costs));
            onClose();
        }).bounds(width / 2 - 81, top() + 181, 78, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(width / 2 + 3, top() + 181, 78, 20).build());
    }

    private Component toggleLabel() {
        return Component.translatable(KEY + (enabled ? "enabled" : "disabled"));
    }

    private int top() { return (height - 208) / 2; }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int center = width / 2;
        int top = top();
        graphics.fill(center - 132, top, center + 132, top + 208, 0xFF101010);
        graphics.fill(center - 131, top + 1, center + 131, top + 207, 0xFF555555);
        graphics.fill(center - 131, top + 1, center + 129, top + 205, 0xFFFFFFFF);
        graphics.fill(center - 129, top + 3, center + 129, top + 205, 0xFFC6C6C6);
        graphics.drawString(font, title, center - 122, top + 9, 0x404040, false);
        graphics.drawString(font, Component.translatable("container.inventory"), center - 80, top + 80, 0x404040, false);
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < 45; i++) {
            boolean fee = i < TransportFare.SLOTS;
            int index = fee ? i : i - TransportFare.SLOTS;
            int columns = fee ? 3 : 9;
            int x = center - columns * 9 + index % columns * 18;
            int y = top + (fee ? 22 : 91) + index / columns * 18 + (!fee && index >= 27 ? 4 : 0);
            ItemStack stack = fee ? costs.get(index) : inventorySample(index);
            graphics.fill(x, y, x + 18, y + 18, 0xFFFFFFFF);
            graphics.fill(x, y, x + 17, y + 17, 0xFF373737);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
            boolean highlight = mouseX > x && mouseX < x + 17 && mouseY > y && mouseY < y + 17;
            if (highlight) graphics.fill(x + 1, y + 1, x + 17, y + 17, 0x70FFFFFF);
            graphics.renderItem(stack, x + 1, y + 1);
            graphics.renderItemDecorations(font, stack, x + 1, y + 1);
            if (highlight) hovered = stack;
        }
        boolean empty = enabled && costs.stream().allMatch(ItemStack::isEmpty);
        Component hint = Component.translatable(KEY + (empty ? "empty" : "help"));
        graphics.drawString(font, hint, center - font.width(hint) / 2, top + 170, empty ? 0x9F2020 : 0x404040, false);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!carried.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 250);
            graphics.renderItem(carried, mouseX - 8, mouseY - 8);
            graphics.renderItemDecorations(font, carried, mouseX - 8, mouseY - 8);
            graphics.pose().popPose();
        } else if (!hovered.isEmpty()) graphics.renderTooltip(font, hovered, mouseX, mouseY);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button != 0 && button != 1) return super.mouseClicked(x, y, button);
        int fee = slotAt(x, y, 3, 3, 22);
        if (fee >= 0) {
            ItemStack existing = costs.get(fee);
            if (carried.isEmpty()) {
                carried = existing.split(button == 0 ? existing.getCount() : (existing.getCount() + 1) / 2);
            } else if (existing.isEmpty()) {
                costs.set(fee, carried.split(button == 0 ? carried.getCount() : 1));
            } else if (ItemStack.isSameItemSameTags(carried, existing)) {
                int moved = Math.min(button == 0 ? carried.getCount() : 1, existing.getMaxStackSize() - existing.getCount());
                existing.grow(moved);
                carried.shrink(moved);
            } else if (button == 0) {
                costs.set(fee, carried);
                carried = existing;
            }
            return true;
        }
        int sample = slotAt(x, y, 9, 3, 91);
        int hotbar = slotAt(x, y, 9, 1, 149);
        if (hotbar >= 0) sample = 27 + hotbar;
        if (sample >= 0) {
            ItemStack stack = inventorySample(sample);
            carried = stack.copyWithCount(button == 0 ? stack.getCount() : (stack.getCount() + 1) / 2);
            return true;
        }
        if (super.mouseClicked(x, y, button)) return true;
        carried = ItemStack.EMPTY;
        return true;
    }

    @Override public boolean mouseScrolled(double x, double y, double delta) {
        int fee = slotAt(x, y, 3, 3, 22);
        if (fee < 0 || delta == 0 || costs.get(fee).isEmpty()) return false;
        var stack = costs.get(fee);
        stack.setCount(Math.max(1, Math.min(stack.getMaxStackSize(), stack.getCount() + (delta > 0 ? 1 : -1))));
        return true;
    }

    private ItemStack inventorySample(int index) {
        return minecraft.player.getInventory().getItem((index + 9) % 36);
    }

    private int slotAt(double x, double y, int columns, int rows, int offset) {
        x -= width / 2 - columns * 9;
        y -= top() + offset;
        return x >= 0 && x < columns * 18 && y >= 0 && y < rows * 18
                ? (int) y / 18 * columns + (int) x / 18 : -1;
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
