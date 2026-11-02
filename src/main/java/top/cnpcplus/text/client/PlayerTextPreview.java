package top.cnpcplus.text.client;

import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.CustomEntities;
import noppes.npcs.CustomNpcs;
import noppes.npcs.client.gui.player.GuiDialogInteract;
import noppes.npcs.client.gui.player.GuiQuestCompletion;
import noppes.npcs.client.gui.player.GuiQuestLog;
import noppes.npcs.client.gui.player.moderngui.GuiDialogModern;
import noppes.npcs.client.gui.player.moderngui.GuiQuestModern;
import noppes.npcs.controllers.data.Dialog;
import noppes.npcs.controllers.data.Quest;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import top.cnpcplus.gui.ModernDialogScrollAccess;
import top.cnpcplus.text.PreviewTextData;
import top.cnpcplus.text.StyledText;

/** Actual player screens, with detached drafts and no native dialog/quest actions. */
public final class PlayerTextPreview {
    private PlayerTextPreview() {}

    public static Screen dialog(EntityNPCInterface source, Dialog dialog, StyledText text) {
        EntityNPCInterface npc = npc(source);
        if (npc == null) return null;
        Dialog copy = PreviewTextData.dialog(dialog, text);
        if (!CustomNpcs.EnableNewDialogSystem) return new TraditionalDialog(npc, copy);
        // PacketDialog.openDialog sends a quest dialog to GuiQuestModern first; the accept button then
        // opens GuiDialogModern locally (optionId -2 sends nothing).
        Quest quest = copy.getQuest();
        return quest == null ? new ModernDialog(npc, copy) : new QuestOffer(npc, quest, copy);
    }

    public static Screen quest(Quest source, StyledText text, boolean log) {
        if (Minecraft.getInstance().player == null) return null;
        Quest copy = PreviewTextData.quest(source, text, log);
        return log ? new QuestLog(copy) : new Completion(copy);
    }

    private static EntityNPCInterface npc(EntityNPCInterface source) {
        CompoundTag snapshot = source == null ? null : source.writeSpawnData().copy();
        if (snapshot != null) snapshot.putString("CNPCPlusPreviewType", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(source.getType()).toString());
        EntityNPCInterface copy = npc(snapshot);
        if (copy != null && source != null) copy.setPos(source.getX(), source.getY(), source.getZ());
        return copy;
    }

