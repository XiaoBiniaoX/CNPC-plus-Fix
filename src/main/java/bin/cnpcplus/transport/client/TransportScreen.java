package bin.cnpcplus.transport.client;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.transport.PacketTransportImage;
import bin.cnpcplus.transport.PacketTransportMeta;
import bin.cnpcplus.transport.TransportData;
import bin.cnpcplus.transport.TransportImages;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import noppes.npcs.client.gui.player.GuiTransportSelection;
import noppes.npcs.client.gui.roles.GuiNpcTransporter;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.client.gui.util.GuiTextArea;
import noppes.npcs.client.gui.util.IGuiData;
import noppes.npcs.client.gui.util.IScrollData;
import noppes.npcs.client.gui.util.ITextChangeListener;
import noppes.npcs.client.gui.util.ITextfieldListener;
import noppes.npcs.client.gui.util.SubGuiInterface;
import noppes.npcs.controllers.data.TransportLocation;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;

/** 952×535 设计空间复用参考贴图；编辑模式在左侧显示可滚动的元素检查器。 */
@Mod.EventBusSubscriber(modid = CnpcPlus.MODID, value = Side.CLIENT)
public final class TransportScreen extends SubGuiInterface implements IGuiData, IScrollData, ITextChangeListener, ITextfieldListener {
    private static TransportScreen active;
    private static final int[] PALETTE = {0xFFFFFF, 0xE0E0E0, 0xCCCCCC, 0x999999, 0x666666, 0x333333, 0x000000, 0xFFD700,
            0xFF5555, 0xFF7F50, 0xFFAA00, 0xFFFF55, 0xAAFF55, 0x55FF55, 0x55FFFF, 0xFF55FF};
    private static final String[] ELEMENTS = {"row", "current", "intro", "tab", "rowicon", "currenticon", "tabicon", "image"};
    private final int npcId;
    private final boolean editor;
    private final TransportLocation parentLocation;
    private final Map<Integer, NBTTagCompound> entries = new TreeMap<>();
    private final List<Integer> filtered = new ArrayList<>();
    private final Set<Integer> dirtyEntries = new HashSet<>();
    /** 本轮已经发给服务端、正在等回执的点位；回执里没出现在 Applied 就说明被拒了。 */
    private final Set<Integer> savingIds = new HashSet<>();
    private final Set<Integer> saveApplied = new HashSet<>();
    private final Map<String, int[]> uploads = new HashMap<>();
    private NBTTagCompound panel = TransportData.cleanPanel(new NBTTagCompound()), current = new NBTTagCompound();
    private int selected = -1, offset, element = 2, sidebarScroll, iconMode, sequence;
    private int saveBatchStart;
    private boolean saveError;
    private float scale;
    private int originX, originY;
    private boolean ready, syncing, dirty, diskDirty, closed, draggingScroll, draggingImage, draggingSidebar;
    /** 拖动排序：按下行 → 移动超过阈值才进入 rowDrag，松手才写 Order。 */
    private boolean rowDrag;
    private int rowPressId = -1, rowPressY;
    private int dragX, dragY, originalImageX, originalImageY;
    private long lastChange, lastClickTime;
    private int lastClickId = -1;
    private String search = "", hint = "";
    /** 手动填写的 PNG 路径；1.12.2/Java 8 没有系统拖拽 API，只能走输入框。 */
    private String importPath = "";
    private static String lastImportDir = "";
    private GuiTextArea area;
    /** 打开本界面的原生"传送师"编辑页；关闭子界面时它不会重建控件，必须回写名字。 */
    private GuiNpcTransporter parentGui;

    public TransportScreen(int npcId, boolean editor, TransportLocation parentLocation) {
        this.npcId = npcId; this.editor = editor; this.parentLocation = parentLocation;
        this.drawDefaultBackground = false; this.closeOnEsc = true; this.title = "";
        active = this;
    }

    /** 与原生编辑页挂钩：改名后同步它的 location 和名字输入框，避免它用旧名字回存覆盖。 */
    public TransportScreen parentGui(GuiNpcTransporter gui) { this.parentGui = gui; return this; }

    @SubscribeEvent public static void open(GuiOpenEvent event) {
        if (event.getGui() instanceof GuiTransportSelection) {
            GuiTransportSelection original = (GuiTransportSelection) event.getGui();
            if (original.npc != null) event.setGui(new TransportScreen(original.npc.getEntityId(), false, null));
        }
    }
    @SubscribeEvent public static void unload(WorldEvent.Unload event) { if (event.getWorld().isRemote) active = null; }
    /** 材质包换图后 PNG 尺寸缓存必须失效，否则新图仍按旧尺寸画。 */
    @SubscribeEvent public static void reload(TextureStitchEvent.Post event) { TransportAssets.clear(); }
    public static void receive(PacketTransportMeta message) {
        if (active == null || message.npcId != active.npcId || message.data == null) return;
        if (message.action == PacketTransportMeta.STATE) active.setGuiData(message.data);
        else if (message.action == PacketTransportMeta.ACK) active.acknowledge(message.data);
    }

    private int sidebarWidth() { return editor ? Math.min(270, Math.max(180, this.width * 28 / 100)) : 0; }
    private int sx(int x) { return originX + Math.round(x * scale); }
    private int sy(int y) { return originY + Math.round(y * scale); }
    private int sw(int size) { return Math.max(1, Math.round(size * scale)); }
    private void layout() {
        int left = sidebarWidth(), available = this.width - left - (editor ? 6 : 0);
        float fit = Math.min(available / 952.0F, (this.height - 20) / 555.0F);
        scale = Math.max(.1F, Math.min(1, fit * (editor ? .98F : .85F)));
        originX = left + (int) ((available - 952 * scale) / 2);
        originY = (int) ((this.height - 535 * scale) / 2);
    }

    @Override public void initGui() {
        super.initGui(); layout();
        GuiNpcTextField searchBox = new GuiNpcTextField(50, this, this.getFontRenderer(), sx(118), sy(44), sw(350), sw(28), search);
        searchBox.setEnableBackgroundDrawing(false); searchBox.setMaxStringLength(64); searchBox.setTextColor(0xFFFFFF); this.addTextField(searchBox);
        if (editor) {
            for (int id = 61; id <= 66; id++) {
                GuiNpcTextField field = new GuiNpcTextField(id, this, this.getFontRenderer(), 0, 0, 70, 16, "");
                field.setMaxStringLength(id == 66 ? 64 : 12); this.addTextField(field);
            }
            area = new GuiTextArea(67, 4, 52, sidebarWidth() - 16, 80, ""); area.setListener(this); this.add(area);
            GuiNpcTextField path = new GuiNpcTextField(68, this, this.getFontRenderer(), 0, 0, 70, 16,
                    importPath.isEmpty() ? lastImportDir : importPath);
            path.setMaxStringLength(512); this.addTextField(path);
            this.addButton(new GuiNpcButton(60, this.width - 78, 4, 74, 18, "gui.save"));
            for (int id : new int[]{70, 71, 72, 73, 80, 81, 82, 83, 84, 89, 90, 92}) this.addButton(new GuiNpcButton(id, 0, 0, 40, 18, ""));
        }
        syncWidgets();
        if (!ready) request();
    }
    private void request() { CraftingViewNetwork.CHANNEL.sendToServer(new PacketTransportMeta(npcId, editor ? PacketTransportMeta.EDITOR : PacketTransportMeta.PLAYER, new NBTTagCompound())); }

