package bin.cnpcplus.mixin.spawner;

import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.spawner.PacketSpawnerAction;
import bin.cnpcplus.spawner.SpawnerData;
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
public abstract class MixinGuiNpcSpawnerLists extends GuiNPCInterface2 {
    @Shadow private JobSpawner job;
    @Unique private int cnpcplus$tab = 1;
    @Unique private List<String> cnpcplus$candidates = new ArrayList<>();
    @Unique private String cnpcplus$leftSearch = "", cnpcplus$rightSearch = "";
    protected MixinGuiNpcSpawnerLists(EntityNPCInterface npc) { super(npc); }

    @Inject(method = "func_73866_w_()V", at = @At("TAIL"), require = 1)
    private void cnpcplus$lists(CallbackInfo ci) {
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
        this.addTextField(new GuiNpcTextField(40, this, this.getFontRenderer(), this.guiLeft + 4, this.guiTop + 40, 180, 16, this.cnpcplus$leftSearch));
        this.addTextField(new GuiNpcTextField(41, this, this.getFontRenderer(), this.guiLeft + 232, this.guiTop + 40, 180, 16, this.cnpcplus$rightSearch));
        for (int i = 0; i < 2; i++) {
            GuiCustomScroll list = new GuiCustomScroll(this, i);
            list.setSize(180, 80); list.guiLeft = this.guiLeft + (i == 0 ? 4 : 232); list.guiTop = this.guiTop + 59;
            this.addScroll(list);
        }
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
        for (String name : this.cnpcplus$candidates) if (name.toLowerCase(Locale.ROOT).contains(this.cnpcplus$leftSearch.toLowerCase(Locale.ROOT))) available.add(name);
        for (Map.Entry<Integer, SpawnerData.Entry> entry : SpawnerData.get(this.job).entrySet()) {
            String name = entry.getKey() + ": " + entry.getValue().tab + ": " + entry.getValue().name;
            if (name.toLowerCase(Locale.ROOT).contains(this.cnpcplus$rightSearch.toLowerCase(Locale.ROOT))) assigned.add(name);
        }
        this.getScroll(0).setList(available);
        this.getScroll(1).setUnsortedList(new ArrayList<>());
        this.getScroll(1).setUnsortedList(assigned);
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
}