    public static EntityNPCInterface npc(CompoundTag data) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        EntityNPCInterface copy = null;
        if (data != null) {
            var type = net.minecraft.world.entity.EntityType.byString(data.getString("CNPCPlusPreviewType")).orElse(null);
            // Only registered CNPC entity types may be instantiated, never arbitrary server-provided entities.
            if (type != null && "customnpcs".equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace())
                    && type.create(mc.level) instanceof EntityNPCInterface npc) copy = npc;
        }
        if (copy == null) copy = new EntityCustomNpc(CustomEntities.entityCustomNpc, mc.level);
        if (data != null) {
            CompoundTag snapshot = data.copy();
            snapshot.putInt("Role", 0);
            if (snapshot.getInt("Job") != 9) snapshot.putInt("Job", 0); // Puppet visuals only, never bard playback.
            copy.readSpawnData(snapshot);
        }
        return copy; // Never add this entity to a world, tick it, or load scripts into it.
    }

    private static void back(GuiBasic gui) {
        gui.closeOnEsc = true;
        gui.addButton(new GuiButtonNop(gui, 900, 8, 8, 110, 20, "cnpcplus.text.backToEdit"));
    }

    private static boolean clickBack(GuiBasic gui, double x, double y, int button) {
        GuiButtonNop back = gui.getButton(900);
        if (button == 0 && back != null && x >= back.getX() && x < back.getX() + back.getWidth()
                && y >= back.getY() && y < back.getY() + back.getHeight()) gui.close();
        return true;
    }

    private static boolean key(GuiBasic gui, int key) {
        if (key == 256 || gui.isInventoryKey(key)) gui.close();
        return true;
    }

    public static final class TraditionalDialog extends GuiDialogInteract {
        TraditionalDialog(EntityNPCInterface npc, Dialog dialog) { super(npc, dialog); }
        @Override public void grabMouse(boolean grab) {} // Virtual call also happens during super construction.
        @Override public void m_7856_() { super.m_7856_(); back(this); }
        @Override public boolean m_6375_(double x, double y, int button) { return clickBack(this, x, y, button); }
        @Override public boolean m_7933_(int key, int scan, int modifiers) { return key(this, key); }
        @Override public void buttonEvent(GuiButtonNop button) { if (button.id == 900) close(); }
    }

    public static final class ModernDialog extends GuiDialogModern {
        ModernDialog(EntityNPCInterface npc, Dialog dialog) { super(npc, dialog); }
        @Override public void grabMouse(boolean grab) {}
        @Override public void m_7856_() { super.m_7856_(); back(this); }
        @Override public boolean m_6375_(double x, double y, int button) {
            // The subclass override would otherwise hide MixinGuiDialogModernScroll's HEAD injection.
            if (((ModernDialogScrollAccess)(Object)this).cnpcplus$scrollbarClick(x, y, button)) return true;
            return clickBack(this, x, y, button);
        }
        @Override public boolean m_7933_(int key, int scan, int modifiers) { return key(this, key); }
        @Override public void buttonEvent(GuiButtonNop button) { if (button.id == 900) close(); }
    }

    /**
     * Quest dialogs never show GuiDialogModern first: the native screen is the quest offer, whose accept
     * button re-opens the dialog locally with optionId -2 (no packet). Mirror that, minus every packet.
     */
    public static final class QuestOffer extends GuiQuestModern {
        private final EntityNPCInterface npc;
        private final Dialog dialog;

        QuestOffer(EntityNPCInterface npc, Quest quest, Dialog dialog) {
            super(npc, quest, dialog, -2);
            this.npc = npc;
            this.dialog = dialog;
        }

        @Override public void grabMouse(boolean grab) {}
        @Override public void m_7856_() { super.m_7856_(); back(this); }

        @Override public boolean m_6375_(double x, double y, int button) {
            double scale = Math.max(1.0E-4D, (double) width / 960.0D);
            double gx = x / scale;
            double gy = y / scale;
            if (button == 0) {
                if (inside(720, 326, 78, 20, gx, gy)) { close(); return true; }
                if (inside(812, 326, 78, 20, gx, gy)) { openDialogPage(); return true; }
            }
            return clickBack(this, x, y, button);
        }

        /** Replace this page in the hosting editor, exactly like a follow-up screen; never send a packet. */
        private void openDialogPage() {
            ModernDialog next = new ModernDialog(npc, dialog);
            Screen parent = getParent();
            if (parent instanceof GuiBasic host && parent != this) host.setSubGui(next);
            else Minecraft.getInstance().setScreen(next);
        }

        @Override public boolean m_7933_(int key, int scan, int modifiers) { return key(this, key); }
        @Override public void buttonEvent(GuiButtonNop button) { if (button.id == 900) close(); }
    }

    private static boolean inside(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public static final class QuestLog extends GuiQuestLog {
        public final Quest draft;
        QuestLog(Quest quest) { super(Minecraft.getInstance().player); draft = quest; }
        @Override public void m_7856_() {
            activeQuests.clear();
            super.m_7856_();
            // Native inventory tabs jump to real player screens; keep only this screen's page buttons.
            for (var child : new ArrayList<>(children())) if (!(child instanceof GuiButtonNop)) removeWidget(child);
            if (selectedQuest == null) {
                var scroll = getScroll(0);
                scroll.setSelected(draft.title);
                scrollClicked(0, 0, 0, scroll);
                return;
            }
            back(this);
        }
        @Override public void buttonEvent(GuiButtonNop button) { if (button.id == 900) close(); }
    }

    public static final class Completion extends GuiQuestCompletion {
        Completion(Quest quest) { super(quest); }
        @Override public void m_7856_() { super.m_7856_(); closeOnEsc = true; }
        @Override public void buttonEvent(GuiButtonNop button) {
            if (button.id == 0) close(); // Do not call the real quest-completion packet handler.
            else super.buttonEvent(button); // Existing text pagination remains available.
        }
    }
}