    @Override public void setGuiData(NBTTagCompound data) {
        if (!data.hasKey("Entries", 9)) return;
        if (ready && diskDirty) return;
        entries.clear(); NBTTagList list = data.getTagList("Entries", 10);
        for (int i = 0; i < list.tagCount(); i++) { NBTTagCompound entry = list.getCompoundTagAt(i); entries.put(entry.getInteger("Id"), entry.copy()); }
        current = data.getCompoundTag("CurrentMeta").copy(); panel = TransportData.cleanPanel(data.getCompoundTag("Panel"));
        if (selected < 0) selected = current.getInteger("Id");
        ready = true; applyFilter(); syncWidgets();
    }
    @Override public void setData(Vector<String> list, HashMap<String, Integer> data) { }
    @Override public void setSelected(String name) { }
    private NBTTagCompound meta(int id) { NBTTagCompound value = entries.get(id); return value == null ? current : value; }
    private NBTTagCompound selectedMeta() { return meta(selected); }
    private NBTTagCompound textMeta() { return element == 1 ? meta(current.getInteger("Id")) : selectedMeta(); }
    private String prefix() { return new String[]{"Row", "Cur", "Intro", "Tab"}[Math.min(3, element)]; }
    private boolean textElement() { return element < 4; }
    private String contentKey() { return element == 2 ? "Desc" : element == 3 ? "TabText" : "Name"; }
    private String spansKey() { return element == 2 ? "DescSpans" : element == 3 ? "TabSpans" : "NameSpans"; }
    private NBTTagCompound contentTag() { return element == 3 ? panel : textMeta(); }
    private String imageKey() { return element == 4 ? iconMode == 0 ? "Icon" : "IconSel" : element == 5 ? "CurIcon" : element == 6 ? "TabIcon" : "Image"; }
    private NBTTagCompound imageTag() { return element == 5 || element == 6 ? panel : selectedMeta(); }
    private String percentKey() { return element == 4 ? "IconSize" : element == 5 ? "CurIconSize" : element == 6 ? "TabIconSize" : "ImgScale"; }

    private void applyFilter() {
        filtered.clear();
        for (Map.Entry<Integer, NBTTagCompound> entry : entries.entrySet()) if (!entry.getValue().getBoolean("Delete")
                && Pinyin.matches(search, entry.getValue().getString("Name"))) filtered.add(entry.getKey());
        filtered.sort(this::compareEntries);
        offset = Math.max(0, Math.min(Math.max(0, filtered.size() - 5), offset));
    }

    /**
     * 默认按名字 A-Z；只要玩家拖过一次，服务端就会给所有点位写入 Order，
     * 之后按 Order 排。没有 Order 的新点位排在有序点位之后（仍按 A-Z）。
     */
    private int compareEntries(int left, int right) {
        NBTTagCompound a = entries.get(left), b = entries.get(right);
        if (a == null || b == null) return Integer.compare(left, right);
        int orderA = a.getInteger("Order"), orderB = b.getInteger("Order");
        if (orderA != orderB) {
            if (orderA < 0) return 1;
            if (orderB < 0) return -1;
            return Integer.compare(orderA, orderB);
        }
        int name = a.getString("Name").compareToIgnoreCase(b.getString("Name"));
        return name != 0 ? name : Integer.compare(left, right);
    }

    /** 拖动结束后把当前可见顺序写成 Order：0..n-1，一次提交整张表。 */
    private void commitOrder() {
        int order = 0;
        for (int id : filtered) {
            NBTTagCompound entry = entries.get(id);
            if (entry == null) continue;
            entry.setInteger("Order", order++);
            dirtyEntries.add(id);
        }
        if (order == 0) return;
        dirty = true; diskDirty = true; lastChange = 0;
        hint = I18n.format("cnpcplus.transport.order_saved");
        applyFilter(); syncWidgets();
    }

