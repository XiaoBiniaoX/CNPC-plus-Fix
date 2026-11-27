package top.cnpcplus.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ICustomScrollListener;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;
import top.cnpcplus.quest.QuestCraft;

/** Edits a private copy; the craft task is changed only by Apply. */
final class SubGuiQuestCraftItems extends GuiNPCInterface implements ICustomScrollListener, ITextfieldListener {
    private record Choice(String id, String name) {}
    private static final ResourceLocation SLOT = new ResourceLocation("customnpcs", "textures/gui/slot.png");

    private final QuestCraft craft;
    private final ItemStack[] draft = new ItemStack[9];
    private final List<Choice> allItems = new ArrayList<>();
    private List<Choice> shownItems = List.of();
    private GuiCustomScrollNop itemsScroll;
    private int selectedSlot;
    private boolean ignoreDamage;
    private boolean ignoreNBT;

    SubGuiQuestCraftItems(QuestCraft craft) {
        this.craft = craft;
        imageWidth = 372;
        imageHeight = 240;
        setBackground("menubg.png");
        ignoreDamage = craft.ignoreDamage;
        ignoreNBT = craft.ignoreNBT;
        for (int i = 0; i < 9; i++) draft[i] = craft.items.items.get(i).copy();
        BuiltInRegistries.ITEM.forEach(item -> {
            if (item != Items.AIR) {
                var id = BuiltInRegistries.ITEM.getKey(item);
                if (id != null) allItems.add(new Choice(id.toString(), Component.translatable(item.getDescriptionId()).getString()));
            }
        });
        allItems.sort(java.util.Comparator.comparing(Choice::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Choice::id));
        shownItems = allItems;
    }

