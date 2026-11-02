package top.cnpcplus.mixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import noppes.npcs.client.gui.roles.GuiNpcSpawner;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcplus.clone.client.ClonePreview;
import top.cnpcplus.clone.client.ClonePreviewHost;
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
public abstract class MixinGuiNpcSpawnerLists extends GuiNPCInterface2 implements IGuiData, ClonePreviewHost {
    protected MixinGuiNpcSpawnerLists(EntityNPCInterface npc) { super(npc); }
    @Unique private ClonePreview cnpcplus$preview, cnpcplus$candidatePreview;
    @Unique private int cnpcplus$previewLeft, cnpcplus$previewRight, cnpcplus$previewY, cnpcplus$previewHeight;
    @Override public ClonePreview cnpcplus$clonePreview() { return cnpcplus$preview; }
    @Override public void cnpcplus$receivePreview(int tab, String name, long request, byte[] bytes) {
        if (cnpcplus$preview != null) cnpcplus$preview.receive(tab, name, request, bytes);
        if (cnpcplus$candidatePreview != null) cnpcplus$candidatePreview.receive(tab, name, request, bytes);
    }
    @Inject(method = "init", at = @At("HEAD"), require = 1)
    private void cnpcplus$fitPage(CallbackInfo ci) { imageWidth = Math.min(620, width - 16); }

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
        if (cnpcplus$preview == null) cnpcplus$preview = new ClonePreview(); else cnpcplus$preview.clear();
        if (cnpcplus$candidatePreview == null) cnpcplus$candidatePreview = new ClonePreview(1); else cnpcplus$candidatePreview.clear();

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
        int tabWidth = (imageWidth - 8) / 9;
        for (int t = 1; t <= 9; t++) {
            String text = (t == tab ? "▶ " : "") + t;
            self.addButton(new GuiButtonNop(self, 29 + t, self.guiLeft + 4 + (t - 1) * tabWidth, self.guiTop + 4, tabWidth - 2, 20, text));
        }
        int column = (imageWidth - 44) / 2;
        int middle = self.guiLeft + column + 10, rightX = middle + 30;
        boolean compact = imageWidth < 400;
        int listWidth = compact ? column : column - 68;
        int listY = self.guiTop + (compact ? 88 : 40), listHeight = compact ? 53 : 101;
        cnpcplus$previewLeft = self.guiLeft + 4 + (compact ? (column - 64) / 2 : listWidth + 4);
        cnpcplus$previewRight = rightX + (compact ? (column - 64) / 2 : listWidth + 4);
        cnpcplus$previewY = self.guiTop + 40; cnpcplus$previewHeight = compact ? 44 : 101;
        self.addLabel(new GuiLabel(13, "cnpcplus.spawner.candidates", self.guiLeft + 4, self.guiTop + 30));
        self.addLabel(new GuiLabel(14, "cnpcplus.spawner.assigned", rightX, self.guiTop + 30));

        GuiCustomScrollNop left = new GuiCustomScrollNop(self, 0);
        left.setSize(listWidth, listHeight);
        left.guiLeft = self.guiLeft + 4;
        left.guiTop = listY;
        self.addScroll(left);

        GuiCustomScrollNop right = new GuiCustomScrollNop(self, 1);
        right.setSize(listWidth, listHeight);
        right.guiLeft = rightX;
        right.guiTop = listY;
        self.addScroll(right);

        self.addButton(new GuiButtonNop(self, 40, middle, self.guiTop + 46, 24, 20, ">"));
        self.addButton(new GuiButtonNop(self, 41, middle, self.guiTop + 70, 24, 20, "<"));
        self.addButton(new GuiButtonNop(self, 42, middle, self.guiTop + 94, 24, 20, "\u2191"));
        self.addButton(new GuiButtonNop(self, 43, middle, self.guiTop + 118, 24, 20, "\u2193"));
        if (compact) {
            self.getLabel(11).setX(self.guiLeft + 160);
            self.getLabel(11).setMessage(net.minecraft.network.chat.Component.translatable("cnpcplus.spawner.recall"));
            self.getButton(11).setX(self.guiLeft + imageWidth - 44);
        }

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
        if (compound == null || !compound.contains("List", 9) || compound.getInt("CNPCPlusCloneListTab") != cnpcplus$tab()) return;
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

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        boolean background = drawDefaultBackground;
        if (background) renderBackground(g, mx, my, partial);
        g.fill(guiLeft, guiTop, guiLeft + imageWidth, guiTop + 214, 0xFFC6C6C6);
        drawDefaultBackground = false;
        super.render(g, mx, my, partial);
        drawDefaultBackground = background;
        if (!hasSubGui()) g.fill(guiLeft + 2, guiTop + 214, guiLeft + imageWidth - 2, guiTop + 217, 0xFF555555);
        if (cnpcplus$preview == null) return;
        if (hasSubGui()) { cnpcplus$clearPreviews(); return; }
        var left = getScroll(0);
        cnpcplus$candidatePreview.select(2, cnpcplus$tab(), left == null ? null : left.getSelected());
        cnpcplus$candidatePreview.render(this, g, cnpcplus$previewLeft, cnpcplus$previewY, 64, cnpcplus$previewHeight);
        var right = getScroll(1);
        String selected = right == null ? null : right.getSelected();
        CloneSpawnData data = selected == null ? null : job.data.get(cnpcplus$entryKey(selected));
        cnpcplus$preview.select(2, data == null ? 1 : data.tab, data == null ? null : data.name);
        cnpcplus$preview.render(this, g, cnpcplus$previewRight, cnpcplus$previewY, 64, cnpcplus$previewHeight);
        for (var list : new GuiCustomScrollNop[]{left, right}) {
            if (list == null || list.getSelected() == null || mx < list.guiLeft || mx >= list.guiLeft + list.getWidth()
                    || my < list.guiTop + 22 || my >= list.guiTop + list.getHeight()) continue;
            var lines = font.split(net.minecraft.network.chat.Component.literal(list.getSelected()), imageWidth - 24);
            int textWidth = 0;
            for (var line : lines) textWidth = Math.max(textWidth, font.width(line));
            int boxHeight = lines.size() * font.lineHeight + 8;
            int boxX = Math.max(guiLeft + 4, Math.min(mx + 8, guiLeft + imageWidth - 12 - textWidth));
            int boxY = Math.max(guiTop + 16, Math.min(my + 12, guiTop + 214 - boxHeight - 4));
            g.pose().pushPose(); g.pose().translate(0, 0, 400);
            g.fill(boxX, boxY, boxX + textWidth + 8, boxY + boxHeight, 0xFF202020);
            for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), boxX + 4, boxY + 4 + i * font.lineHeight, 0xFFFFFFFF, false);
            g.pose().popPose();
        }
    }
    @Inject(method = "save", at = @At("HEAD"), require = 1)
    private void cnpcplus$closePreview(CallbackInfo ci) { cnpcplus$clearPreviews(); }
    @Unique private void cnpcplus$clearPreviews() {
        if (cnpcplus$preview != null) cnpcplus$preview.clear();
        if (cnpcplus$candidatePreview != null) cnpcplus$candidatePreview.clear();
    }
    @Override public void removed() { cnpcplus$clearPreviews(); super.removed(); }
}
