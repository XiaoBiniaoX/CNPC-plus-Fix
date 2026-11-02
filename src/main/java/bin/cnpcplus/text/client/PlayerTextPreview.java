package bin.cnpcplus.text.client;

import bin.cnpcplus.text.StyledText;
import bin.cnpcplus.text.TextStyles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.client.gui.player.GuiDialogInteract;
import noppes.npcs.client.gui.player.GuiQuestCompletion;
import noppes.npcs.client.gui.player.GuiQuestLog;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.controllers.data.Dialog;
import noppes.npcs.controllers.data.Quest;
import noppes.npcs.controllers.data.QuestCategory;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.entity.EntityNPCInterface;

/** Detached drafts in the actual player screens; never submit dialog/quest actions. */
public final class PlayerTextPreview {
    private PlayerTextPreview() { }
    public static GuiScreen dialog(EntityNPCInterface source, Dialog sourceDialog, StyledText text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return null;
        EntityCustomNpc npc = new EntityCustomNpc(mc.world);
        if (source != null) {
            NBTTagCompound visual = source.writeSpawnData().copy();
            visual.setInteger("Role", 0);
            if (visual.getInteger("Job") != 9) visual.setInteger("Job", 0);
            npc.readSpawnData(visual); npc.setPosition(source.posX, source.posY, source.posZ);
        }
        Dialog draft = new Dialog(sourceDialog.category);
        draft.readNBT(sourceDialog.writeToNBT(new NBTTagCompound()).copy());
        draft.text = text.text; draft.sound = ""; draft.command = ""; draft.disableEsc = false;
        TextStyles.put(draft, TextStyles.DIALOG, text);
        return new DialogPage(npc, draft, mc.currentScreen);
    }
    public static GuiScreen quest(Quest source, StyledText text, boolean log) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return null;
        Quest draft = new Quest(new QuestCategory());
        draft.category.title = source.category == null ? "" : source.category.title;
        draft.readNBT(source.writeToNBT(new NBTTagCompound()).copy());
        if (log) draft.logText = text.text; else draft.completeText = text.text;
        TextStyles.put(draft, log ? TextStyles.LOG : TextStyles.COMPLETE, text);
        return log ? new LogPage(draft, mc.currentScreen) : new CompletionPage(draft, mc.currentScreen);
    }
    public static final class DialogPage extends GuiDialogInteract {
        private final GuiScreen editor;
        private DialogPage(EntityNPCInterface npc, Dialog draft, GuiScreen editor) { super(npc, draft); this.editor = editor; }
        @Override public void grabMouse(boolean grab) { }
        @Override public void initGui() { super.initGui(); addButton(new GuiNpcButton(900, 8, 8, 110, 20, "cnpcplus.text.backToEdit")); }
        @Override public void mouseClicked(int x, int y, int button) {
            if (button == 0 && x >= 8 && x < 118 && y >= 8 && y < 28) close();
        }
        @Override public void keyTyped(char c, int key) { if (key == 1 || isInventoryKey(key)) close(); }
        @Override public void close() { mc.displayGuiScreen(editor); }
        @Override public void save() { }
    }
    public static final class LogPage extends GuiQuestLog {
        public final Quest draft;
        private final GuiScreen editor;
        private LogPage(Quest draft, GuiScreen editor) { super(Minecraft.getMinecraft().player); this.draft = draft; this.editor = editor; }
        @Override public void initGui() {
            activeQuests.clear(); super.initGui();
            if (selectedQuest == null) {
                getScroll(0).setSelected(draft.title);
                scrollClicked(0, 0, 0, getScroll(0)); return;
            }
            addButton(new GuiNpcButton(900, 8, 8, 110, 20, "cnpcplus.text.backToEdit"));
        }
        @Override protected void actionPerformed(GuiButton button) {
            if (button.id == 900) close(); else super.func_146284_a(button);
        }
        @Override public void mouseClicked(int x, int y, int button) { super.func_73864_a(x, y, button); }
        @Override public void keyTyped(char c, int key) { if (key == 1 || isInventoryKey(key)) close(); }
        @Override public void close() { Minecraft.getMinecraft().displayGuiScreen(editor); }
        @Override public void save() { }
    }
    public static final class CompletionPage extends GuiQuestCompletion {
        private final GuiScreen editor;
        private CompletionPage(Quest draft, GuiScreen editor) { super(draft); this.editor = editor; }
        @Override public void initGui() { super.initGui(); getButton(0).displayString = net.minecraft.client.resources.I18n.format("cnpcplus.text.backToEdit"); }
        @Override protected void actionPerformed(GuiButton button) { if (button.id == 0) close(); }
        @Override public void mouseClicked(int x, int y, int button) { super.func_73864_a(x, y, button); }
        @Override public void keyTyped(char c, int key) { if (key == 1 || isInventoryKey(key)) close(); }
        @Override public void close() { mc.displayGuiScreen(editor); }
        @Override public void save() { }
    }
}
