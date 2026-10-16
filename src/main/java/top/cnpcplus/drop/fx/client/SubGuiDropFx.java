package top.cnpcplus.drop.fx.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import top.cnpcplus.drop.client.GuiDropRecipes;
import top.cnpcplus.drop.fx.DropFxConfig;
import top.cnpcplus.drop.fx.DropFxCaps;
import top.cnpcplus.drop.fx.ShapePreset;
import top.cnpcplus.drop.fx.ShapeType;
import top.cnpcplus.drop.fx.runtime.DropFxSampler;
import top.cnpcplus.drop.fx.runtime.FxComputePool;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 掉落物配方粒子特效编辑器（233d d2，「允许自写面板」的落地）。
 *
 * <h3>外观（2026-10-17 哈基彬设计图定稿 + 三轮截图反馈）</h3>
 * <b>三块独立原版灰板面板</b>（无大底板，缝隙露父界面）：
 * 左最大块=粒子注册表（图标+滚动条，<b>列表内容区局部变黑</b>）；
 * 右上=形状预设列表（直观可见+滚动条）；右下=参数区（<b>可滚动</b>，
 * EditBox/切换钮跟随滚动量 setY+visible 联动）。
 * 每块材质=原版容器灰板（#C6C6C6+白亮边+暗边+黑描边，箱子同色系）。
 *
 * <h3>包围盒 420×256（第四轮反馈「严重超屏」后压缩）</h3>
 * GUI scale=4（1080p）时 scaled 屏幕只有 480×270——旧 430×320 使
 * {@code guiTop=(270-320)/2=-30} 负数整块顶出屏幕。现 420×256：
 * 最坏情形 gl=30/gt=7，全部内容在屏内。
 *
 * <h3>dirt 双开关（字节码实证两个来源，第四轮反馈后补齐）</h3>
 * vanilla {@link ObjectSelectionList#render} 画 dirt 有两处：
 * ① {@code renderBackground=true}→blit BACKGROUND_LOCATION 32×32
 *    于列表区 x0..x1×y0..y1（设置界面同款）；
 * ② <b>{@code renderTopAndBottom=true}（默认）</b>→blit dirt 于
 *    x0..x0+width×<b>0..y0</b>（屏幕顶到列表顶）与 y1..height——
 *    上一版漏关此项=「莫名其妙泥土」残留根因。
 * 三个列表全部 {@code setRenderBackground(false)+setRenderTopAndBottom(false)}，
 * 块内自己 fill 0x80000000 暗底（用户拍板「局部变黑」），
 * 滚动条仍由 vanilla 列表绘制。
 *
 * <h3>粒子图标三级链（兼容 mod，233d-UI2 后）</h3>
 * ① {@link ParticleIconLookup} 静态表（vanilla 88 粒子实证快速路径）；
 * ② {@link DynamicParticleIcon}（AT 开放 ParticleEngine.spriteSets，
 *    key=粒子注册 id——<b>vanilla 与任意 mod 同一命名空间</b>，
 *    blit 直接吃 TextureAtlasSprite，mod 粒子零配置）；
 * ③ 黄 '?'（纯代码注册无 sprite 的粒子）。
 *
 * <h3>构造器语义</h3>
 * {@code (mc, width, height, y0, y1, itemHeight)} 无 x 参；包围盒 place() 摆。
 *
 * <h3>233d-UI5 三修（第六轮反馈）</h3>
 * ① 三列表 override {@code getScrollbarPosition()=x1-6}：vanilla 公式
 *    {@code width/2+124} 是全屏居中列表的绝对坐标，place 后滚动条错位
 *    （画到左面板上）；且 {@code getEntryAtPosition} 要求
 *    mouseX&lt;getScrollbarPosition 才命中 entry——形状/参数列表整体在
 *    其右侧→「＋添加预设」等所有点击全 miss，同一根因一并修复；
 * ② 整体下移：m_7856_ 里 guiTop+=8；
 * ③ 父界面 icon 置顶由 GuiIconToggleButton hasSubGui 拦截（见该类注释）。
 *
 * <h3>保存链</h3>
 * save() 写回父内存字段；落盘靠父「保存」按钮；取消/ESC=cancelled 不写。
 */
public class SubGuiDropFx extends GuiBasic {

    private static final int FIELD_RADIUS = 100;
    private static final int FIELD_ROTSPD = 101;
    private static final int FIELD_ROT = 102;
    private static final int FIELD_INSET_AMT = 103;
    private static final int FIELD_DURATION = 104;
    private static final int FIELD_DENSITY = 105;
    private static final int FIELD_OX = 106;
    private static final int FIELD_OY = 107;
    private static final int FIELD_OZ = 108;

    private static final int BG_FILL = 0xFFC6C6C6;
    private static final int BG_LIGHT = 0xFFFFFFFF;
    private static final int BG_DARK = 0xFF555555;
    /** 列表内容区「局部变黑」底。 */
    private static final int LIST_DARK = 0x80000000;

    /** 参数行高（列表 itemHeight 与 EditBox 跟随同步用）。 */
    private static final int PARAM_ROW = 18;
    /** 参数总行数（233d-UI4：每参数独立一行=10 行，列表滚动显示）。 */
    private static final int PARAM_ROWS = 10;

    private final GuiDropRecipes parent;
    private final List<DropFxConfig> editCopy = new ArrayList<>();
    private final List<String> particleIds = new ArrayList<>();
    private final Map<Integer, EditBox> editBoxes = new HashMap<>();

    private String selectedParticleId = null;
    private int selectedShapeIndex = -1;
    private boolean cancelled = false;
    private boolean settingFields = false;

    private ParticleList particleList;
    private ShapeList shapeList;
    private ParamList paramList;
    private Button insetButton;

    public SubGuiDropFx(GuiDropRecipes parent, List<DropFxConfig> currentFxs) {
        this.parent = parent;
        for (DropFxConfig c : currentFxs) this.editCopy.add(c.copy());
        // 定位包围盒 420×256：适配 GUI scale=4（1080p→480×270）不超屏
        this.imageWidth = 420;
        this.imageHeight = 256;
        this.drawDefaultBackground = false; // 无全屏 dirt
        this.closeOnEsc = true;
        List<String> ids = new ArrayList<>();
        for (ResourceLocation id : ForgeRegistries.PARTICLE_TYPES.getKeys()) {
            ids.add(id.toString());
        }
        ids.sort(Comparator.naturalOrder());
        this.particleIds.addAll(ids);
    }

    @Override
    public void m_7856_() {
        super.m_7856_();
        // 整体下移 8px（233d-UI5「界面应该下移一点」）：guiTop 偏移后所有
        // place/render/按钮坐标自动跟随。scale=4（1080p，屏 270）校验：
        // 黑边底=gt+252=15+252=267<270、黑边顶=gt-2=13>0，不超屏。
        this.guiTop += 8;
        int gl = this.guiLeft;
        int gt = this.guiTop;

        // 左块（最大）：粒子注册表（width=196 贴面板内缘，消 4px 灰缝防像第二条滚动条）
        if (this.particleList == null) {
            this.particleList = new ParticleList();
            killVanillaDirt(this.particleList);
        }
        this.particleList.place(gl + 6, gt + 18);
        this.addRenderableWidget(this.particleList);

        // 右上块：形状预设（width=206 贴面板内缘）
        if (this.shapeList == null) {
            this.shapeList = new ShapeList();
            killVanillaDirt(this.shapeList);
        }
        this.shapeList.place(gl + 212, gt + 16);
        this.addRenderableWidget(this.shapeList);

        // 右下块：参数（10 行每参数一行滚动；EditBox 叠加跟随滚动）
        if (this.paramList == null) {
            this.paramList = new ParamList();
            killVanillaDirt(this.paramList);
        }
        this.paramList.place(gl + 212, gt + 120);
        this.addRenderableWidget(this.paramList);

        // 参数 EditBox（每行一个 x 对齐列表左缘+100；y 每帧 syncParamWidgets 跟随滚动）
        this.editBoxes.clear();
        this.addRenderableWidget(field(FIELD_RADIUS, 0, 0, 70));
        this.addRenderableWidget(field(FIELD_ROTSPD, 0, 0, 70));
        this.addRenderableWidget(field(FIELD_ROT, 0, 0, 70));
        this.addRenderableWidget(field(FIELD_INSET_AMT, 0, 0, 70));
        this.addRenderableWidget(field(FIELD_DURATION, 0, 0, 70));
        this.addRenderableWidget(field(FIELD_DENSITY, 0, 0, 70));
        this.addRenderableWidget(field(FIELD_OX, 0, 0, 70));
        this.addRenderableWidget(field(FIELD_OY, 0, 0, 70));
        this.addRenderableWidget(field(FIELD_OZ, 0, 0, 70));

        // 内陷/外扩切换钮（参数行9 右侧，跟随滚动）
        this.insetButton = Button.builder(
                        Component.translatable(this.currentInsetLabelKey()), b -> {
                            ShapePreset s = this.currentShape();
                            if (s == null) return;
                            s.inset = !s.inset;
                            b.setMessage(Component.translatable(this.currentInsetLabelKey()));
                            this.prefetchCurrent();
                        })
                .bounds(0, 0, 60, 18).build();
        this.addRenderableWidget(this.insetButton);

        // 底部按钮行（右下参数板内底部，不再悬空在板外——233d-UI4）
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                        b -> this.close())
                .bounds(gl + 212, gt + 218, 40, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),
                        b -> { this.cancelled = true; this.close(); })
                .bounds(gl + 256, gt + 218, 40, 20).build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("cnpcplus.dropfx.delshape"), b -> this.deleteShape())
                .bounds(gl + 300, gt + 218, 50, 20).build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("cnpcplus.dropfx.delparticle"), b -> this.deleteParticle())
                .bounds(gl + 354, gt + 218, 62, 20).build());

        this.refreshFieldsFromShape();
        this.updateFieldEnabled();
        this.rebuildLists();
        this.syncParamWidgets();
    }

    /**
     * 关掉 vanilla 列表自带 dirt 的两个来源（字节码实证）：
     * renderBackground=列表区 dirt；renderTopAndBottom=列表外上下
     * dirt 条（从屏幕边画到列表边，旧版漏关=泥土残留根因）。
     */
    private static void killVanillaDirt(ObjectSelectionList<?> list) {
        list.setRenderBackground(false);
        list.setRenderTopAndBottom(false);
        list.setRenderSelection(false);
    }

    private EditBox field(int id, int x, int y, int width) {
        EditBox box = new EditBox(Minecraft.getInstance().font, x, y, width, 16, Component.literal(""));
        box.setMaxLength(16);
        box.setResponder(text -> {
            if (this.settingFields) return;
            this.applyField(id, text);
        });
        this.editBoxes.put(id, box);
        return box;
    }

    /**
     * 参数区滚动联动（233d-UI4：每参数独立一行，10 行滚动）：
     * EditBox/切换钮的 y=列表内容行位-滚动量，滚出可视区 visible=false
     * （1.20.1 AbstractWidget 无 setVisible，public 字段直赋值）。
     * x 每帧对齐参数列表左缘+100（标签左框右一行一项）。每帧 render 前调用。
     */
    private void syncParamWidgets() {
        if (this.paramList == null) return;
        double sc = this.paramList.getScrollAmount();
        int base = this.paramList.top() + 4;
        int top = this.paramList.top();
        int bot = this.paramList.bottom();
        int lx = this.paramList.left();
        // 行0..8：每行一个 EditBox（标签在列表 Entry 自绘于左，框叠在右）
        placeXY(FIELD_RADIUS, lx + 100, base + 0 * PARAM_ROW + 1, sc, top, bot);
        placeXY(FIELD_ROTSPD, lx + 100, base + 1 * PARAM_ROW + 1, sc, top, bot);
        placeXY(FIELD_ROT, lx + 100, base + 2 * PARAM_ROW + 1, sc, top, bot);
        placeXY(FIELD_INSET_AMT, lx + 100, base + 3 * PARAM_ROW + 1, sc, top, bot);
        placeXY(FIELD_DURATION, lx + 100, base + 4 * PARAM_ROW + 1, sc, top, bot);
        placeXY(FIELD_DENSITY, lx + 100, base + 5 * PARAM_ROW + 1, sc, top, bot);
        placeXY(FIELD_OX, lx + 100, base + 6 * PARAM_ROW + 1, sc, top, bot);
        placeXY(FIELD_OY, lx + 100, base + 7 * PARAM_ROW + 1, sc, top, bot);
        placeXY(FIELD_OZ, lx + 100, base + 8 * PARAM_ROW + 1, sc, top, bot);
        // 行9：类型行右侧切换钮
        if (this.insetButton != null) {
            int y = base + 9 * PARAM_ROW + 1 - (int) sc;
            this.insetButton.setX(lx + 130);
            this.insetButton.setY(y);
            this.insetButton.visible = y >= top && y + 18 <= bot;
        }
    }

    private void placeXY(int fieldId, int x, int yUnscrolled, double sc, int top, int bot) {
        EditBox b = this.editBoxes.get(fieldId);
        if (b == null) return;
        int y = yUnscrolled - (int) sc;
        b.setX(x);
        b.setY(y);
        b.visible = y >= top && y + 16 <= bot; // 1.20.1 无 setVisible，public 字段
    }

    // ===== 编辑逻辑 =====

    private DropFxConfig currentConfig() {
        for (DropFxConfig c : this.editCopy) {
            if (c.particleId.equals(this.selectedParticleId)) return c;
        }
        return null;
    }

    private ShapePreset currentShape() {
        DropFxConfig c = this.currentConfig();
        if (c == null || this.selectedShapeIndex < 0
                || this.selectedShapeIndex >= c.shapes.size()) return null;
        return c.shapes.get(this.selectedShapeIndex);
    }

    private boolean particleConfigured(String id) {
        for (DropFxConfig c : this.editCopy) {
            if (c.particleId.equals(id) && !c.shapes.isEmpty()) return true;
        }
        return false;
    }

    private void selectParticle(String id) {
        this.selectedParticleId = id;
        this.selectedShapeIndex = -1;
        this.refreshFieldsFromShape();
        this.updateFieldEnabled();
        this.rebuildLists();
    }

    private void selectShape(int index) {
        this.selectedShapeIndex = index;
        this.refreshFieldsFromShape();
        this.updateFieldEnabled();
        this.rebuildLists();
        this.prefetchCurrent();
    }

    private void addShape() {
        DropFxConfig c = this.currentConfig();
        if (c == null) {
            if (this.selectedParticleId == null) return;
            c = new DropFxConfig(this.selectedParticleId);
            this.editCopy.add(c);
        }
        if (c.shapes.size() >= DropFxCaps.MAX_SHAPES_PER_CONFIG) return;
        c.shapes.add(new ShapePreset());
        this.selectShape(c.shapes.size() - 1);
    }

    private void deleteShape() {
        DropFxConfig c = this.currentConfig();
        if (c == null || this.selectedShapeIndex < 0
                || this.selectedShapeIndex >= c.shapes.size()) return;
        c.shapes.remove(this.selectedShapeIndex);
        if (c.shapes.isEmpty()) this.editCopy.remove(c);
        this.selectedShapeIndex = -1;
        this.refreshFieldsFromShape();
        this.updateFieldEnabled();
        this.rebuildLists();
    }

    private void deleteParticle() {
        if (this.selectedParticleId == null) return;
        this.editCopy.removeIf(c -> c.particleId.equals(this.selectedParticleId));
        this.selectedParticleId = null;
        this.selectedShapeIndex = -1;
        this.refreshFieldsFromShape();
        this.updateFieldEnabled();
        this.rebuildLists();
    }

    private void applyField(int id, String text) {
        ShapePreset s = this.currentShape();
        if (s == null) return;
        double v;
        try {
            v = Double.parseDouble(text.trim());
        } catch (NumberFormatException ex) {
            return;
        }
        switch (id) {
            case FIELD_RADIUS -> s.radius = clamp(v, -10, 10);
            case FIELD_ROTSPD -> s.rotSpeed = clamp(v, -10, 10);
            case FIELD_ROT -> s.rotation = clamp(v, -10, 10);
            case FIELD_INSET_AMT -> s.insetAmount = clamp(v, -10, 10);
            case FIELD_DURATION -> s.durationTicks =
                    (int) clamp(Math.round(v), 1, DropFxCaps.MAX_DURATION_TICKS);
            case FIELD_DENSITY -> s.density = clamp(v, 0.01, 10);
            case FIELD_OX -> s.offsetX = clamp(v, -10, 10);
            case FIELD_OY -> s.offsetY = clamp(v, -10, 10);
            case FIELD_OZ -> s.offsetZ = clamp(v, -10, 10);
            default -> { }
        }
        this.prefetchCurrent();
    }

    private void prefetchCurrent() {
        ShapePreset s = this.currentShape();
        if (s == null) return;
        ShapePreset snapshot = s.copy();
        FxComputePool.submit(() -> DropFxSampler.prefetch(snapshot));
    }

    private void refreshFieldsFromShape() {
        ShapePreset s = this.currentShape();
        this.settingFields = true;
        try {
            set(FIELD_RADIUS, s == null ? "" : fmt(s.radius));
            set(FIELD_ROTSPD, s == null ? "" : fmt(s.rotSpeed));
            set(FIELD_ROT, s == null ? "" : fmt(s.rotation));
            set(FIELD_INSET_AMT, s == null ? "" : fmt(s.insetAmount));
            set(FIELD_DURATION, s == null ? "" : String.valueOf(s.durationTicks));
            set(FIELD_DENSITY, s == null ? "" : fmt(s.density));
            set(FIELD_OX, s == null ? "" : fmt(s.offsetX));
            set(FIELD_OY, s == null ? "" : fmt(s.offsetY));
            set(FIELD_OZ, s == null ? "" : fmt(s.offsetZ));
        } finally {
            this.settingFields = false;
        }
    }

    private void set(int id, String v) {
        EditBox b = this.editBoxes.get(id);
        if (b != null) b.setValue(v);
    }

    private void updateFieldEnabled() {
        boolean on = this.currentShape() != null;
        for (EditBox b : this.editBoxes.values()) b.setEditable(on);
    }

    private String currentInsetLabelKey() {
        ShapePreset s = this.currentShape();
        return (s != null && s.inset) ? "cnpcplus.dropfx.inset" : "cnpcplus.dropfx.outset";
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String fmt(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) return String.valueOf((long) v);
        return String.format(Locale.ROOT, "%.2f", v);
    }

    @Override
    public void save() {
        if (this.cancelled || this.parent == null) return;
        List<DropFxConfig> result = new ArrayList<>();
        for (DropFxConfig c : this.editCopy) {
            if (c.shapes.isEmpty()) continue;
            result.add(c.copy());
        }
        this.parent.applyFxCopy(result);
    }

    // ===== 渲染：三块独立灰板 + 局部变黑列表区 + 组件 + 联动 =====

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int gl = this.guiLeft;
        int gt = this.guiTop;

        // 三块独立原版灰板（左最大 / 右上形状 / 右下参数+按钮，233d-UI4 右下加高容纳按钮行）
        panel(g, gl + 2, gt + 2, 200, 204);
        panel(g, gl + 208, gt + 2, 210, 100);
        panel(g, gl + 208, gt + 106, 210, 142);

        // 列表内容区「局部变黑」（滚动条由 vanilla 列表画在暗底右缘）
        darken(g, this.particleList.left(), this.particleList.top(),
                this.particleList.right(), this.particleList.bottom());
        darken(g, this.shapeList.left(), this.shapeList.top(),
                this.shapeList.right(), this.shapeList.bottom());
        darken(g, this.paramList.left(), this.paramList.top(),
                this.paramList.right(), this.paramList.bottom());

        // 参数区滚动联动（super 画 widget 前同步位置）
        this.syncParamWidgets();

        super.render(g, mx, my, pt);

        var f = Minecraft.getInstance().font;
        // 块标题（灰板上深字）
        g.drawString(f, Component.translatable("cnpcplus.dropfx.title"), gl + 6, gt + 6, 0x404040, false);
        g.drawString(f, Component.translatable("cnpcplus.dropfx.shapes"), gl + 212, gt + 6, 0x404040, false);
        g.drawString(f, Component.translatable("cnpcplus.dropfx.params"), gl + 212, gt + 110, 0x404040, false);
        // 当前粒子（左块底部）
        DropFxConfig cfg = this.currentConfig();
        String who = cfg != null ? cfg.particleId
                : this.selectedParticleId != null ? this.selectedParticleId : "-";
        g.drawString(f, who, gl + 6, gt + 194, 0x404040, false);
    }

    /** 原版容器灰板：黑描边+灰底+左上白亮边+右下暗边。 */
    private void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 4, y - 4, x + w + 4, y + h + 4, 0xFF000000);
        g.fill(x, y, x + w, y + h, BG_FILL);
        g.fill(x - 3, y - 3, x + w + 3, y - 1, BG_LIGHT);
        g.fill(x - 3, y - 3, x - 1, y + h + 3, BG_LIGHT);
        g.fill(x - 1, y + h + 1, x + w + 3, y + h + 3, BG_DARK);
        g.fill(x + w + 1, y - 3, x + w + 3, y + h + 3, BG_DARK);
    }

    /** 列表内容区暗底（局部变黑）；坐标经内部类访问器传入（protected 跨类限制）。 */
    private void darken(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, LIST_DARK);
    }

    // ===== 左块：粒子注册表 =====

    private final class ParticleList extends ObjectSelectionList<ParticleList.Entry> {

        ParticleList() {
            // width=196：place(gl+6) 后 x1=gl+202=左面板内缘，暗底无缝（防像第二条滚动条）
            super(Minecraft.getInstance(), 196, 172, 0, 172, 18);
        }

        void place(int x, int y) {
            this.x0 = x;
            this.x1 = x + this.width;
            this.y0 = y;
            this.y1 = y + this.height;
        }

        /**
         * 233d-UI5：vanilla {@code getScrollbarPosition()=width/2+124} 是
         * 「全屏居中列表」的绝对坐标（不含 x0）——place 到任意位置后滚动条
         * 画错位；且 {@code getEntryAtPosition} 要求 mouseX&lt;该值才命中
         * entry（形状/参数列表整体在其右侧→点击全 miss=「添加预设无反应」根因）。
         * override 为列表右缘 x1-6：渲染/拖动判定/entry 命中三处同时修复。
         */
        @Override
        protected int getScrollbarPosition() {
            return this.x1 - 6;
        }

        int left() { return this.x0; }
        int top() { return this.y0; }
        int right() { return this.x1; }
        int bottom() { return this.y1; }

        @Override
        public int getRowWidth() {
            return this.width - 12;
        }

        void rebuild() {
            this.clearEntries();
            for (String id : SubGuiDropFx.this.particleIds) {
                this.addEntry(new Entry(id));
            }
        }

        final class Entry extends ObjectSelectionList.Entry<Entry> {
            final String id;
            final boolean usable;
            final boolean configured;
            final boolean selected;

            Entry(String id) {
                this.id = id;
                ParticleType<?> t = ForgeRegistries.PARTICLE_TYPES.getValue(
                        new ResourceLocation(id));
                this.usable = t instanceof ParticleOptions;
                this.configured = SubGuiDropFx.this.particleConfigured(id);
                this.selected = id.equals(SubGuiDropFx.this.selectedParticleId);
            }

            @Override
            public Component getNarration() {
                return Component.literal(this.id);
            }

            @Override
            public void render(GuiGraphics g, int index, int top, int left, int width,
                               int height, int mx, int my, boolean hovering, float pt) {
                var font = Minecraft.getInstance().font;
                int textColor = this.usable ? (this.selected ? 0x55FFFF : 0xE0E0E0) : 0x808080;
                int ix = left + 2;
                int iy = top + (height - 8) / 2;
                // 图标三级链：静态表（vanilla 快速）→ 动态 atlas（mod 零配置）→ '?'
                ParticleIconLookup.Icon icon = ParticleIconLookup.of(this.id);
                if (icon != null) {
                    g.blit(icon.texture(), ix, iy, ParticleIconLookup.Icon.DRAW,
                            ParticleIconLookup.Icon.DRAW, 0f, 0f,
                            icon.srcW(), icon.srcH(), icon.texW(), icon.texH());
                } else if (!DynamicParticleIcon.tryBlit(g, ix, iy, this.id)) {
                    g.drawString(font, "?", left + 4, iy, 0xFFFF55, false);
                }
                String text = this.id;
                int maxChars = (width - 16) / 5;
                if (text.length() > maxChars) text = text.substring(0, Math.max(1, maxChars)) + "…";
                g.drawString(font, text, left + 14, iy, textColor, false);
                int border = this.configured ? 0xFFFFAA00 : (this.selected ? 0xFF00AAAA : 0);
                if (border != 0) {
                    g.fill(left, top, left + width, top + 1, border);
                    g.fill(left, top + height - 1, left + width, top + height, border);
                    g.fill(left, top, left + 1, top + height, border);
                    g.fill(left + width - 1, top, left + width, top + height, border);
                }
            }

            @Override
            public boolean mouseClicked(double x, double y, int button) {
                if (this.usable) {
                    SubGuiDropFx.this.selectParticle(this.id);
                    return true;
                }
                return false;
            }
        }
    }

    // ===== 右上块：形状预设 =====

    private final class ShapeList extends ObjectSelectionList<ShapeList.Entry> {

        ShapeList() {
            // width=206：place(gl+212) 后 x1=gl+418=右上板内缘，暗底无缝
            super(Minecraft.getInstance(), 206, 72, 0, 72, 16);
        }

        void place(int x, int y) {
            this.x0 = x;
            this.x1 = x + this.width;
            this.y0 = y;
            this.y1 = y + this.height;
        }

        /** 见 ParticleList 同名方法（滚动条右缘+entry 命中修复，233d-UI5）。 */
        @Override
        protected int getScrollbarPosition() {
            return this.x1 - 6;
        }

        int left() { return this.x0; }
        int top() { return this.y0; }
        int right() { return this.x1; }
        int bottom() { return this.y1; }

        @Override
        public int getRowWidth() {
            return this.width - 12;
        }

        void rebuild() {
            this.clearEntries();
            DropFxConfig cfg = SubGuiDropFx.this.currentConfig();
            int n = cfg == null ? 0 : cfg.shapes.size();
            for (int i = 0; i < n; i++) {
                this.addEntry(new Entry(i, cfg.shapes.get(i).type, false));
            }
            this.addEntry(new Entry(n, null, true));
        }

        final class Entry extends ObjectSelectionList.Entry<Entry> {
            final int index;
            final ShapeType type;
            final boolean addRow;
            final boolean selected;

            Entry(int index, ShapeType type, boolean addRow) {
                this.index = index;
                this.type = type;
                this.addRow = addRow;
                this.selected = !addRow && index == SubGuiDropFx.this.selectedShapeIndex;
            }

            @Override
            public Component getNarration() {
                return Component.literal(this.addRow ? "+" : (this.type == null ? "?" : this.type.name()));
            }

            @Override
            public void render(GuiGraphics g, int index, int top, int left, int width,
                               int height, int mx, int my, boolean hovering, float pt) {
                var font = Minecraft.getInstance().font;
                String text = this.addRow
                        ? Component.translatable("cnpcplus.dropfx.addshape").getString()
                        : (this.index + 1) + ". " + (this.type == null ? "?" : this.type.name());
                int textColor = this.addRow ? 0x55FF55 : 0xE0E0E0;
                g.drawString(font, text, left + 4, top + (height - 8) / 2, textColor, false);
                if (this.selected) {
                    g.fill(left, top, left + width, top + 1, 0xFF00AAAA);
                    g.fill(left, top + height - 1, left + width, top + height, 0xFF00AAAA);
                    g.fill(left, top, left + 1, top + height, 0xFF00AAAA);
                    g.fill(left + width - 1, top, left + width, top + height, 0xFF00AAAA);
                }
            }

            @Override
            public boolean mouseClicked(double x, double y, int button) {
                if (SubGuiDropFx.this.selectedParticleId == null) return false;
                if (this.addRow) {
                    SubGuiDropFx.this.addShape();
                } else {
                    SubGuiDropFx.this.selectShape(this.index);
                }
                return true;
            }
        }
    }

    // ===== 右下块：参数（滚动骨架，标签自绘，EditBox 叠加跟随） =====

    private final class ParamList extends ObjectSelectionList<ParamList.Entry> {

        ParamList() {
            // 内容 10 行×18=180 > 可视 92 → 溢出 88px，滚动条明显（233d-UI4 每参数一行）
            super(Minecraft.getInstance(), 206, 92, 0, 92, PARAM_ROW);
        }

        void place(int x, int y) {
            this.x0 = x;
            this.x1 = x + this.width;
            this.y0 = y;
            this.y1 = y + this.height;
        }

        /** 见 ParticleList 同名方法（滚动条右缘+entry 命中修复，233d-UI5）。 */
        @Override
        protected int getScrollbarPosition() {
            return this.x1 - 6;
        }

        int left() { return this.x0; }
        int top() { return this.y0; }
        int right() { return this.x1; }
        int bottom() { return this.y1; }

        @Override
        public int getRowWidth() {
            return this.width; // 不截断，标签坐标硬编码
        }

        void rebuild() {
            this.clearEntries();
            for (int i = 0; i < PARAM_ROWS; i++) {
                this.addEntry(new Entry(i));
            }
        }

        /** 一行=一个参数标签（EditBox 是外部 widget，按行位跟随滚动；233d-UI4 每参数独立一行）。 */
        final class Entry extends ObjectSelectionList.Entry<Entry> {
            final int row;

            Entry(int row) {
                this.row = row;
            }

            @Override
            public Component getNarration() {
                return Component.literal("");
            }

            @Override
            public void render(GuiGraphics g, int index, int top, int left, int width,
                               int height, int mx, int my, boolean hovering, float pt) {
                var font = Minecraft.getInstance().font;
                int c = 0xE0E0E0;
                int ly = top + (height - 8) / 2;
                switch (this.row) {
                    case 0 -> g.drawString(font, Component.translatable("cnpcplus.dropfx.radius"), left + 2, ly, c, false);
                    case 1 -> g.drawString(font, Component.translatable("cnpcplus.dropfx.rotspeed"), left + 2, ly, c, false);
                    case 2 -> g.drawString(font, Component.translatable("cnpcplus.dropfx.rotation"), left + 2, ly, c, false);
                    case 3 -> g.drawString(font, Component.translatable("cnpcplus.dropfx.insetamt"), left + 2, ly, c, false);
                    case 4 -> g.drawString(font, Component.translatable("cnpcplus.dropfx.duration"), left + 2, ly, c, false);
                    case 5 -> g.drawString(font, Component.translatable("cnpcplus.dropfx.density"), left + 2, ly, c, false);
                    case 6 -> g.drawString(font, Component.translatable("cnpcplus.dropfx.ox"), left + 2, ly, c, false);
                    case 7 -> g.drawString(font, Component.translatable("cnpcplus.dropfx.oy"), left + 2, ly, c, false);
                    case 8 -> g.drawString(font, Component.translatable("cnpcplus.dropfx.oz"), left + 2, ly, c, false);
                    default -> {
                        ShapePreset s = SubGuiDropFx.this.currentShape();
                        g.drawString(font, Component.translatable("cnpcplus.dropfx.type").getString() + ": "
                                + (s == null ? "-" : s.type.name()), left + 2, ly, c, false);
                    }
                }
            }
        }
    }

    /** 选中/增删变化后重建列表条目。 */
    private void rebuildLists() {
        this.particleList.rebuild();
        this.shapeList.rebuild();
        if (this.paramList != null) this.paramList.rebuild();
    }
}
