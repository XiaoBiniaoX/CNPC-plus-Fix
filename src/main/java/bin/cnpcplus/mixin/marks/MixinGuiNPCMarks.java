package bin.cnpcplus.mixin.marks;

import bin.cnpcplus.client.gui.SubGuiColorWheel;
import bin.cnpcplus.marks.MarkRotation;
import bin.cnpcplus.marks.client.MarkTypes;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.client.gui.SubGuiNpcAvailability;
import noppes.npcs.client.gui.advanced.GuiNPCMarks;
import noppes.npcs.client.gui.util.*;
import noppes.npcs.controllers.data.MarkData;
import noppes.npcs.entity.EntityNPCInterface;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Four native rows per page, independent editor draft and stationary selected-mark preview. */
@Mixin(value = GuiNPCMarks.class, remap = false)
public abstract class MixinGuiNPCMarks extends GuiNPCInterface2 {
    @Shadow private MarkData data;
    @Shadow private MarkData.Mark selectedMark;
    @Unique private int cnpcplus$page;
    @Unique private EntityNPCInterface cnpcplus$preview;
    protected MixinGuiNPCMarks(EntityNPCInterface npc) { super(npc); }
    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void cnpcplus$draft(EntityNPCInterface npc, CallbackInfo ci) {
        NBTTagCompound saved = data.getNBT(); data = new MarkData(); data.setNBT(saved);
        if (!data.marks.isEmpty()) selectedMark = data.marks.get(0);
    }
    @Inject(method = "func_73866_w_", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$rows(CallbackInfo ci) {
        super.initGui();
        cnpcplus$page = Math.max(0, Math.min(cnpcplus$page, Math.max(0, (data.marks.size() - 1) / 4)));
        for (int row = 0; row < 4 && cnpcplus$page * 4 + row < data.marks.size(); row++) {
            MarkData.Mark mark = data.marks.get(cnpcplus$page * 4 + row);
            int y = guiTop + 8 + row * 38, id = row * 10;
            int type = mark.type >= 0 && mark.type < MarkTypes.NAMES.length ? mark.type : 0;
            addButton(new GuiButtonBiDirectional(id + 1, guiLeft + 6, y, 90, 18, MarkTypes.NAMES, type));
            addButton(new GuiNpcButton(id + 2, guiLeft + 98, y, 49, 18, String.format("%06X", mark.color & 0xFFFFFF)));
            getButton(id + 2).setTextColor(mark.color & 0xFFFFFF);
            addButton(new GuiNpcButton(id + 3, guiLeft + 149, y, 91, 18, "availability.options"));
            addButton(new GuiNpcButton(id + 4, guiLeft + 242, y, 26, 18, "X"));
            MarkRotation rotation = (MarkRotation) mark;
            addButton(new GuiNpcButton(id + 5, guiLeft + 6, y + 19, 91, 18,
                    new String[]{"cnpcplus.mark.horizontalOff", "cnpcplus.mark.horizontalOn"}, rotation.cnpcplus$horizontal() ? 1 : 0));
            addButton(new GuiNpcButton(id + 6, guiLeft + 99, y + 19, 91, 18,
                    new String[]{"cnpcplus.mark.verticalOff", "cnpcplus.mark.verticalOn"}, rotation.cnpcplus$vertical() ? 1 : 0));
            addButton(new GuiNpcButton(id + 7, guiLeft + 192, y + 19, 76, 18, "cnpcplus.text.preview"));
        }
        addButton(new GuiNpcButton(700, guiLeft + 6, guiTop + 169, 60, 20, "gui.add"));
        addButton(new GuiNpcButton(701, guiLeft + 72, guiTop + 169, 24, 20, "<")); getButton(701).enabled = cnpcplus$page > 0;
        addButton(new GuiNpcButton(702, guiLeft + 244, guiTop + 169, 24, 20, ">")); getButton(702).enabled = (long) (cnpcplus$page + 1) * 4 < data.marks.size();
        cnpcplus$refreshPreview(); ci.cancel();
    }
    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$edit(GuiButton button, CallbackInfo ci) {
        ci.cancel();
        if (button.id == 700) {
            selectedMark = data.new Mark(); data.marks.add(selectedMark); cnpcplus$page = (data.marks.size() - 1) / 4; initGui(); return;
        }
        if (button.id == 701 || button.id == 702) { cnpcplus$page += button.id == 701 ? -1 : 1; initGui(); return; }
        int index = cnpcplus$page * 4 + button.id / 10;
        if (button.id < 1 || button.id > 37 || index >= data.marks.size()) return;
        MarkData.Mark mark = selectedMark = data.marks.get(index);
        MarkRotation rotation = (MarkRotation) mark;
        switch (button.id % 10) {
            case 1: mark.type = ((GuiNpcButton) button).getValue(); break;
            case 2: setSubGui(new SubGuiColorWheel(mark.color, color -> mark.color = color)); break;
            case 3: setSubGui(new SubGuiNpcAvailability(mark.availability)); break;
            case 4:
                data.marks.remove(index); selectedMark = data.marks.isEmpty() ? null : data.marks.get(Math.min(index, data.marks.size() - 1)); initGui(); break;
            case 5: rotation.cnpcplus$rotation(((GuiNpcButton) button).getValue() == 1, rotation.cnpcplus$vertical()); break;
            case 6: rotation.cnpcplus$rotation(rotation.cnpcplus$horizontal(), ((GuiNpcButton) button).getValue() == 1); break;
            default: break;
        }
        cnpcplus$refreshPreview();
    }
    @Inject(method = "subGuiClosed", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$closeColor(SubGuiInterface gui, CallbackInfo ci) { initGui(); ci.cancel(); }
    @Unique private void cnpcplus$refreshPreview() {
        if (cnpcplus$preview == null) {
            Entity entity = EntityList.createEntityByIDFromName(EntityList.getKey(npc), mc.world);
            if (!(entity instanceof EntityNPCInterface)) return;
            cnpcplus$preview = (EntityNPCInterface) entity;
            NBTTagCompound display = npc.writeSpawnData().copy(); display.setBoolean("NoLivingAnimation", true); display.setInteger("ShowName", 1);
            cnpcplus$preview.readSpawnData(display);
        }
        NBTTagCompound tag = data.getNBT(); NBTTagList list = new NBTTagList();
        int index = data.marks.indexOf(selectedMark);
        if (index >= 0) {
            NBTTagCompound mark = tag.getTagList("marks", 10).getCompoundTagAt(index).copy();
            mark.setTag("availability", new NBTTagCompound()); list.appendTag(mark);
        }
        tag.setTag("marks", list); MarkData.get(cnpcplus$preview).setNBT(tag);
    }
    @Override public void drawScreen(int mx, int my, float partial) {
        super.drawScreen(mx, my, partial); if (hasSubGui()) return;
        fontRenderer.drawString((cnpcplus$page + 1) + "/" + Math.max(1, (data.marks.size() + 3L) / 4) + " (" + data.marks.size() + ")", guiLeft + 103, guiTop + 175, 0x202020);
        drawRect(guiLeft + 276, guiTop + 7, guiLeft + 412, guiTop + 193, 0xFF000000);
        if (cnpcplus$preview == null) return;
        double fx = mc.displayWidth / (double) width, fy = mc.displayHeight / (double) height;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) ((guiLeft + 277) * fx), mc.displayHeight - (int) ((guiTop + 192) * fy), (int) (134 * fx), (int) (184 * fy));
        int oldX = mouseX, oldY = mouseY;
        mouseX = guiLeft + 344; mouseY = Math.round(guiTop + 178 - 50 * (cnpcplus$preview.height > 2.4F ? 2F / cnpcplus$preview.height : 1F));
        try { drawNpc(cnpcplus$preview, 344, 178, 1F, 180); }
        finally { mouseX = oldX; mouseY = oldY; GL11.glDisable(GL11.GL_SCISSOR_TEST); }
    }
    @Inject(method = "save", at = @At("HEAD"), require = 1)
    private void cnpcplus$release(CallbackInfo ci) { cnpcplus$preview = null; }
}
