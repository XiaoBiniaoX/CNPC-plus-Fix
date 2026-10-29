package top.cnpcplus.mixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import noppes.npcs.client.gui.roles.GuiNpcSpawner;
import noppes.npcs.controllers.data.CloneSpawnData;
import noppes.npcs.packets.Packets;
import noppes.npcs.packets.server.SPacketCloneList;
import noppes.npcs.packets.server.SPacketNpcJobSpawnerSet;
import noppes.npcs.roles.JobSpawner;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.listeners.IGuiData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 召唤师（job.spawner）职业页改版：隐藏原生 6 行槽位，改为 Tab 栏 + 左右双搜索滚动条目集 + 搬运按钮。
 *
 * <p>init TAIL：把原生 6 行按钮/标签隐去（id 0-5 标题按钮、20-25 删除按钮、标签 0-5），
 * 再叠新 UI——9 个 Tab（30-38）、双滚动（0/1）、搬运四个按钮（40-43）；
 * 右栏条目直接读 {@code job.data}（Map&lt;Integer,CloneSpawnData&gt;，1 基 key），只读
 * (tab,name) 缓冲，不触发 {@code getCompound()}（那会碰 ServerCloneController）。
 * 每次重建按当前 Tab 发 {@link SPacketCloneList} 拉克隆列表。
 *
 * <p>按钮 HEAD 注入接管 30-38/40-43（cancel 后原生 body 对这些 id 本就无分支）：
 * Tab 切换整页重建；添加走 maxKey+1 新槽 + {@link SPacketNpcJobSpawnerSet} 即时同步；
 * 删除/上下移仅改内存，随原生 save()（关闭 GUI 时 SPacketNpcJobSave）持久化——
 * 与原生 X 按钮的持久化口径一致。
 *
 * <p>实现 {@link IGuiData} 接收克隆列表回包（PacketGuiData 只发给实现了该接口的当前屏）。
 */
@Mixin(value = GuiNpcSpawner.class, remap = false)
public abstract class MixinGuiNpcSpawnerLists implements IGuiData {

    @Shadow
    private JobSpawner job;

    @Unique
    private int cnpcplus$tab;

    @Unique
    private int cnpcplus$tab() {
        return this.cnpcplus$tab >= 1 && this.cnpcplus$tab <= 9 ? this.cnpcplus$tab : 1;
    }

