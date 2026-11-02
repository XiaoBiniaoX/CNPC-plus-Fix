package top.cnpcplus.text.client;

import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import noppes.npcs.CustomEntities;
import noppes.npcs.client.gui.player.GuiDialogInteract;
import noppes.npcs.client.gui.player.GuiQuestCompletion;
import noppes.npcs.client.gui.player.GuiQuestLog;
import noppes.npcs.controllers.data.Dialog;
import noppes.npcs.controllers.data.Quest;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import top.cnpcplus.text.PreviewTextData;
import top.cnpcplus.text.StyledText;

/** Actual player screens, without dialog/quest action packets or a ticking preview entity. */
public final class PlayerTextPreview {
    private PlayerTextPreview() {}

    public static Screen dialog(EntityNPCInterface source, Dialog dialog, StyledText text) {
        CompoundTag snapshot = source == null ? null : source.writeSpawnData().copy();
        if (snapshot != null) snapshot.putString("CNPCPlusPreviewType", BuiltInRegistries.ENTITY_TYPE.getKey(source.getType()).toString());
        EntityNPCInterface npc = npc(snapshot);
        if (npc == null) return null;
        if (source != null) npc.setPos(source.getX(), source.getY(), source.getZ());
        return new TraditionalDialog(npc, PreviewTextData.dialog(Minecraft.getInstance().player.registryAccess(), dialog, text));
    }

    public static Screen quest(Quest source, StyledText text, boolean log) {
        if (Minecraft.getInstance().player == null) return null;
        Quest copy = PreviewTextData.quest(Minecraft.getInstance().player.registryAccess(), source, text, log);
        return log ? new QuestLog(copy) : new Completion(copy);
    }

    public static EntityNPCInterface npc(CompoundTag data) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        EntityNPCInterface copy = null;
        if (data != null) {
            var type = EntityType.byString(data.getString("CNPCPlusPreviewType")).orElse(null);
            if (type != null && "customnpcs".equals(BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace())
                    && type.create(mc.level) instanceof EntityNPCInterface npc) copy = npc;
        }
        if (copy == null) copy = new EntityCustomNpc(CustomEntities.entityCustomNpc, mc.level);
        if (data != null) {
            CompoundTag snapshot = data.copy();
            snapshot.putInt("Role", 0);
            if (snapshot.getInt("Job") != 9) snapshot.putInt("Job", 0);
            copy.readSpawnData(snapshot);
        }
        return copy; // Never add to the world, tick it, or load its scripts.
    }

    private static void back(GuiBasic gui) {
        gui.closeOnEsc = true;
        gui.addButton(new GuiButtonNop(gui, 900, 8, 8, 110, 20, "cnpcplus.text.backToEdit"));
    }

    public static final class TraditionalDialog extends GuiDialogInteract {
        TraditionalDialog(EntityNPCInterface npc, Dialog dialog) { super(npc, dialog); }
        @Override public void grabMouse(boolean grab) {}
        @Override public void init() { super.init(); back(this); }
        @Override public boolean mouseClicked(double x, double y, int button) {
            GuiButtonNop back = getButton(900);
            if (button == 0 && back != null && x >= back.getX() && x < back.getX() + back.getWidth()
                    && y >= back.getY() && y < back.getY() + back.getHeight()) close();
            return true;
        }
        @Override public boolean keyPressed(int key, int scan, int modifiers) {
            if (key == 256 || isInventoryKey(key)) close();
            return true;
        }
        @Override public void buttonEvent(GuiButtonNop button) { if (button.id == 900) close(); }
    }

    public static final class QuestLog extends GuiQuestLog {
        public final Quest draft;
        QuestLog(Quest quest) { super(Minecraft.getInstance().player); draft = quest; }
        @Override public void init() {
            activeQuests.clear();
            super.init();
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
        @Override public void init() { super.init(); closeOnEsc = true; }
        @Override public void buttonEvent(GuiButtonNop button) {
            if (button.id == 0) close();
            else super.buttonEvent(button);
        }
    }
}