    /** 拖动中的实时预览：先动 filtered，松手才写 NBT。搜索状态下顺序不可见，直接禁用。 */
    private void dragRow(int y) {
        if (!editor || rowPressId < 0 || !search.isEmpty()) return;
        if (!rowDrag && Math.abs(y - rowPressY) < 6) return;
        rowDrag = true;
        int dy = (int) ((y - originY) / scale);
        int target = Math.max(0, Math.min(filtered.size() - 1, offset + Math.floorDiv(dy - 98, 70)));
        int from = filtered.indexOf(rowPressId);
        if (from < 0 || from == target) return;
        filtered.remove(from);
        filtered.add(target, rowPressId);
        if (target < offset) offset = target;
        else if (target >= offset + 5) offset = target - 4;
    }
    private void changed(boolean entry) {
        if (!ready || syncing) return;
        if (entry) dirtyEntries.add(element == 1 ? current.getInteger("Id") : selected);
        dirty = true; diskDirty = true; lastChange = System.currentTimeMillis();
        applyFilter();
    }
    private void sendSave(boolean disk) {
        if (!editor || !ready || (!diskDirty && !disk)) return;
        saveBatchStart = sequence + 1; saveError = false;
        saveApplied.clear();
        // 显式保存时把整份目录都提交一遍：只改了下标/大小写、或者上一轮自动保存的包
        // 在网络里丢了，都不会因为"不在 dirtyEntries 里"而永远发不出去 ——
        // 这正是以前"点保存没反应、名字还是 default name"的来源之一。
        Set<Integer> ids = new HashSet<>(dirtyEntries);
        if (disk) ids.addAll(entries.keySet());
        savingIds.clear();
        boolean blank = false;
        // 每个点位独立发送，避免长描述/样式随整个目录累计突破 C2S 载荷上限。
        for (int id : ids) {
            NBTTagCompound entry = entries.get(id);
            if (entry == null) continue;
            // 名字是必填项：服务端会忽略空名字、再把旧名字原样带回，
            // 界面就成了"清空后自己弹回去"。本地先留着空串，等玩家打完再发。
            if (blankName(entry)) { blank = true; continue; }
            NBTTagList list = new NBTTagList(); list.appendTag(entry.copy());
            NBTTagCompound change = new NBTTagCompound(); change.setTag("Entries", list); change.setInteger("Sequence", ++sequence);
            CraftingViewNetwork.CHANNEL.sendToServer(new PacketTransportMeta(npcId, PacketTransportMeta.SAVE, change));
            savingIds.add(id);
        }
        dirtyEntries.clear();
        NBTTagCompound tag = new NBTTagCompound(); tag.setTag("Panel", panel.copy());
        tag.setBoolean("Disk", disk); tag.setInteger("Sequence", ++sequence);
        CraftingViewNetwork.CHANNEL.sendToServer(new PacketTransportMeta(npcId, PacketTransportMeta.SAVE, tag));
        dirty = false;
        if (disk) diskDirty = false;
        if (blank) hint = I18n.format("cnpcplus.transport.name_required");
    }
    private void acknowledge(NBTTagCompound tag) {
        if (tag.hasKey("ImageKey")) {
            String key = tag.getString("ImageKey"); int[] target = uploads.remove(key);
            if (target != null && tag.getBoolean("ImageSaved")) {
                NBTTagCompound destination = target[1] == 5 || target[1] == 6 ? panel : meta(target[0]);
                String field = target[1] == 4 ? target[2] == 0 ? "Icon" : "IconSel" : target[1] == 5 ? "CurIcon" : target[1] == 6 ? "TabIcon" : "Image";
                destination.setString(field, key);
                if (target[1] == 7) { destination.setInteger("SrcW", target[3]); destination.setInteger("SrcH", target[4]); }
                if (target[1] != 5 && target[1] != 6) dirtyEntries.add(target[0]);
                diskDirty = true; dirty = true; lastChange = 0;
                hint = I18n.format("cnpcplus.transport.image_saved"); syncWidgets();
                if (closed) { sendSave(true); updateParent(); }
            } else hint = I18n.format("cnpcplus.transport.image_failed");
            return;
        }
        if (tag.getInteger("Sequence") < saveBatchStart) return;
        boolean accepted = tag.getBoolean("Saved");
        for (int id : tag.getIntArray("Applied")) saveApplied.add(id);
        // 服务端回带权威条目，直接覆盖本地：保存后看到的就是真正写进去的结果，
        // 不会再出现"界面显示保存成功、玩家面板还是 default name"这种假成功。
        boolean authoritative = tag.hasKey("Entries", 9);
        Set<Integer> present = new HashSet<>();
        if (authoritative) {
            NBTTagList list = tag.getTagList("Entries", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound entry = list.getCompoundTagAt(i); int id = entry.getInteger("Id");
                present.add(id);
                if (dirtyEntries.contains(id)) continue;   // 回执之后用户又改了，留给下一批
                if (blankName(entries.get(id))) continue;  // 玩家正把名字清空重输，别拿旧名字盖回去
                if (entry.getBoolean("Delete")) entries.remove(id); else entries.put(id, entry.copy());
            }
        }
        if (!accepted) saveError = true;
        if (tag.getInteger("Sequence") != sequence) return;   // 还没收到最后一条（面板）回执
        boolean lost = false;
        for (int id : new HashSet<>(savingIds)) {
            if (dirtyEntries.contains(id)) continue;
            if (authoritative && !present.contains(id)) { entries.remove(id); continue; }
            if (!saveApplied.contains(id)) { lost = true; saveError = true; }
        }
        savingIds.clear(); saveApplied.clear();
        applyFilter(); syncWidgets();
        hint = I18n.format(anyBlankName() ? "cnpcplus.transport.name_required"
                : lost ? "cnpcplus.transport.save_not_applied"
                : saveError ? "cnpcplus.transport.save_failed" : "cnpcplus.transport.saved");
    }

    private void syncWidgets() {
        if (!editor || area == null) return;
        syncing = true;
        try {
            area.setText(textElement() ? contentTag().getString(contentKey()) : "");
            int size = element == 0 ? selectedMeta().getInteger("TextSize") : panel.getInteger(prefix() + "Size");
            if (size <= 0) size = panel.getInteger("RowSize");
            this.getTextField(61).setText(Integer.toString(size));
            this.getTextField(62).setText(Integer.toString(imageTag().hasKey(percentKey()) ? imageTag().getInteger(percentKey()) : 100));
            this.getTextField(63).setText(Integer.toString(element == 7 ? selectedMeta().getInteger("ImgX") : panel.getInteger(prefix() + "X")));
            this.getTextField(64).setText(Integer.toString(element == 7 ? selectedMeta().getInteger("ImgY") : panel.getInteger(prefix() + "Y")));
            int color = element == 0 ? selectedMeta().getInteger("TextColor") : panel.getInteger(prefix() + "Color");
            if (color < 0) color = panel.getInteger("RowColor");
            this.getTextField(65).setText(String.format("%06X", color & 0xFFFFFF));
            this.getTextField(66).setText(imageTag().getString(imageKey()));
            GuiNpcTextField path = this.getTextField(68);
            if (path != null) path.setText(importPath.isEmpty() ? lastImportDir : importPath);
        } finally { syncing = false; }
        positionWidgets();
    }
    private void field(int id, int x, int y, int width, boolean show) {
        GuiNpcTextField field = this.getTextField(id); field.x = x; field.y = y - sidebarScroll; field.width = width;
        boolean visible = show && field.y >= 26 && field.y + 16 < this.height - 24;
        field.enabled = visible; field.canEdit = visible && ready; field.setVisible(visible);
    }
    private void button(int id, int x, int y, int width, String label, boolean show) {
        GuiNpcButton button = this.getButton(id); button.x = x; button.y = y - sidebarScroll; button.width = width;
        button.visible = show && button.y >= 26 && button.y + button.height < this.height - 24;
        button.enabled = ready; button.setDisplayText(label);
    }
    private void positionWidgets() {
        if (!editor || area == null) return;
        int width = sidebarWidth(), half = (width - 18) / 2;
        boolean text = textElement(), icon = element >= 4 && element <= 6, image = element == 7;
        area.y = 52 - sidebarScroll; area.width = width - 16;
        area.visible = text && area.y >= 26 && area.y + area.height < this.height - 24;
        area.enabled = area.visible && ready;
        field(61, width - 82, 140, 70, text);
        field(62, width - 82, image ? 152 : icon ? 130 : 132, 70, !text);
        field(63, 28, image ? 178 : 162, half - 25, text || image);
        field(64, half + 38, image ? 178 : 162, half - 25, text || image);
        field(65, width - 82, 184, 70, text);
        field(66, 4, image ? 50 : 54, width - 16, !text);
        field(68, 4, 80, width - 16, image);
        button(80, 4, 28, width - 16, "cnpcplus.transport.element." + ELEMENTS[element], true);
        button(81, 4, image ? 100 : 78, half, "cnpcplus.transport.import", !text);
        button(82, half + 10, image ? 100 : 78, half, "gui.clear", !text);
        button(83, 4, 102, width - 16, iconMode == 0 ? "cnpcplus.transport.icon.normal" : "cnpcplus.transport.icon.selected", element == 4);
        button(84, 4, 124, width - 16, "cnpcplus.transport.mode." + selectedMeta().getInteger("ImageMode"), image);
        String[] styles = {"B", "U", "I", "gui.reset"};
        // 先"应用/清除颜色"，再样式，最后调色板：顺序固定，滚动时一起移动。
        for (int i = 0; i < 4; i++) button(70 + i, 4 + i * (width - 16) / 4, 228, (width - 20) / 4, styles[i], text);
        button(90, 4, 206, half, "cnpcplus.transport.apply_color", text);
        button(89, half + 10, 206, half, "cnpcplus.transport.clear_color", text);
        button(92, 4, 312, width - 16, "cnpcplus.transport.delete", element == 0 && selected != current.getInteger("Id"));
    }

