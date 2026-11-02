package bin.cnpcplus.mixin.spawner;

import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.spawner.PacketSpawnerAction;
import bin.cnpcplus.spawner.SpawnerData;
import bin.cnpcplus.client.gui.DragSearchField;
import bin.cnpcplus.transport.client.Pinyin;
import bin.cnpcplus.wand.PacketWandNpc;
import bin.cnpcplus.wand.client.NpcPreview;
import bin.cnpcplus.wand.client.PreviewReceiver;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.client.gui.roles.GuiNpcSpawner;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.client.gui.util.GuiCustomScroll;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.JobSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Mixin(value = GuiNpcSpawner.class, remap = false)
public abstract class MixinGuiNpcSpawnerLists extends GuiNPCInterface2 implements PreviewReceiver {
    @Shadow private JobSpawner job;
    @Unique private int cnpcplus$tab = 1;
    @Unique private List<String> cnpcplus$candidates = new ArrayList<>();
    @Unique private String cnpcplus$leftSearch = "", cnpcplus$rightSearch = "";
    @Unique private final NpcPreview cnpcplus$leftPreview = new NpcPreview(), cnpcplus$rightPreview = new NpcPreview();
    @Unique private int cnpcplus$previewLeft, cnpcplus$previewRight, cnpcplus$previewY, cnpcplus$previewHeight;
    protected MixinGuiNpcSpawnerLists(EntityNPCInterface npc) { super(npc); }

    @Inject(method = "func_73866_w_()V", at = @At("TAIL"), require = 1)
    private void cnpcplus$lists(CallbackInfo ci) {
        cnpcplus$leftPreview.clear(); cnpcplus$rightPreview.clear();
        for (int i = 0; i < 6; i++) {
            this.getButton(i).visible = false; this.getButton(i).enabled = false;
            this.getButton(20 + i).visible = false; this.getButton(20 + i).enabled = false;
            this.getLabel(i).enabled = false;
        }
        for (int tab = 1; tab <= 9; tab++) this.addButton(new GuiNpcButton(29 + tab,
                this.guiLeft + 4 + (tab - 1) * 45, this.guiTop + 4, 43, 20,
                (tab == this.cnpcplus$tab ? "> " : "") + "Tab " + tab));
        this.addLabel(new GuiNpcLabel(13, "cnpcplus.spawner.candidates", this.guiLeft + 4, this.guiTop + 29));
        this.addLabel(new GuiNpcLabel(14, "cnpcplus.spawner.assigned", this.guiLeft + 232, this.guiTop + 29));
        this.addTextField(new DragSearchField(40, this, this.guiLeft + 4, this.guiTop + 40, 180, 16, this.cnpcplus$leftSearch));
        this.addTextField(new DragSearchField(41, this, this.guiLeft + 232, this.guiTop + 40, 180, 16, this.cnpcplus$rightSearch));
        for (int i = 0; i < 2; i++) {
            GuiCustomScroll list = new GuiCustomScroll(this, i);
            list.setSize(112, 80); list.guiLeft = this.guiLeft + (i == 0 ? 4 : 232); list.guiTop = this.guiTop + 59;
            this.addScroll(list);
        }
        cnpcplus$previewLeft = 120; cnpcplus$previewRight = 348; cnpcplus$previewY = 59; cnpcplus$previewHeight = 80;
        String[] actions = {">", "<", "↑", "↓"};
        for (int i = 0; i < actions.length; i++) this.addButton(new GuiNpcButton(40 + i, this.guiLeft + 196, this.guiTop + 56 + i * 22, 24, 20, actions[i]));
        this.cnpcplus$refresh();
        this.cnpcplus$request(0, 0, 0, "");
    }