    @Inject(method = "init", at = @At("TAIL"), require = 1, remap = false)
    private void cnpcplus$buildLists(CallbackInfo ci) {
        GuiNpcSpawner self = (GuiNpcSpawner) (Object) this;

        for (int i = 0; i < 6; i++) {
            GuiButtonNop title = self.getButton(i);
            if (title != null) {
                title.visible = false;
                title.active = false;
            }
            GuiButtonNop del = self.getButton(20 + i);
            if (del != null) {
                del.visible = false;
                del.active = false;
            }
            self.wrapper.labels.remove(i);
        }

        int tab = this.cnpcplus$tab();
        for (int t = 1; t <= 9; t++) {
            String text = (t == tab ? "▶ Tab " : "Tab ") + t;
            self.addButton(new GuiButtonNop(self, 29 + t, self.guiLeft + 4 + (t - 1) * 45, self.guiTop + 4, 43, 20, text));
        }
        self.addLabel(new GuiLabel(13, "cnpcplus.spawner.candidates", self.guiLeft + 4, self.guiTop + 30));
        self.addLabel(new GuiLabel(14, "cnpcplus.spawner.assigned", self.guiLeft + 232, self.guiTop + 30));

        GuiCustomScrollNop left = new GuiCustomScrollNop(self, 0);
        left.setSize(180, 101);
        left.guiLeft = self.guiLeft + 4;
        left.guiTop = self.guiTop + 40;
        self.addScroll(left);

        GuiCustomScrollNop right = new GuiCustomScrollNop(self, 1);
        right.setSize(180, 101);
        right.guiLeft = self.guiLeft + 232;
        right.guiTop = self.guiTop + 40;
        self.addScroll(right);

        self.addButton(new GuiButtonNop(self, 40, self.guiLeft + 196, self.guiTop + 46, 24, 20, ">"));
        self.addButton(new GuiButtonNop(self, 41, self.guiLeft + 196, self.guiTop + 70, 24, 20, "<"));
        self.addButton(new GuiButtonNop(self, 42, self.guiLeft + 196, self.guiTop + 94, 24, 20, "\u2191"));
        self.addButton(new GuiButtonNop(self, 43, self.guiLeft + 196, self.guiTop + 118, 24, 20, "\u2193"));

        this.cnpcplus$refreshRight(self, null);
        Packets.sendServer(new SPacketCloneList(tab));
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$buttonEvent(GuiButtonNop button, CallbackInfo ci) {
        GuiNpcSpawner self = (GuiNpcSpawner) (Object) this;
        int id = button.id;

        if (id >= 30 && id <= 38) {
            this.cnpcplus$tab = id - 29;
            self.init();
            ci.cancel();
            return;
        }

        if (id == 40) {
            GuiCustomScrollNop left = self.getScroll(0);
            String name = left == null ? null : left.getSelected();
            if (name != null) {
                int slot = this.job.data.isEmpty() ? 1 : Collections.max(this.job.data.keySet()) + 1;
                int tab = this.cnpcplus$tab();
                this.job.setJobCompound(slot, tab, name);
                Packets.sendServer(new SPacketNpcJobSpawnerSet(tab, name, slot));
                GuiCustomScrollNop right = self.getScroll(1);
                this.cnpcplus$refreshRight(self, right == null ? null : right.getSelected());
            }
            ci.cancel();
            return;
        }

        if (id == 41) {
            GuiCustomScrollNop right = self.getScroll(1);
            String selected = right == null ? null : right.getSelected();
            if (selected != null) {
                int key = this.cnpcplus$entryKey(selected);
                if (key > 0) {
                    this.job.remove(key);
                    this.cnpcplus$refreshRight(self, null);
                }
            }
            ci.cancel();
            return;
        }

        if (id == 42 || id == 43) {
            GuiCustomScrollNop right = self.getScroll(1);
            String selected = right == null ? null : right.getSelected();
            if (selected != null) {
                int from = this.cnpcplus$entryKey(selected);
                TreeMap<Integer, CloneSpawnData> sorted = new TreeMap<>(this.job.data);
                List<Integer> keys = new ArrayList<>(sorted.keySet());
                int index = keys.indexOf(from);
                int target = id == 42 ? index - 1 : index + 1;
                if (index >= 0 && target >= 0 && target < keys.size()) {
                    int fromKey = keys.get(index);
                    int toKey = keys.get(target);
                    CloneSpawnData moved = this.job.data.get(fromKey);
                    this.job.data.put(fromKey, this.job.data.get(toKey));
                    this.job.data.put(toKey, moved);
                    this.cnpcplus$refreshRight(self, toKey + ": " + moved.tab + ": " + moved.name);
                }
            }
            ci.cancel();
        }
    }

    @Override
    public void setGuiData(CompoundTag compound) {
        if (compound == null || !compound.contains("List", 9)) return;
        GuiNpcSpawner self = (GuiNpcSpawner) (Object) this;
        GuiCustomScrollNop left = self.getScroll(0);
        if (left == null) return;

        ListTag listTag = compound.getList("List", 8);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < listTag.size(); i++) {
            names.add(listTag.getString(i));
        }
        left.setSelectedIndex(-1);
        left.setList(names);
    }

    /**
     * 重建右栏（key 升序）。两段式 setUnsortedList：先空表再灌值，绕过 isSameList
     * 的“内容相同即跳过”（同集合不同顺序的上下移才会真的刷新）。
     */
    @Unique
    private void cnpcplus$refreshRight(GuiNpcSpawner self, String keepSelected) {
        GuiCustomScrollNop right = self.getScroll(1);
        if (right == null) return;

        List<String> items = new ArrayList<>();
        for (java.util.Map.Entry<Integer, CloneSpawnData> entry : new TreeMap<>(this.job.data).entrySet()) {
            CloneSpawnData sd = entry.getValue();
            items.add(entry.getKey() + ": " + sd.tab + ": " + sd.name);
        }

        right.setSelectedIndex(-1);
        right.setUnsortedList(new ArrayList<>());
        right.setUnsortedList(items);
        if (keepSelected != null) {
            right.setSelected(keepSelected);
        }
    }

    /** 条目串形如 "3: 2: CloneName"，取首个冒号前的槽位 key。 */
    @Unique
    private int cnpcplus$entryKey(String entry) {
        int colon = entry.indexOf(':');
        if (colon <= 0) return -1;
        try {
            return Integer.parseInt(entry.substring(0, colon).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