    @Override public void textUpdate(String text) {
        if (syncing || !textElement() || !ready) return;
        int max = element == 2 ? 2048 : element == 3 ? 16 : 64;
        String value = TransportData.limit(text, max);
        contentTag().setString(contentKey(), value);
        syncSpans(value);
        if (!value.equals(text)) { syncing = true; area.setText(value); syncing = false; }
        changed(element != 3);
    }
    @Override public void unFocused(GuiNpcTextField field) {
        if (!editor || syncing || field == null || !ready) return;
        try {
            if (field == getTextField(61)) {
                int value = Math.max(6, Math.min(48, Integer.parseInt(field.getText().trim())));
                int previous = element == 0 ? selectedMeta().getInteger("TextSize") : panel.getInteger(prefix() + "Size");
                if (previous <= 0) previous = panel.getInteger("RowSize");
                if (value == previous) return;
                if (element == 0) selectedMeta().setInteger("TextSize", value); else panel.setInteger(prefix() + "Size", value);
                changed(element == 0);
            } else if (field == getTextField(62)) {
                int value = Math.max(25, Math.min(400, Integer.parseInt(field.getText().trim())));
                if (imageTag().getInteger(percentKey()) == value) return;
                imageTag().setInteger(percentKey(), value); changed(element == 4 || element == 7);
            } else if (field == getTextField(63) || field == getTextField(64)) {
                String axis = field == getTextField(63) ? "X" : "Y";
                int limit = element == 7 ? 2000 : 120;
                int value = Math.max(-limit, Math.min(limit, Integer.parseInt(field.getText().trim())));
                if ((element == 7 ? selectedMeta().getInteger("Img" + axis) : panel.getInteger(prefix() + axis)) == value) return;
                if (element == 7) selectedMeta().setInteger("Img" + axis, value); else panel.setInteger(prefix() + axis, value);
                changed(element == 7);
            } else if (field == getTextField(65)) {
                int value = Integer.parseInt(field.getText().replace("#", "").trim(), 16) & 0xFFFFFF;
                int previous = element == 0 ? selectedMeta().getInteger("TextColor") : panel.getInteger(prefix() + "Color");
                if (previous < 0) previous = panel.getInteger("RowColor");
                if (value == previous) return;
                if (element == 0) selectedMeta().setInteger("TextColor", value); else panel.setInteger(prefix() + "Color", value);
                // 只改"基础颜色"；要刷到具体文字上请框选后用"应用颜色"，
                // 免得在输入框里改个默认色就把整段已上色的文本覆盖掉。
                changed(element == 0);
            } else if (field == getTextField(66)) {
                // 这里现在同时接受内置图标名和"相对资源路径"：
                //   portal            → 内置图标（icons.txt 里的一格）
                //   custom/portal.png → 资源包 assets/cnpcplus/textures/gui/transporter/custom/portal.png
                //   mypack:textures/gui/transporter/x.png → 直接指向别的命名空间
                String raw = field.getText().trim();
                String key = TransportData.image(raw);
                if (!raw.isEmpty() && key.isEmpty()) { hint = I18n.format("cnpcplus.transport.image_invalid_path"); syncWidgets(); return; }
                if (imageTag().getString(imageKey()).equals(key)) return;
                imageTag().setString(imageKey(), key); changed(element == 4 || element == 7);
            } else if (field == getTextField(68)) {
                importPath = field.getText().trim();
            }
        } catch (NumberFormatException ignored) { }
    }
    /**
     * GuiTextArea 只在 setText() 里回调 ITextChangeListener，键盘输入不会触发；
     * 而 syncWidgets() 又是在 syncing=true 下调 setText，回调被防抖挡掉。
     * 结果就是"名字已经敲进输入框、NBT 里还是 default name"。
     * 这里直接读控件文本并写回 NBT，输入时和保存前都跑一遍。
     */
    private void syncText() {
        if (!editor || !ready || syncing || !textElement() || area == null) return;
        int max = element == 2 ? 2048 : element == 3 ? 16 : 64;
        String text = TransportData.limit(area.getText(), max);
        if (text.equals(contentTag().getString(contentKey()))) return;
        contentTag().setString(contentKey(), text);
        syncSpans(text);
        changed(element != 3);
    }

    /**
     * 样式是按字符下标存的。删除文字后若不裁剪，旧颜色会落到之后新输入的字上
     * —— 表现就是"文字清空重打，颜色又自己回来了"。
     */
    private void syncSpans(String text) {
        NBTTagCompound tag = contentTag();
        String key = spansKey(), raw = tag.getString(key);
        String trimmed = TransportText.trim(raw, text.length());
        if (!trimmed.equals(raw)) tag.setString(key, trimmed);
    }

    /** 名称是必填项：服务端会忽略空名字，再把旧名字回带，等于把输入框内容弹回去。 */
    private static boolean blankName(NBTTagCompound entry) {
        return entry != null && !entry.getBoolean("Delete") && entry.hasKey("Name")
                && entry.getString("Name").trim().isEmpty();
    }

    private boolean anyBlankName() {
        for (NBTTagCompound entry : entries.values()) if (blankName(entry)) return true;
        return false;
    }

    private void flushFields() {
        if (!editor || !ready) return;
        syncText();
        for (int id = 61; id <= 66; id++) if (getTextField(id) != null && getTextField(id).enabled) unFocused(getTextField(id));
        GuiNpcTextField path = getTextField(68);
        if (path != null && path.enabled) unFocused(path);
    }
    /**
     * 仿 IBE 的文本编辑器：所有上色/样式只作用于**框选**范围。
     * 以前没有选区就当成"整段"，点一下色块整段变色，是"扎手"的主要来源。
     */
    private int[] selection(int length) {
        if (area == null) return null;
        int start = Math.max(0, Math.min(length, area.getSelectionBeforeText().length()));
        int end = Math.max(start, Math.min(length, length - area.getSelectionAfterText().length()));
        return end > start ? new int[]{start, end} : null;
    }