    @Inject(method = "setGuiData", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$receiveLists(NBTTagCompound tag, CallbackInfo ci) {
        if (!tag.hasKey(SpawnerData.TITLES, 9) && !tag.hasKey("CNPCPlusSpawnerTab")) return;
        if (tag.hasKey(SpawnerData.TITLES, 9)) SpawnerData.read(this.job, tag, SpawnerData.TITLES);
        if (tag.getInteger("CNPCPlusSpawnerTab") == this.cnpcplus$tab && tag.hasKey("List", 9)) {
            this.cnpcplus$candidates.clear();
            NBTTagList list = tag.getTagList("List", 8);
            for (int i = 0; i < list.tagCount(); i++) this.cnpcplus$candidates.add(list.getStringTagAt(i));
        }
        this.cnpcplus$refresh();
        ci.cancel();
    }

    @Unique private void cnpcplus$refresh() {
        if (this.getScroll(0) == null) return;
        List<String> available = new ArrayList<>(), assigned = new ArrayList<>();
        for (String name : this.cnpcplus$candidates) if (Pinyin.matches(this.cnpcplus$leftSearch, name)) available.add(name);
        for (Map.Entry<Integer, SpawnerData.Entry> entry : SpawnerData.get(this.job).entrySet()) {
            String name = entry.getKey() + ": " + entry.getValue().tab + ": " + entry.getValue().name;
            if (Pinyin.matches(this.cnpcplus$rightSearch, name)) assigned.add(name);
        }
        this.getScroll(0).setList(available);
        this.getScroll(1).setUnsortedList(new ArrayList<>());
        this.getScroll(1).setUnsortedList(assigned);
        cnpcplus$leftPreview.clear(); cnpcplus$rightPreview.clear();
    }

    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$listAction(GuiButton button, CallbackInfo ci) {
        if (button.id >= 30 && button.id <= 38) {
            this.cnpcplus$tab = button.id - 29; this.cnpcplus$candidates.clear();
            ((GuiNpcSpawner) (Object) this).func_73866_w_(); ci.cancel(); return;
        }
        if (button.id < 40 || button.id > 43) return;
        String selected = this.getScroll(button.id == 40 ? 0 : 1).getSelected();
        if (selected != null) {
            if (button.id == 40) this.cnpcplus$request(1, 0, 0, selected);
            else {
                int colon = selected.indexOf(':');
                if (colon > 0) {
                    int slot = Integer.parseInt(selected.substring(0, colon));
                    this.cnpcplus$request(button.id == 41 ? 2 : 3, slot, button.id == 42 ? -1 : 1, "");
                }
            }
        }
        ci.cancel();
    }

    @Override public void func_73869_a(char character, int key) {
        super.func_73869_a(character, key);
        String left = this.getTextField(40).getText(), right = this.getTextField(41).getText();
        if (!left.equals(this.cnpcplus$leftSearch) || !right.equals(this.cnpcplus$rightSearch)) {
            this.cnpcplus$leftSearch = left; this.cnpcplus$rightSearch = right; this.cnpcplus$refresh();
        }
    }

    @Unique private void cnpcplus$request(int action, int slot, int direction, String name) {
        CraftingViewNetwork.CHANNEL.sendToServer(new PacketSpawnerAction(this.npc.getEntityId(), action, this.cnpcplus$tab, slot, direction, name));
    }
    @Override public void drawScreen(int x, int y, float partial) {
        super.drawScreen(x, y, partial);
        if (hasSubGui()) { cnpcplus$leftPreview.clear(); cnpcplus$rightPreview.clear(); return; }
        cnpcplus$leftPreview.select(2, 0, cnpcplus$tab, getScroll(0).getSelected(), () -> null);
        String selected = getScroll(1).getSelected();
        int slot = selected == null ? 0 : Integer.parseInt(selected.substring(0, selected.indexOf(':')));
        cnpcplus$rightPreview.select(3, npc.getEntityId(), slot, selected, () -> null);
        cnpcplus$leftPreview.render(this, cnpcplus$previewLeft, cnpcplus$previewY, 64, cnpcplus$previewHeight);
        cnpcplus$rightPreview.render(this, cnpcplus$previewRight, cnpcplus$previewY, 64, cnpcplus$previewHeight);
        for (int i = 0; i < 2; i++) {
            GuiCustomScroll list = getScroll(i);
            if (list.getSelected() != null && x >= list.guiLeft && x < list.guiLeft + 112 && y >= list.guiTop && y < list.guiTop + 80)
                net.minecraftforge.fml.client.config.GuiUtils.drawHoveringText(java.util.Collections.singletonList(list.getSelected()), x, y, width, height, 260, fontRenderer);
        }
    }
    @Override public void cnpcplus$receive(PacketWandNpc packet) {
        cnpcplus$leftPreview.receive(packet); cnpcplus$rightPreview.receive(packet);
    }
}
