package top.cnpcplus.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import noppes.npcs.client.gui.SubGuiNpcAvailability;
import noppes.npcs.client.gui.advanced.GuiNPCMarks;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.controllers.data.MarkData;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonBiDirectional;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.client.gui.SafeEntityPreview;
import top.cnpcplus.client.gui.SubGuiColorWheel;
import top.cnpcplus.marks.MarkRotation;
import top.cnpcplus.marks.client.MarkTypes;
import top.cnpcplus.text.client.PlayerTextPreview;

/** Four visible rows, unlimited marks, and an isolated registry-bound editor draft. */
@Mixin(value = GuiNPCMarks.class, remap = false)
public abstract class MixinGuiNPCMarks extends GuiNPCInterface2 {
    @Shadow private MarkData data;
    @Shadow private MarkData.Mark selectedMark;
    @Unique private int cnpcplus$page;
    @Unique private EntityNPCInterface cnpcplus$preview;
    protected MixinGuiNPCMarks(EntityNPCInterface npc) { super(npc); }
    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void cnpcplus$draft(EntityNPCInterface npc, CallbackInfo ci) {
        CompoundTag saved = data.getNBT();
        data = new MarkData(); ((MarkDataAccess)data).cnpcplus$entity(npc); data.setNBT(saved);
        if (!data.marks.isEmpty()) selectedMark = data.marks.get(0);
    }
    @Inject(method = "init", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$rows(CallbackInfo ci) {
        super.init();
        cnpcplus$page = Math.max(0, Math.min(cnpcplus$page, Math.max(0, (data.marks.size() - 1) / 4)));
        for (int row = 0; row < 4 && cnpcplus$page * 4 + row < data.marks.size(); row++) {
            MarkData.Mark mark = data.marks.get(cnpcplus$page * 4 + row);
            int y = guiTop + 8 + row * 38, id = row * 10;
            int type = mark.type >= 0 && mark.type < MarkTypes.NAMES.length ? mark.type : 0;
            addButton(new GuiButtonBiDirectional(this, id + 1, guiLeft + 6, y, 90, 18, MarkTypes.NAMES, type));
            addButton(new GuiButtonNop(this, id + 2, guiLeft + 98, y, 49, 18, String.format("%06X", mark.color & 0xFFFFFF)));
            getButton(id + 2).setFGColor(mark.color & 0xFFFFFF);
            addButton(new GuiButtonNop(this, id + 3, guiLeft + 149, y, 91, 18, "availability.options"));
            addButton(new GuiButtonNop(this, id + 4, guiLeft + 242, y, 26, 18, "X"));
            MarkRotation rotation = (MarkRotation)mark;
            addButton(new GuiButtonNop(this, id + 5, guiLeft + 6, y + 19, 91, 18,
                    new String[]{"cnpcplus.mark.horizontalOff", "cnpcplus.mark.horizontalOn"}, rotation.cnpcplus$horizontal() ? 1 : 0));
            addButton(new GuiButtonNop(this, id + 6, guiLeft + 99, y + 19, 91, 18,
                    new String[]{"cnpcplus.mark.verticalOff", "cnpcplus.mark.verticalOn"}, rotation.cnpcplus$vertical() ? 1 : 0));
            addButton(new GuiButtonNop(this, id + 7, guiLeft + 192, y + 19, 76, 18, "cnpcplus.text.preview"));
        }
        addButton(new GuiButtonNop(this, 700, guiLeft + 6, guiTop + 169, 60, 20, "gui.add"));
        addButton(new GuiButtonNop(this, 701, guiLeft + 72, guiTop + 169, 24, 20, "<", cnpcplus$page > 0));
        addButton(new GuiButtonNop(this, 702, guiLeft + 244, guiTop + 169, 24, 20, ">", (long)(cnpcplus$page + 1) * 4 < data.marks.size()));
        cnpcplus$refreshPreview(); ci.cancel();
    }
    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$edit(GuiButtonNop button, CallbackInfo ci) {
        ci.cancel();
        if (button.id == 700) { selectedMark = data.new Mark(); data.marks.add(selectedMark); cnpcplus$page = (data.marks.size() - 1) / 4; init(); return; }
        if (button.id == 701 || button.id == 702) { cnpcplus$page += button.id == 701 ? -1 : 1; init(); return; }
        int index = cnpcplus$page * 4 + button.id / 10;
        if (button.id < 1 || button.id > 37 || index >= data.marks.size()) return;
        MarkData.Mark mark = selectedMark = data.marks.get(index);
        MarkRotation rotation = (MarkRotation)mark;
        switch (button.id % 10) {
            case 1 -> mark.type = button.getValue();
            case 2 -> setSubGui(new SubGuiColorWheel(mark.color, color -> mark.color = color));
            case 3 -> setSubGui(new SubGuiNpcAvailability(mark.availability));
            case 4 -> { data.marks.remove(index); selectedMark = data.marks.isEmpty() ? null : data.marks.get(Math.min(index, data.marks.size() - 1)); init(); }
            case 5 -> rotation.cnpcplus$rotation(button.getValue() == 1, rotation.cnpcplus$vertical());
            case 6 -> rotation.cnpcplus$rotation(rotation.cnpcplus$horizontal(), button.getValue() == 1);
            default -> { }
        }
        cnpcplus$refreshPreview();
    }
    @Inject(method = "subGuiClosed", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$closeColor(Screen gui, CallbackInfo ci) { init(); ci.cancel(); }
    @Unique private void cnpcplus$refreshPreview() {
        if (cnpcplus$preview == null && npc != null) {
            CompoundTag display = npc.writeSpawnData().copy();
            display.putString("CNPCPlusPreviewType", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(npc.getType()).toString());
            display.putBoolean("NoLivingAnimation", true); display.putInt("ShowName", 1);
            cnpcplus$preview = PlayerTextPreview.npc(display);
        }
        if (cnpcplus$preview == null) return;
        CompoundTag tag = data.getNBT(); ListTag list = new ListTag();
        int index = data.marks.indexOf(selectedMark);
        if (index >= 0) {
            CompoundTag mark = tag.getList("marks", 10).getCompound(index).copy();
            mark.put("availability", new CompoundTag()); list.add(mark);
        }
        tag.put("marks", list); MarkData.get(cnpcplus$preview).setNBT(tag);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        if (hasSubGui()) return;
        g.drawString(font, (cnpcplus$page + 1) + "/" + Math.max(1, (data.marks.size() + 3L) / 4) + "  (" + data.marks.size() + ")", guiLeft + 103, guiTop + 175, 0x202020, false);
        g.fill(guiLeft + 276, guiTop + 7, guiLeft + 412, guiTop + 193, 0xFF000000);
        if (cnpcplus$preview == null) return;
        g.enableScissor(guiLeft + 277, guiTop + 8, guiLeft + 411, guiTop + 192);
        try {
            cnpcplus$preview.setYRot(180); cnpcplus$preview.setXRot(0);
            cnpcplus$preview.yBodyRot = cnpcplus$preview.yBodyRotO = cnpcplus$preview.yHeadRot = cnpcplus$preview.yHeadRotO = 180;
            SafeEntityPreview.inventory(g, guiLeft + 344, guiTop + 178, 36, new Quaternionf().rotateZ((float)Math.PI), new Quaternionf(), cnpcplus$preview);
        } finally { g.disableScissor(); }
    }
    @Override public void removed() { cnpcplus$preview = null; super.removed(); }
    @Inject(method = "save", at = @At("HEAD"), require = 1)
    private void cnpcplus$releasePreview(CallbackInfo ci) { cnpcplus$preview = null; }
}