    private void format(int color, int flags, boolean reset) {
        if (!textElement() || area == null) return;
        // 键盘输入不会回调 textUpdate，先把文本框现状写回 NBT，否则选区下标是按旧正文算的。
        syncText();
        String text = contentTag().getString(contentKey());
        int[] range = selection(text.length());
        if (range == null) {
            // 只有"重置"保留整段语义（玩家点重置就是想把这一项恢复默认）；
            // 上色/加粗必须先框选，避免一次点击把整段改掉。
            if (!reset || text.isEmpty()) { hint = I18n.format("cnpcplus.transport.select_first"); return; }
            range = new int[]{0, text.length()};
        }
        String spans = contentTag().getString(spansKey());
        String value = reset ? TransportText.style(spans, text.length(), range[0], range[1], 0, true)
                : color >= 0 ? TransportText.color(spans, text.length(), range[0], range[1], color)
                : TransportText.style(spans, text.length(), range[0], range[1], flags, false);
        if (value.equals(spans)) return;
        contentTag().setString(spansKey(), value);
        changed(element != 3);
    }

    /** 清除选区颜色，其余位置的颜色保持不变（IBE 的 removeColorFormatting）。 */
    private void clearColor() {
        if (!textElement() || area == null) return;
        syncText();
        String text = contentTag().getString(contentKey());
        int[] range = selection(text.length());
        if (range == null) { hint = I18n.format("cnpcplus.transport.select_first"); return; }
        String spans = contentTag().getString(spansKey());
        String value = TransportText.clear(spans, text.length(), range[0], range[1]);
        if (value.equals(spans)) { hint = I18n.format("cnpcplus.transport.color_cleared"); return; }
        contentTag().setString(spansKey(), value);
        changed(element != 3);
        hint = I18n.format("cnpcplus.transport.color_cleared");
    }

    /** 把 RGB 输入框里的颜色刷到选区；输入框本身只决定"基础颜色"。 */
    private void applyColorField() {
        GuiNpcTextField field = getTextField(65);
        if (field == null) return;
        try {
            format(Integer.parseInt(field.getText().replace("#", "").trim(), 16) & 0xFFFFFF, 0, false);
        } catch (NumberFormatException ignored) { hint = I18n.format("cnpcplus.transport.color_invalid"); }
    }

    @Override public void buttonEvent(GuiButton button) {
        if (!ready) return;
        if (button.id == 60) { flushFields(); sendSave(true); hint = I18n.format("cnpcplus.transport.saving"); }
        if (button.id == 80) { flushFields(); element = (element + 1) % 8; sidebarScroll = 0; syncWidgets(); }
        if (button.id >= 70 && button.id <= 73) format(-1, button.id == 70 ? 1 : button.id == 71 ? 2 : 4, button.id == 73);
        if (button.id == 89) clearColor();
        if (button.id == 90) applyColorField();
        if (button.id == 81) chooseImage();
        if (button.id == 82) { imageTag().setString(imageKey(), ""); changed(element == 4 || element == 7); syncWidgets(); }
        if (button.id == 83) { iconMode = 1 - iconMode; syncWidgets(); }
        if (button.id == 84) { selectedMeta().setInteger("ImageMode", (selectedMeta().getInteger("ImageMode") + 1) % 5); changed(true); syncWidgets(); }
        if (button.id == 92 && selected != current.getInteger("Id")) { selectedMeta().setBoolean("Delete", true); changed(true); }
    }

    private void chooseImage() {
        flushFields();
        String raw = importPath == null ? "" : importPath.trim();
        if (raw.isEmpty()) { hint = I18n.format("cnpcplus.transport.import_hint"); return; }
        // 先当"资源包路径"处理：custom/portal.png 只要在资源包里存在，就直接记成路径，
        // 不上传、不落临时文件，以后换材质包里的同名文件就自动换图。
        if (applyResourcePath(raw)) return;
        try {
            java.nio.file.Path file = resolveImage(raw);
            if (Files.isDirectory(file)) {
                lastImportDir = file.toString();
                importPath = lastImportDir;
                syncWidgets();
                hint = I18n.format("cnpcplus.transport.import_hint");
                return;
            }
            if (!Files.isRegularFile(file)) { hint = I18n.format("cnpcplus.transport.image_failed") + " " + raw; return; }
            if (Files.size(file) > PacketTransportImage.MAX_BYTES) throw new IOException("Image exceeds 4 MiB");
            byte[] bytes = Files.readAllBytes(file);
            if (!TransportImages.validPng(bytes)) throw new IOException("PNG dimensions must be at most 2048 x 2048");
            lastImportDir = file.getParent() == null ? "" : file.getParent().toString();
            importPath = file.toString();
            uploadImage(TransportImages.hash(bytes), bytes);
        } catch (Exception error) {
            hint = I18n.format("cnpcplus.transport.image_failed") + " " + error.getMessage();
        }
    }

    /** 相对/命名空间路径如果能在客户端资源包里读到，就直接写进条目字段并返回 true。 */
    private boolean applyResourcePath(String raw) {
        String relative = TransportData.path(raw);
        if (relative.isEmpty()) return false;
        ResourceLocation texture = TransportData.resource(relative);
        if (!TransportAssets.exists(texture)) return false;
        imageTag().setString(imageKey(), relative);
        changed(element == 4 || element == 7);
        hint = I18n.format("cnpcplus.transport.path_applied", relative);
        syncWidgets();
        return true;
    }

    /** 绝对路径优先；否则在记忆目录里找；再否则补 .png 重试。 */
    private java.nio.file.Path resolveImage(String raw) {
        java.nio.file.Path direct = java.nio.file.Paths.get(raw);
        if (Files.isRegularFile(direct) || Files.isDirectory(direct)) return direct;
        java.util.List<String> names = new java.util.ArrayList<>();
        names.add(raw);
        if (!raw.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) names.add(raw + ".png");
        if (!lastImportDir.isEmpty()) for (String name : names) {
            java.nio.file.Path joined = java.nio.file.Paths.get(lastImportDir).resolve(name);
            if (Files.isRegularFile(joined)) return joined;
        }
        for (String name : names) {
            java.nio.file.Path appended = java.nio.file.Paths.get(name);
            if (Files.isRegularFile(appended)) return appended;
        }
        return direct;
    }