    @Override
    public void m_7856_() {
        super.m_7856_();
        for (int i = 0; i < 9; i++) {
            int x = guiLeft + 18 + (i % 3) * 25;
            int y = guiTop + 42 + (i / 3) * 25;
            addButton(new GuiButtonNop(this, 10 + i, x, y, 20, 20, " "));
        }
        if (itemsScroll == null) itemsScroll = new GuiCustomScrollNop(this, 0);
        itemsScroll.disabledSearch();
        itemsScroll.setSize(248, 112);
        itemsScroll.guiLeft = guiLeft + 112;
        itemsScroll.guiTop = guiTop + 77;
        itemsScroll.setUnsortedList(shownItems.stream().map(item -> item.name() + "  §8" + item.id()).toList());
        addScroll(itemsScroll);
        GuiTextFieldNop search = new GuiTextFieldNop(0, this, guiLeft + 112, guiTop + 53, 248, 20, "");
        search.setResponder(this::filterItems);
        addTextField(search);
        addButton(new GuiButtonNop(this, 1, guiLeft + 112, guiTop + 194, 112, 20, "cnpcplus.quest.craft.use_held"));
        addButton(new GuiButtonNop(this, 2, guiLeft + 228, guiTop + 194, 66, 20, "cnpcplus.quest.craft.clear"));
        GuiButtonNop damage = new GuiButtonNop(this, 5, guiLeft + 12, guiTop + 157, 92, 20, matchDamageText());
        damage.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("cnpcplus.quest.craft.match.damage.tooltip")));
        addButton(damage);
        GuiButtonNop nbt = new GuiButtonNop(this, 6, guiLeft + 12, guiTop + 181, 92, 20, matchNbtText());
        nbt.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("cnpcplus.quest.craft.match.nbt.tooltip")));
        addButton(nbt);
        addButton(new GuiButtonNop(this, 3, guiLeft + 12, guiTop + 218, 74, 20, "gui.cancel"));
        addButton(new GuiButtonNop(this, 4, guiLeft + imageWidth - 86, guiTop + 218, 74, 20, "gui.done"));
    }

    private String matchDamageText() {
        return I18n.get(ignoreDamage ? "cnpcplus.quest.craft.match.damage.ignore" : "cnpcplus.quest.craft.match.damage.keep");
    }

    private String matchNbtText() {
        return I18n.get(ignoreNBT ? "cnpcplus.quest.craft.match.nbt.ignore" : "cnpcplus.quest.craft.match.nbt.keep");
    }

    private void filterItems(String value) {
        String query = value.trim().toLowerCase(java.util.Locale.ROOT);
        shownItems = query.isEmpty() ? allItems : allItems.stream()
                .filter(item -> item.id().toLowerCase(java.util.Locale.ROOT).contains(query)
                        || item.name().toLowerCase(java.util.Locale.ROOT).contains(query)).toList();
        itemsScroll.setUnsortedList(shownItems.stream().map(item -> item.name() + "  §8" + item.id()).toList());
    }

    @Override public void unFocused(GuiTextFieldNop field) {}

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id >= 10 && button.id < 19) selectedSlot = button.id - 10;
        else if (button.id == 1 && Minecraft.getInstance().player != null) {
            draft[selectedSlot] = Minecraft.getInstance().player.getMainHandItem().copy();
        } else if (button.id == 2) draft[selectedSlot] = ItemStack.EMPTY;
        else if (button.id == 5) {
            ignoreDamage = !ignoreDamage;
            button.setDisplayText(matchDamageText());
        } else if (button.id == 6) {
            ignoreNBT = !ignoreNBT;
            button.setDisplayText(matchNbtText());
        }
        else if (button.id == 4) {
            for (int i = 0; i < 9; i++) craft.items.items.set(i, draft[i].copy());
            craft.ignoreDamage = ignoreDamage;
            craft.ignoreNBT = ignoreNBT;
            close();
        } else if (button.id == 3) close();
    }

    @Override
    public void scrollClicked(double x, double y, int button, GuiCustomScrollNop scroll) {
        int index = scroll.getSelectedIndex();
        if (index < 0 || index >= shownItems.size()) return;
        var item = BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(shownItems.get(index).id()));
        if (item != Items.AIR) draft[selectedSlot] = new ItemStack(item);
    }

    @Override public void scrollDoubleClicked(String selected, GuiCustomScrollNop scroll) {
        scrollClicked(0, 0, 0, scroll);
    }

    @Override
    public void m_88315_(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.m_88315_(graphics, mouseX, mouseY, partialTick);
        if (hasSubGui()) return;
        graphics.drawString(font, I18n.get("cnpcplus.quest.craft.items.title"), guiLeft + 12, guiTop + 10, 0x202020, false);
        graphics.drawWordWrap(font, Component.translatable("cnpcplus.quest.craft.items.help"), guiLeft + 112, guiTop + 18, 248, 0x555555);
        graphics.drawWordWrap(font, Component.translatable("cnpcplus.quest.craft.match.help"), guiLeft + 12, guiTop + 125, 92, 0x555555);
        for (int i = 0; i < 9; i++) {
            int x = guiLeft + 18 + (i % 3) * 25;
            int y = guiTop + 42 + (i / 3) * 25;
            graphics.fill(x, y, x + 20, y + 20, 0xff171717);
            graphics.blit(SLOT, x + 1, y + 1, 0, 0, 18, 18);
            if (!draft[i].isEmpty()) graphics.renderItem(draft[i], x + 2, y + 2);
            if (i == selectedSlot) graphics.renderOutline(x, y, 20, 20, 0xfff4f0dc);
        }
        if (mouseX >= guiLeft + 18 && mouseX < guiLeft + 87 && mouseY >= guiTop + 42 && mouseY < guiTop + 111) {
            int col = (mouseX - guiLeft - 18) / 25;
            int row = (mouseY - guiTop - 42) / 25;
            int x = guiLeft + 18 + col * 25, y = guiTop + 42 + row * 25;
            if (col < 3 && row < 3 && mouseX < x + 20 && mouseY < y + 20 && !draft[row * 3 + col].isEmpty())
                graphics.renderTooltip(font, draft[row * 3 + col], mouseX, mouseY);
        }
    }
}
