package top.cnpcplus.transport.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import top.cnpcplus.transport.TransportFare;

public final class TransportFareConfirmation extends Screen {
    private static final String KEY = "cnpcplus.transporter.fare.";
    private static final int CELL_WIDTH = 40;
    private static final int CELL_HEIGHT = 32;
    private final Screen parent;
    private final List<ItemStack> costs;
    private final Runnable confirm;
    private Button confirmButton;

    public TransportFareConfirmation(Screen parent, String target, List<ItemStack> costs, Runnable confirm) {
        super(Component.translatable(KEY + "confirm", target));
        this.parent = parent;
        this.costs = costs;
        this.confirm = confirm;
    }

    @Override protected void init() {
        int y = height / 2 + 50;
        confirmButton = addRenderableWidget(Button.builder(CommonComponents.GUI_YES, button -> confirm.run())
                .bounds(width / 2 - 102, y, 98, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_NO, button -> onClose())
                .bounds(width / 2 + 4, y, 98, 20).build());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        var requirements = TransportFare.requirements(minecraft.player.getInventory(), costs);
        confirmButton.active = !requirements.isEmpty() && requirements.stream().allMatch(TransportFare.Requirement::affordable);
        var titleLines = font.split(title, width - 24);
        int top = height / 2 - 44;
        for (int i = 0; i < titleLines.size(); i++) {
            graphics.drawCenteredString(font, titleLines.get(i), width / 2, top - titleLines.size() * 10 + i * 10, 0xFFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        drawCosts(graphics, font, requirements, confirmButton.active, width, width / 2, top, mouseX, mouseY);
    }

    public static boolean drawCosts(GuiGraphics graphics, Font font, List<TransportFare.Requirement> requirements,
                                    boolean affordable, int width, int center, int top, int mouseX, int mouseY) {
        Component heading = Component.translatable(KEY + (affordable ? "amounts" : "missing"));
        int columns = Math.min(Math.max(1, requirements.size()), Math.max(1, (width - 16) / CELL_WIDTH));
        int panelWidth = Mth.clamp(Math.max(columns * CELL_WIDTH + 10, font.width(heading) + 14), 1, width - 8);
        int x = Mth.clamp(center - panelWidth / 2, 4, width - panelWidth - 4);
        int panelCenter = x + panelWidth / 2;
        int rows = (requirements.size() + columns - 1) / columns;
        int bottom = top + 20 + rows * CELL_HEIGHT;
        graphics.fill(x, top, x + panelWidth, bottom, 0xE6171717);
        graphics.fill(x, top, x + panelWidth, top + 1, affordable ? 0xFF8C8C8C : 0xFFC45B54);
        graphics.drawCenteredString(font, heading, panelCenter, top + 6, affordable ? 0xFFE3E3E3 : 0xFFFFA099);
        TransportFare.Requirement hovered = null;
        for (int i = 0; i < requirements.size(); i++) {
            var requirement = requirements.get(i);
            int rowSize = Math.min(columns, requirements.size() - i / columns * columns);
            int cellX = panelCenter - rowSize * CELL_WIDTH / 2 + i % columns * CELL_WIDTH;
            int cellY = top + 18 + i / columns * CELL_HEIGHT;
            int iconX = cellX + (CELL_WIDTH - 16) / 2;
            graphics.fill(iconX - 1, cellY - 1, iconX + 17, cellY + 17, 0xFF535353);
            graphics.renderItem(requirement.sample(), iconX, cellY);
            graphics.drawCenteredString(font, requirement.owned() + "/" + requirement.needed(), cellX + CELL_WIDTH / 2,
                    cellY + 19, requirement.affordable() ? 0xFFA5E4A5 : 0xFFFF8F87);
            if (mouseX >= cellX && mouseX < cellX + CELL_WIDTH && mouseY >= cellY && mouseY < cellY + CELL_HEIGHT) hovered = requirement;
        }
        if (hovered != null) {
            var stack = hovered.sample();
            var tooltip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
            tooltip.add(Component.translatable(KEY + "amountTooltip", hovered.owned(), hovered.needed()));
            graphics.renderTooltip(font, tooltip, stack.getTooltipImage(), stack, mouseX, mouseY);
        }
        return hovered != null;
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