    private void uploadImage(String key, byte[] bytes) {
        java.nio.ByteBuffer header = java.nio.ByteBuffer.wrap(bytes);
        uploads.put(key, new int[]{selected, element, iconMode, header.getInt(16), header.getInt(20)});
        ClientTransportImages.cache(key, bytes);
        for (int offset = 0; offset < bytes.length; offset += PacketTransportImage.CHUNK) {
            CraftingViewNetwork.CHANNEL.sendToServer(new PacketTransportImage(npcId, key, offset, bytes));
        }
        hint = I18n.format("cnpcplus.transport.uploading");
        syncWidgets();
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        if (editor) { drawRect(0, 0, sidebarWidth(), this.height, 0xE0202020); positionWidgets(); }
        GlStateManager.pushMatrix(); GlStateManager.translate(originX, originY, 0); GlStateManager.scale(scale, scale, 1);
        GlStateManager.enableBlend(); GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        TransportAssets.draw("panel_main.png", 0, 0, 952, 535);
        TransportAssets.draw("tab.png", 74, -20, 184, 42);
        icon(panel.getString("TabIcon"), 90, -11, 32 * panel.getInteger("TabIconSize") / 100, "tab_star.png");
        TransportText.draw(this.getFontRenderer(), panel.getString("TabText"), panel.getString("TabSpans"),
                128 + panel.getInteger("TabX"), -6 + panel.getInteger("TabY"), panel.getInteger("TabSize"), panel.getInteger("TabColor"), 0);
        TransportAssets.draw("search_box.png", 74, 36, 404, 44); TransportAssets.draw("search_icon.png", 86, 46, 24, 24);
        TransportAssets.draw("list_gutter.png", 74, 88, 404, 354);
        for (int row = 0; row < 5 && row + offset < filtered.size(); row++) {
            int id = filtered.get(row + offset), y = 98 + row * 70;
            NBTTagCompound entry = meta(id);
            boolean lifted = rowDrag && id == rowPressId;
            TransportAssets.draw(id == selected || lifted ? "row_bg.png" : "row_normal.png", 86, y, 372, 64);
            if (editor) {
                // 左侧小“把手”：提示这一行可以拖动排序。
                for (int line = 0; line < 3; line++) drawRect(77, y + 28 + line * 3, 83, y + 29 + line * 3, lifted ? 0xFFFFFFFF : 0xFF888888);
            }
            String key = id == selected && !entry.getString("IconSel").isEmpty() ? entry.getString("IconSel") : entry.getString("Icon");
            int iconSize = 40 * entry.getInteger("IconSize") / 100;
            icon(key, 94 + (40 - iconSize) / 2, y + 12 + (40 - iconSize) / 2, iconSize, "default_icon.png");
            int size = entry.getInteger("TextSize") > 0 ? entry.getInteger("TextSize") : panel.getInteger("RowSize");
            int color = entry.getInteger("TextColor") >= 0 ? entry.getInteger("TextColor") : panel.getInteger("RowColor");
            scissor(140, y + 2, 308, 60);
            TransportText.draw(this.getFontRenderer(), entry.getString("Name"), entry.getString("NameSpans"),
                    148 + panel.getInteger("RowX"), y + (64 - size) / 2 + panel.getInteger("RowY"), size, color, 0);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        if (filtered.size() > 5) {
            int thumb = Math.max(30, 340 * 5 / filtered.size());
            TransportAssets.draw("scroll_track.png", 458, 96, 20, 340);
            TransportAssets.draw("scroll_thumb.png", 460, 96 + (340 - thumb) * offset / (filtered.size() - 5), 16, thumb);
        }
        TransportAssets.draw("current_panel.png", 74, 452, 404, 64);
        NBTTagCompound own = meta(current.getInteger("Id"));
        String ownIcon = panel.getString("CurIcon"); if (ownIcon.isEmpty()) ownIcon = own.getString("Icon");
        int currentSize = 40 * panel.getInteger("CurIconSize") / 100;
        icon(ownIcon, 86 + (40 - currentSize) / 2, 464 + (40 - currentSize) / 2, currentSize, "current_icon.png");
        TransportText.draw(this.getFontRenderer(), I18n.format("cnpcplus.transport.current"), "", 136 + panel.getInteger("CurX"), 462 + panel.getInteger("CurY"), 12, 0x444444, 0);
        TransportText.draw(this.getFontRenderer(), own.getString("Name"), own.getString("NameSpans"), 136 + panel.getInteger("CurX"), 480 + panel.getInteger("CurY"), panel.getInteger("CurSize"), panel.getInteger("CurColor"), 320);
        drawImage();
        TransportAssets.draw("image_frame.png", 500, 44, 440, 282);
        TransportAssets.draw("intro_panel.png", 500, 342, 440, 178);
        scissor(510, 350, 420, 162);
        String description = selectedMeta().getString("Desc");
        TransportText.draw(this.getFontRenderer(), description.isEmpty() ? I18n.format("cnpcplus.transport.no_description") : description,
                selectedMeta().getString("DescSpans"), 516 + panel.getInteger("IntroX"), 358 + panel.getInteger("IntroY"),
                panel.getInteger("IntroSize"), description.isEmpty() ? 0x777777 : panel.getInteger("IntroColor"), 408);
        GL11.glDisable(GL11.GL_SCISSOR_TEST); GlStateManager.disableBlend(); GlStateManager.popMatrix();
        if (editor) drawSidebar();
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (editor) this.getFontRenderer().drawString(this.getFontRenderer().trimStringToWidth(hint, this.width - 8), 4, this.height - 17, 0xFFFFFF);
    }

    private void icon(String key, int x, int y, int size, String fallback) {
        if (key.isEmpty()) { TransportAssets.draw(fallback, x, y, size, size); return; }
        if (TransportData.isPath(key)) {
            // 资源包路径：直接从客户端资源管理器读，不经上传通道，换图只要换包里的文件。
            ResourceLocation texture = TransportData.resource(key);
            TransportAssets.draw(TransportAssets.exists(texture) ? texture : TransportAssets.resource("default_icon.png"), x, y, size, size);
            return;
        }
        if (TransportAssets.ICONS.contains(key)) { TransportAssets.draw("icons/" + key + ".png", x, y, size, size); return; }
        ClientTransportImages.Image image = ClientTransportImages.get(npcId, editor, key);
        if (image != null) TransportAssets.draw(image.texture, x, y, size, size);
        else TransportAssets.draw(fallback, x, y, size, size);
    }
    private void scissor(int x, int y, int width, int height) {
        double factorX = this.mc.displayWidth / (double) this.width, factorY = this.mc.displayHeight / (double) this.height;
        int x1 = (int) Math.floor(sx(x) * factorX), y1 = (int) Math.floor(sy(y) * factorY);
        int x2 = (int) Math.ceil(sx(x + width) * factorX), y2 = (int) Math.ceil(sy(y + height) * factorY);
        GL11.glEnable(GL11.GL_SCISSOR_TEST); GL11.glScissor(x1, this.mc.displayHeight - y2, Math.max(0, x2 - x1), Math.max(0, y2 - y1));
    }
    private void drawImage() {
        NBTTagCompound meta = selectedMeta(); String key = meta.getString("Image");
        if (key.isEmpty()) return;
        ResourceLocation texture; int width, height;
        if (TransportData.isPath(key)) {
            // 路径贴图：尺寸取自 PNG 头，和上传图片走同一套绘制逻辑。
            texture = TransportData.resource(key);
            int[] size = TransportAssets.size(texture);
            if (size == null) return;
            width = size[0]; height = size[1];
        } else {
            ClientTransportImages.Image image = ClientTransportImages.get(npcId, editor, key);
            if (image == null) return;
            texture = image.texture; width = image.width; height = image.height;
        }
        int mode = meta.getInteger("ImageMode"), offX = meta.getInteger("ImgX"), offY = meta.getInteger("ImgY");
        double zoom = meta.getInteger("ImgScale") / 100.0;
        if (zoom <= 0) zoom = 1;
        double fit = Math.min(440.0 / width, 282.0 / height);
        int w = (int) Math.max(1, Math.round(width * (mode == 3 || mode == 2 ? Math.min(1, fit) : fit) * zoom));
        int h = (int) Math.max(1, Math.round(height * (mode == 3 || mode == 2 ? Math.min(1, fit) : fit) * zoom));
        if (mode == 1) { w = (int) (440 * zoom); h = (int) (282 * zoom); }
        if (mode == 2 && (long) (440 / w + 2) * (282 / h + 2) > 8192) {
            mode = 4; w = Math.max(1, (int) Math.round(width * fit)); h = Math.max(1, (int) Math.round(height * fit));
        }
        scissor(504, 48, 432, 274);
        if (mode == 2 && (long) (440 / w + 2) * (282 / h + 2) <= 8192) {
            for (int y = 44 + Math.floorMod(offY, h) - h; y < 326; y += h) for (int x = 500 + Math.floorMod(offX, w) - w; x < 940; x += w) TransportAssets.draw(texture, x, y, w, h);
        } else TransportAssets.draw(texture, 500 + (440 - w) / 2 + offX, 44 + (282 - h) / 2 + offY, w, h);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private void drawSidebar() {
        this.getFontRenderer().drawString(I18n.format("cnpcplus.transport.edit"), 5, 8, 0xFFFFFF);
        if (editor && search.isEmpty()) this.getFontRenderer().drawString(I18n.format("cnpcplus.transport.order_hint"), 5, 19, 0x999999);
        int y = -sidebarScroll;
        if (textElement()) {
            label("cnpcplus.transport.size", 4, 144 + y); label("X", 4, 166 + y); label("Y", (sidebarWidth() - 18) / 2 + 14, 166 + y);
            label("RGB", 4, 188 + y);
            label("cnpcplus.transport.palette", 4, 250 + y);
            for (int i = 0; i < PALETTE.length; i++) {
                int px = 6 + (i % 8) * 18, py = 254 + (i / 8) * 18 + y;
                if (py >= 26 && py + 14 < this.height - 24) {
                    drawRect(px, py, px + 14, py + 14, 0xFF000000 | PALETTE[i]);
                    drawRect(px, py, px + 14, py + 1, 0xFF000000);
                    drawRect(px, py + 13, px + 14, py + 14, 0xFF000000);
                }
            }
            int[] range = selection(contentTag().getString(contentKey()).length());
            String selectedText = range == null ? I18n.format("cnpcplus.transport.select_hint")
                    : I18n.format("cnpcplus.transport.selection", range[1] - range[0]);
            if (292 + y >= 26 && 302 + y < this.height - 24)
                this.getFontRenderer().drawString(this.getFontRenderer().trimStringToWidth(selectedText, sidebarWidth() - 10), 5, 292 + y, 0xBBBBBB);
        } else {
            label("cnpcplus.transport.percent", 4, (element == 7 ? 156 : 134) + y);
            if (element == 7) {
                label("cnpcplus.transport.image_path", 4, 70 + y);
                label("X", 4, 182 + y); label("Y", (sidebarWidth() - 18) / 2 + 14, 182 + y);
                label("cnpcplus.transport.drag_image", 4, 204 + y);
            }
            else {
                int columns = Math.max(1, (sidebarWidth() - 12) / 30);
                for (int i = 0; i < TransportAssets.ICONS.size(); i++) {
                    int px = 4 + i % columns * 30, py = 154 + i / columns * 30 + y;
                    if (py < 26 || py + 28 >= this.height - 24) continue;
                    String key = TransportAssets.ICONS.get(i);
                    drawRect(px, py, px + 28, py + 28, imageTag().getString(imageKey()).equals(key) ? 0xFF85969A : 0xFF464646);
                    GlStateManager.enableBlend(); TransportAssets.draw("icons/" + key + ".png", px + 2, py + 2, 24, 24); GlStateManager.disableBlend();
                }
            }
        }
        int full = sidebarHeight(), visible = this.height - 50;
        if (full > visible) {
            int thumb = Math.max(18, visible * visible / full), sy = 26 + (visible - thumb) * sidebarScroll / Math.max(1, full - visible);
            drawRect(sidebarWidth() - 6, 26, sidebarWidth() - 2, this.height - 24, 0xFF333333);
            drawRect(sidebarWidth() - 6, sy, sidebarWidth() - 2, sy + thumb, 0xFFBBBBBB);
        }
    }
    private void label(String key, int x, int y) { if (y >= 26 && y + 10 < this.height - 24) this.getFontRenderer().drawString(I18n.format(key), x, y, 0xDDDDDD); }
    private int sidebarHeight() { return textElement() ? 336 : element == 7 ? 224 : 160 + (TransportAssets.ICONS.size() + Math.max(1, (sidebarWidth() - 12) / 30) - 1) / Math.max(1, (sidebarWidth() - 12) / 30) * 30; }

    @Override public void mouseClicked(int x, int y, int button) throws IOException {
        if (!ready) { super.mouseClicked(x, y, button); return; }
        if (editor && x < sidebarWidth()) {
            if (button == 0 && x >= sidebarWidth() - 8 && y >= 26 && y < this.height - 24) {
                draggingSidebar = true; sidebarAt(y); return;
            }
            int logicalY = y + sidebarScroll;
            // 调色板 16 格 = 2 行（254/272），点到行外一律忽略。
            if (button == 0 && textElement() && logicalY >= 254 && logicalY < 254 + ((PALETTE.length + 7) / 8) * 18 && x >= 6 && x < 150) {
                int index = (logicalY - 254) / 18 * 8 + (x - 6) / 18;
                if (index < PALETTE.length) { format(PALETTE[index], 0, false); return; }
            }
            if (button == 0 && element >= 4 && element <= 6 && logicalY >= 154 && y >= 26 && y < this.height - 24) {
                int columns = Math.max(1, (sidebarWidth() - 12) / 30);
                int index = (logicalY - 154) / 30 * columns + (x - 4) / 30;
                if (x >= 4 && x < 4 + columns * 30 && index >= 0 && index < TransportAssets.ICONS.size()) {
                    imageTag().setString(imageKey(), TransportAssets.ICONS.get(index)); changed(element == 4); syncWidgets(); return;
                }
            }
        }
        int dx = (int) ((x - originX) / scale), dy = (int) ((y - originY) / scale);
        if (button == 0 && ready && (!editor || x >= sidebarWidth())) {
            if (dx >= 458 && dx <= 478 && dy >= 96 && dy <= 436) { draggingScroll = true; scrollAt(dy); return; }
            // 编辑器里行左侧还画了拖动手柄（77..83），命中区一起放宽到 74，免得按在手柄上没反应。
            if (dx >= (editor ? 74 : 86) && dx < 458 && dy >= 98 && dy < 442) {
                int row = (dy - 98) / 70, index = offset + row;
                if (row < 5 && index < filtered.size()) {
                    flushFields(); int id = filtered.get(index); long now = System.currentTimeMillis();
                    if (!editor && id == lastClickId && now - lastClickTime <= 400) {
                        NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("Id", id);
                        CraftingViewNetwork.CHANNEL.sendToServer(new PacketTransportMeta(npcId, PacketTransportMeta.TRAVEL, tag)); close(); return;
                    }
                    selected = id; lastClickId = id; lastClickTime = now;
                    if (editor) {
                        // 记录按下点：鼠标纵向移动超过阈值就进入拖动排序，松手才写 Order。
                        rowPressId = id; rowPressY = y; rowDrag = false;
                        element = dx < 142 ? 4 : 0; sidebarScroll = 0; syncWidgets();
                    }
                    return;
                }
            }
            if (editor) {
                int next = -1;
                if (dx >= 500 && dx <= 940 && dy >= 44 && dy <= 326) next = 7;
                else if (dx >= 500 && dx <= 940 && dy >= 342 && dy <= 520) next = 2;
                else if (dx >= 74 && dx <= 478 && dy >= 452 && dy <= 516) next = dx < 130 ? 5 : 1;
                else if (dx >= 74 && dx <= 258 && dy >= -20 && dy <= 22) next = dx < 128 ? 6 : 3;
                if (next >= 0) {
                    flushFields(); element = next; sidebarScroll = 0; syncWidgets();
                    if (next == 7) { draggingImage = true; dragX = dx; dragY = dy; originalImageX = selectedMeta().getInteger("ImgX"); originalImageY = selectedMeta().getInteger("ImgY"); }
                    return;
                }
            }
        }
        super.mouseClicked(x, y, button);
    }
    private void scrollAt(int designY) { offset = Math.max(0, Math.min(Math.max(0, filtered.size() - 5), (designY - 96) * Math.max(0, filtered.size() - 5) / 340)); }
    private void sidebarAt(int y) {
        int maximum = Math.max(0, sidebarHeight() - (this.height - 50));
        sidebarScroll = Math.max(0, Math.min(maximum, (y - 26) * maximum / Math.max(1, this.height - 50)));
        positionWidgets();
    }
    @Override protected void mouseClickMove(int x, int y, int button, long time) {
        if (rowPressId >= 0) dragRow(y);
        else if (draggingSidebar) sidebarAt(y);
        else if (draggingScroll) scrollAt((int) ((y - originY) / scale));
        else if (draggingImage && editor) {
            selectedMeta().setInteger("ImgX", Math.max(-2000, Math.min(2000, originalImageX + (int) ((x - originX) / scale) - dragX)));
            selectedMeta().setInteger("ImgY", Math.max(-2000, Math.min(2000, originalImageY + (int) ((y - originY) / scale) - dragY)));
            changed(true); syncWidgets();
        } else super.mouseClickMove(x, y, button, time);
    }
    @Override protected void mouseReleased(int x, int y, int button) {
        if (rowDrag) commitOrder();
        rowDrag = false; rowPressId = -1;
        draggingScroll = false; draggingImage = false; draggingSidebar = false; super.mouseReleased(x, y, button);
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput(); int wheel = Mouse.getEventDWheel(); if (wheel == 0) return;
        int x = Mouse.getEventX() * this.width / this.mc.displayWidth, y = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        if (editor && x < sidebarWidth()) {
            sidebarScroll = Math.max(0, Math.min(Math.max(0, sidebarHeight() - (this.height - 50)), sidebarScroll + (wheel > 0 ? -18 : 18))); positionWidgets(); return;
        }
        int dx = (int) ((x - originX) / scale), dy = (int) ((y - originY) / scale);
        if (dx >= 74 && dx <= 478 && dy >= 88 && dy <= 442) { offset += wheel > 0 ? -1 : 1; applyFilter(); }
        else if (editor && element == 7 && dx >= 500 && dx <= 940 && dy >= 44 && dy <= 326) {
            selectedMeta().setInteger("ImgScale", Math.max(25, Math.min(400, selectedMeta().getInteger("ImgScale") + (wheel > 0 ? 5 : -5)))); changed(true); syncWidgets();
        }
    }
    @Override public void keyTyped(char character, int key) throws IOException {
        super.keyTyped(character, key);
        syncText();
        GuiNpcTextField field = this.getTextField(50);
        if (field != null && !field.getText().equals(search)) { search = field.getText(); applyFilter(); }
    }
    @Override public void updateScreen() {
        super.updateScreen();
        syncText();
        if (editor && dirty && System.currentTimeMillis() - lastChange >= 150) sendSave(false);
    }
    private void updateParent() {
        if (parentLocation == null || !ready) return;
        NBTTagCompound own = meta(current.getInteger("Id"));
        String name = own.getString("Name");
        // 名字是必填项：本地被清空时不要把空串回写，保留上一次有效的名字，
        // 否则原生页 save() 会把空名字发上去、再被服务端丢掉，看起来又"弹回来"。
        boolean keep = name.trim().isEmpty();
        parentLocation.id = own.getInteger("Id");
        if (!keep) parentLocation.name = name;
        TransportData.setLocation(parentLocation, own);
        // 原生页在 setSubGui 之后不会重建控件，它 save() 读的还是旧输入框文本。
        // 不回写的话，可视化编辑改完名字、一关原生页就被旧名字盖回去。
        if (parentGui != null && !keep) {
            parentGui.location.name = parentLocation.name;
            GuiNpcTextField field = parentGui.getTextField(0);
            if (field != null) field.setText(parentLocation.name);
        }
    }
    @Override public void save() { if (editor) { flushFields(); if (diskDirty) sendSave(true); updateParent(); } }
    @Override public void close() { closed = true; super.close(); }
    @Override public void onGuiClosed() { save(); closed = true; super.onGuiClosed(); }
}
