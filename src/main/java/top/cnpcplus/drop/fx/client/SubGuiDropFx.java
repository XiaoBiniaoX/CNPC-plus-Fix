package top.cnpcplus.drop.fx.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.registries.ForgeRegistries;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;
import top.cnpcplus.drop.client.GuiDropRecipes;
import top.cnpcplus.drop.fx.DropFxConfig;
import top.cnpcplus.drop.fx.DropFxCaps;
import top.cnpcplus.drop.fx.ShapePreset;
import top.cnpcplus.drop.fx.ShapeType;
import top.cnpcplus.drop.fx.runtime.DropFxSampler;
import top.cnpcplus.drop.fx.runtime.FxComputePool;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
 * <h3>233d-UI6 画廊重做（第七轮反馈「预设面板=内置画廊」）</h3>
 * 用户拍板：形状预设面板<b>不是</b>「用户手动添加的列表」，而是一整套
 * 25 种 ShapeType 的<b>内置可视化画廊</b>——2 行网格、每格=图案预览+
 * 短文本名、底部横向滚动条可拖可滚。旧 ShapeList（已添加行+「＋添加预设」）
 * 因此删除，替换为自定义 {@link ShapeGallery}：
 * <ul>
 *   <li>单击格子=添加该形状新预设并选中编辑（重复点击=多个同类实例）；</li>
 *   <li>参数区行9 前后按钮 &lt;/&gt; 循环切换已添加 shapes（编辑旧预设入口）；</li>
 *   <li>边框态：选中=青 / 该类型已添加=橙 / hover=白 / 默认=灰；</li>
 *   <li>图案：固定种子 {@code RandomSource.create(0)} 每类型采样 24 点
 *       预计算（轮廓类折线+闭合，填充类只画点），映射到格子 16px 区域。</li>
 * </ul>
 *
 * <h3>保存链</h3>
 * save() 写回父内存字段；落盘靠父「保存」按钮；取消/ESC=cancelled 不写。
 */
public class SubGuiDropFx extends GuiBasic implements ITextfieldListener {

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

    /**
     * 画廊图案预计算点集（233d-UI6）：每 ShapeType 固定种子采样 24 点
     * 单位坐标（x,y ∈ [-1,1]），类加载时一次算好，渲染每帧直接读。
     * 填充类（DISC/RANDOM/SQUARE_FILL/GRID）只画点不连线。
     */
    private static final double[][][] SHAPE_PREVIEW;

    static {
        ShapeType[] all = ShapeType.values();
        SHAPE_PREVIEW = new double[all.length][][];
        for (int i = 0; i < all.length; i++) {
            List<double[]> out = new ArrayList<>();
            // 固定种子=每个形状图案确定（每次打开 GUI 相同），RandomSource.create(0)
            all[i].sample(24, RandomSource.create(0), out);
            SHAPE_PREVIEW[i] = out.toArray(new double[0][]);
        }
    }

    private final GuiDropRecipes parent;
    private final List<DropFxConfig> editCopy = new ArrayList<>();
    private final List<String> particleIds = new ArrayList<>();

    private String selectedParticleId = null;
    private int selectedShapeIndex = -1;
    private boolean cancelled = false;

    private ParticleList particleList;
    private ShapeGallery shapeGallery;
    private ParamList paramList;
    private Button insetButton;
    /** 行9 前后切换已添加 shapes（233d-UI6 画廊替代 ShapeList 后的「编辑旧预设」入口）。 */
    private Button prevShapeBtn;
    private Button nextShapeBtn;

    public SubGuiDropFx(GuiDropRecipes parent, List<DropFxConfig> currentFxs) {
        this.parent = parent;
        for (DropFxConfig c : currentFxs) this.editCopy.add(c.copy());
        // 233d-UI7 包围盒 400×256（原 420 在窄窗口/GUI scale=2 时右侧删除按钮被裁=「超出」根因）
        this.imageWidth = 400;
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

        // 左块（最大）：粒子注册表（width=192 贴面板内缘，消 4px 灰缝防像第二条滚动条）
        if (this.particleList == null) {
            this.particleList = new ParticleList();
            killVanillaDirt(this.particleList);
        }
        this.particleList.place(gl + 6, gt + 18);
        this.addRenderableWidget(this.particleList);

        // 右上块：形状画廊（233d-UI6 替代旧 ShapeList——内置 25 种预设网格）
        if (this.shapeGallery == null) {
            this.shapeGallery = new ShapeGallery();
        }
        this.shapeGallery.place(gl + 206, gt + 16);
        this.addRenderableWidget(this.shapeGallery);

        // 右下块：参数（10 行每参数一行滚动；EditBox 叠加跟随滚动）
        if (this.paramList == null) {
            this.paramList = new ParamList();
            killVanillaDirt(this.paramList);
        }
        this.paramList.place(gl + 206, gt + 120);
        this.addRenderableWidget(this.paramList);

        // 参数 EditBox（每行一个 x 对齐列表左缘+100；y 每帧 syncParamWidgets 跟随滚动）
        // 233d-UI7：换 CNPC GuiTextFieldNop + addTextField——vanilla EditBox 的
        // charTyped 被 GuiWrapper.charTyped 恒 true 吞掉（无注册 textfield 时
        // 也 return true），导致 super.charTyped 永不调用、打字永远进不了框。
        // GuiTextFieldNop 走 wrapper.textfields 路由（mouseClicked/charTyped/
        // keyPressed/render 全由 CNPC 分发），本类实现 ITextfieldListener.unFocused
        // 在失焦时把值写回 ShapePreset 并 prefetch。
        this.addTextField(field(FIELD_RADIUS, 0, 0, 70));
        this.addTextField(field(FIELD_ROTSPD, 0, 0, 70));
        this.addTextField(field(FIELD_ROT, 0, 0, 70));
        this.addTextField(field(FIELD_INSET_AMT, 0, 0, 70));
        this.addTextField(field(FIELD_DURATION, 0, 0, 70));
        this.addTextField(field(FIELD_DENSITY, 0, 0, 70));
        this.addTextField(field(FIELD_OX, 0, 0, 70));
        this.addTextField(field(FIELD_OY, 0, 0, 70));
        this.addTextField(field(FIELD_OZ, 0, 0, 70));

        // 内陷/外扩切换钮（参数行9 右侧，跟随滚动）
        this.insetButton = Button.builder(
                        Component.translatable(this.currentInsetLabelKey()), b -> {
                            ShapePreset s = this.currentShape();
                            if (s == null) return;
                            s.inset = !s.inset;
                            b.setMessage(Component.translatable(this.currentInsetLabelKey()));
                            this.prefetchCurrent();
                        })
                .bounds(0, 0, 54, 18).build();
        this.addRenderableWidget(this.insetButton);
        // 行9 前后切换（画廊替代 ShapeList 后编辑旧预设的入口；< 循环上一个/> 循环下一个）
        this.prevShapeBtn = Button.builder(Component.literal("<"), b -> this.cycleShape(-1))
                .bounds(0, 0, 12, 18).build();
        this.nextShapeBtn = Button.builder(Component.literal(">"), b -> this.cycleShape(1))
                .bounds(0, 0, 12, 18).build();
        this.addRenderableWidget(this.prevShapeBtn);
        this.addRenderableWidget(this.nextShapeBtn);

        // 底部按钮行（右下参数板内底部；233d-UI7 宽度 400 后压缩间距）
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                        b -> this.close())
                .bounds(gl + 206, gt + 218, 40, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),
                        b -> { this.cancelled = true; this.close(); })
                .bounds(gl + 250, gt + 218, 40, 20).build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("cnpcplus.dropfx.delshape"), b -> this.deleteShape())
                .bounds(gl + 294, gt + 218, 48, 20).build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("cnpcplus.dropfx.delparticle"), b -> this.deleteParticle())
                .bounds(gl + 346, gt + 218, 52, 20).build());

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

    /**
     * 创建参数输入框（233d-UI7 换 CNPC GuiTextFieldNop）：
     * floatsOnly=true 允许输入负号/小数点（radius 等参数支持负值）；
     * setMinMaxDefault 在 unFocused 时夹值并重置非法输入；
     * listener 由构造器自动绑定（parent=本类 implements ITextfieldListener）。
     */
    private GuiTextFieldNop field(int id, int x, int y, int width) {
        GuiTextFieldNop box = new GuiTextFieldNop(id, this, x, y, width, 16, "");
        box.floatsOnly = true;
        box.setMinMaxDefault(-10f, 10f, 0f);
        if (id == FIELD_DURATION) {
            // 时长 1..1200 ticks（DropFxCaps.MAX_DURATION_TICKS）
            box.setMinMaxDefault(1f, DropFxCaps.MAX_DURATION_TICKS, 20f);
        } else if (id == FIELD_DENSITY) {
            // 密度不允许 0（除数），夹 0.01..10
            box.setMinMaxDefault(0.01f, 10f, 1f);
        }
        return box;
    }

    /**
     * 参数区滚动联动（233d-UI7 换 GuiTextFieldNop 后改用 enabled 字段）：
     * GuiBasic.render 直调 {@code tf.m_87963_}(renderWidget) 绕过 AbstractWidget.visible
     * 检查——滚出可视区/无选中形状时只能置 {@code enabled=false}（GuiTextFieldNop
     * 的 renderWidget 首行判断 enabled，false=整框跳过渲染且鼠标不可点）。
     * 每帧 render 前调用。
     */
    private void syncParamWidgets() {
        if (this.paramList == null) return;
        double sc = this.paramList.getScrollAmount();
        int base = this.paramList.top() + 4;
        int top = this.paramList.top();
        int bot = this.paramList.bottom();
        int lx = this.paramList.left();
        // 行0..8：每行一个输入框（标签在列表 Entry 自绘于左，框叠在右）
        placeXY(FIELD_RADIUS, lx + 100, base + 0 * PARAM_ROW + 1, sc, top, bot);
        placeXY(FIELD_ROTSPD, lx + 100, base + 1 * PARAM_ROW + 1, sc, top, bot);
        placeXY(FIELD_ROT, lx + 100, base + 2 * PARAM_ROW + 1, sc, top, bot);
        placeXY(FIELD_INSET_AMT, lx + 100, base + 3 * PARAM_ROW + 1, sc, top, bot);
        placeXY(FIELD_DURATION, lx + 100, base + 4 * PARAM_ROW + 1, sc, top, bot);
        placeXY(FIELD_DENSITY, lx + 100, base + 5 * PARAM_ROW + 1, sc, top, bot);
        placeXY(FIELD_OX, lx + 100, base + 6 * PARAM_ROW + 1, sc, top, bot);
        placeXY(FIELD_OY, lx + 100, base + 7 * PARAM_ROW + 1, sc, top, bot);
        placeXY(FIELD_OZ, lx + 100, base + 8 * PARAM_ROW + 1, sc, top, bot);
        // 行9：类型行右侧 < > 切换钮 + inset 钮（233d-UI6 画廊后编辑旧预设入口）
        int y9 = base + 9 * PARAM_ROW + 1 - (int) sc;
        boolean vis9 = y9 >= top && y9 + 18 <= bot;
        if (this.prevShapeBtn != null) {
            this.prevShapeBtn.setX(lx + 118);
            this.prevShapeBtn.setY(y9);
            this.prevShapeBtn.visible = vis9;
        }
        if (this.nextShapeBtn != null) {
            this.nextShapeBtn.setX(lx + 132);
            this.nextShapeBtn.setY(y9);
            this.nextShapeBtn.visible = vis9;
        }
        if (this.insetButton != null) {
            this.insetButton.setX(lx + 150);
            this.insetButton.setY(y9);
            this.insetButton.visible = vis9;
        }
    }

    private void placeXY(int fieldId, int x, int yUnscrolled, double sc, int top, int bot) {
        GuiTextFieldNop b = this.getTextField(fieldId);
        if (b == null) return;
        int y = yUnscrolled - (int) sc;
        b.setX(x);
        b.setY(y);
        // enabled=false 同时隐藏+禁用（GuiTextFieldNop.renderWidget 首行判 enabled）
        b.enabled = y >= top && y + 16 <= bot && this.currentShape() != null;
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
        this.addShape(ShapeType.CIRCLE); // 旧无参入口=默认圆形（现主入口为画廊单击）
    }

    /**
     * 233d-UI6 画廊入口：添加指定类型的新预设并立即选中编辑。
     * 未选粒子时静默返回；已达上限也不加（caps 32）。
     */
    private void addShape(ShapeType type) {
        DropFxConfig c = this.currentConfig();
        if (c == null) {
            if (this.selectedParticleId == null) return;
            c = new DropFxConfig(this.selectedParticleId);
            this.editCopy.add(c);
        }
        if (c.shapes.size() >= DropFxCaps.MAX_SHAPES_PER_CONFIG) return;
        ShapePreset p = new ShapePreset();
        p.type = type;
        c.shapes.add(p);
        this.selectShape(c.shapes.size() - 1);
    }

    /** 行9 前后切换（循环切换已添加 shapes 的选中索引；dir=±1）。 */
    private void cycleShape(int dir) {
        DropFxConfig c = this.currentConfig();
        if (c == null || c.shapes.isEmpty()) return;
        int n = c.shapes.size();
        int idx = ((this.selectedShapeIndex % n) + dir + n) % n;
        this.selectShape(idx);
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
        // GuiTextFieldNop.m_94144_(setValue) 不触发 listener——安全刷新
        set(FIELD_RADIUS, s == null ? "" : fmt(s.radius));
        set(FIELD_ROTSPD, s == null ? "" : fmt(s.rotSpeed));
        set(FIELD_ROT, s == null ? "" : fmt(s.rotation));
        set(FIELD_INSET_AMT, s == null ? "" : fmt(s.insetAmount));
        set(FIELD_DURATION, s == null ? "" : String.valueOf(s.durationTicks));
        set(FIELD_DENSITY, s == null ? "" : fmt(s.density));
        set(FIELD_OX, s == null ? "" : fmt(s.offsetX));
        set(FIELD_OY, s == null ? "" : fmt(s.offsetY));
        set(FIELD_OZ, s == null ? "" : fmt(s.offsetZ));
    }

    private void set(int id, String v) {
        GuiTextFieldNop b = this.getTextField(id);
        if (b != null) b.setValue(v);
    }

    private void updateFieldEnabled() {
        // 无选中形状时全部 disabled（placeXY 每帧还会再校验 enabled）
        boolean on = this.currentShape() != null;
        for (int id : new int[]{FIELD_RADIUS, FIELD_ROTSPD, FIELD_ROT,
                FIELD_INSET_AMT, FIELD_DURATION, FIELD_DENSITY, FIELD_OX, FIELD_OY, FIELD_OZ}) {
            GuiTextFieldNop b = this.getTextField(id);
            if (b != null) b.enabled = on;
        }
    }

    /**
     * 233d-UI7：GuiTextFieldNop 失焦回调（CNPC ITextfieldListener）。
     * unFocused() 已按 floatsOnly+setMinMaxDefault 夹好值，这里只需写回 ShapePreset
     * 并触发 prefetch 预采样。替代旧 EditBox.responder 的即时 apply——
     * 失焦提交语义更清晰（输入过程中不反复重采样）。
     */
    @Override
    public void unFocused(GuiTextFieldNop textfield) {
        if (textfield == null) return;
        // textfield.id 即 FIELD_* 常量；值已被 unFocused() 按 minMax 夹过
        this.applyField(textfield.id, textfield.getValue());
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

        // 三块独立原版灰板（233d-UI7 宽度 420→400：左 196 / 右 196，窄窗口不再裁按钮）
        panel(g, gl + 2, gt + 2, 196, 204);
        panel(g, gl + 202, gt + 2, 196, 100);
        panel(g, gl + 202, gt + 106, 196, 142);

        // 列表内容区「局部变黑」（滚动条由 vanilla 列表画在暗底右缘；画廊自绘底+边框）
        darken(g, this.particleList.left(), this.particleList.top(),
                this.particleList.right(), this.particleList.bottom());
        darken(g, this.shapeGallery.getX(), this.shapeGallery.getY(),
                this.shapeGallery.getX() + this.shapeGallery.getWidth(),
                this.shapeGallery.getY() + this.shapeGallery.getHeight());
        darken(g, this.paramList.left(), this.paramList.top(),
                this.paramList.right(), this.paramList.bottom());

        // 参数区滚动联动（super 画 widget 前同步位置）
        this.syncParamWidgets();

        super.render(g, mx, my, pt);

        var f = Minecraft.getInstance().font;
        // 块标题（灰板上深字；233d-UI7 右板 x=gl+206）
        g.drawString(f, Component.translatable("cnpcplus.dropfx.title"), gl + 6, gt + 6, 0x404040, false);
        g.drawString(f, Component.translatable("cnpcplus.dropfx.shapes"), gl + 206, gt + 6, 0x404040, false);
        g.drawString(f, Component.translatable("cnpcplus.dropfx.params"), gl + 206, gt + 110, 0x404040, false);
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
            // width=192：place(gl+6) 后 x1=gl+198=左面板内缘（233d-UI7 宽 196→192），暗底无缝
            super(Minecraft.getInstance(), 192, 172, 0, 172, 18);
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

    // ===== 右上块：形状画廊（233d-UI6：内置 25 种预设网格+横向滚动） =====

    /**
     * 形状预设画廊：2 行 × N 列网格，每格=图案预览+短文本名；底部横向
     * 滚动条（滚轮/拖动/点轨道翻页）。替代旧 ShapeList「用户添加列表」。
     * 单击格子=添加该 type 新预设并选中编辑；已添加类型格子橙框。
     */
    private final class ShapeGallery extends AbstractWidget {
        /** 格子边长。 */
        private static final int CELL = 32;
        /** 格子水平/垂直间距。 */
        private static final int GAP = 1;
        /** 列宽=CELL+GAP。 */
        private static final int COL_W = CELL + GAP;
        /** 网格行数（固定 2）。 */
        private static final int ROWS = 2;
        /** 底部滚动条高度。 */
        private static final int BAR_H = 6;
        /** 可视内容区（不含滚动条）宽=面板内缘宽（233d-UI7 宽 200→192）。 */
        private final int viewW = 192;
        /** 可视内容区高=2 行格子+间距。 */
        private final int viewH = ROWS * CELL + (ROWS - 1) * GAP;
        /** 横向滚动偏移（像素）。 */
        private double scrollX;
        /** 拖动滚动条时的滑块抓取偏移，-1=未拖动。 */
        private int dragThumbX = -1;

        ShapeGallery() {
            // 总高=内容(2*32+1=65)+滚动条 6+1 间距=72（super 不能引用实例字段 viewH，常量直接算）
            // 233d-UI7 宽 206→192
            super(0, 0, 192, ROWS * CELL + (ROWS - 1) * GAP + BAR_H + 1, Component.empty());
        }

        @Override
        public void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput out) {
            // 网格画廊无可朗读条目；空实现满足 AbstractWidget 抽象方法
        }

        void place(int x, int y) {
            this.setX(x);
            this.setY(y);
        }

        /** 总列数=ceil(总形状数/行数)（25/2→13 列）。 */
        private int cols() {
            return (SHAPE_PREVIEW.length + ROWS - 1) / ROWS;
        }

        /** 内容总宽=列数×列宽（视口外部分靠滚动显示）。 */
        private int contentW() {
            return cols() * COL_W;
        }

        /** 最大滚动量=内容宽-视口宽。 */
        private int maxScroll() {
            return Math.max(0, contentW() - viewW);
        }

        private void clampScroll() {
            this.scrollX = Math.max(0, Math.min(this.scrollX, maxScroll()));
        }

        /** 滚动条滑块宽（等比，最小 20 防太小）。 */
        private int thumbW() {
            return Math.max(20, viewW * viewW / Math.max(1, contentW()));
        }

        /** 滚动条滑块 x 偏移（相对可视区左缘）。 */
        private int thumbX() {
            int t = maxScroll();
            return t <= 0 ? 0 : (int) ((viewW - thumbW()) * this.scrollX / t);
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double delta) {
            if (!this.active || !this.visible) return false;
            this.scrollX -= delta * COL_W; // 滚轮=横向滑动一格
            clampScroll();
            return true; // 消费掉，防父界面处理
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!this.active || !this.visible || button != 0) return false;
            int sx = this.getX(), sy = this.getY();
            int barY = sy + viewH + 1;
            // 滚动条区域：抓住滑块 / 点轨道翻页
            if (my >= barY && my < barY + BAR_H && maxScroll() > 0) {
                this.dragThumbX = thumbX();
                if (mx >= sx + dragThumbX && mx < sx + dragThumbX + thumbW()) {
                    return true; // 抓住滑块（后续 mouseDragged 更新）
                }
                this.scrollX += (mx < sx + dragThumbX ? -viewW : viewW); // 点轨道=翻页
                clampScroll();
                return true;
            }
            if (mx < sx || mx >= sx + viewW || my < sy || my >= sy + viewH) return false;
            // 命中格子：col*2+row=线性索引（列优先：上行偶数、下行奇数）
            int col = (int) ((mx - sx + this.scrollX) / COL_W);
            int row = (int) ((my - sy) / (CELL + GAP));
            if (row < 0 || row >= ROWS || col < 0 || col >= cols()) return false;
            int idx = col * ROWS + row;
            if (idx < 0 || idx >= SHAPE_PREVIEW.length) return false;
            ShapeType type = ShapeType.values()[idx];
            // 233d-UI7：该 type 已存在于当前 config → 选中首个同类型实例，
            // 不再总是 addShape（第六轮「1/1→15/15」根因：每点一次加一个）。
            DropFxConfig c = SubGuiDropFx.this.currentConfig();
            if (c != null) {
                for (int i = 0; i < c.shapes.size(); i++) {
                    if (c.shapes.get(i).type == type) {
                        SubGuiDropFx.this.selectShape(i);
                        return true;
                    }
                }
            }
            SubGuiDropFx.this.addShape(type); // 不存在才添加
            return true;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (this.dragThumbX < 0 || maxScroll() <= 0) return false;
            int track = viewW - thumbW();
            double rel = (mx - this.getX() - dragThumbX) / Math.max(1, track);
            this.scrollX = rel * maxScroll();
            clampScroll();
            return true;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            this.dragThumbX = -1;
            return false;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            var font = Minecraft.getInstance().font;
            DropFxConfig cfg = SubGuiDropFx.this.currentConfig();
            ShapeType selType = SubGuiDropFx.this.currentShape() == null
                    ? null : SubGuiDropFx.this.currentShape().type;
            int sx = this.getX(), sy = this.getY();
            for (int i = 0; i < SHAPE_PREVIEW.length; i++) {
                int col = i / ROWS, row = i % ROWS;
                int cx = sx + col * COL_W - (int) this.scrollX;
                if (cx + CELL < sx || cx > sx + viewW) continue; // 视口外剔除
                int cy = sy + row * (CELL + GAP);
                ShapeType type = ShapeType.values()[i];
                // 格子底
                g.fill(cx, cy, cx + CELL, cy + CELL, 0xFF2A2A2A);
                // 边框：选中编辑中=青 / 该类型已添加过=橙 / hover=白 / 默认灰
                boolean added = cfg != null && cfg.shapes.stream().anyMatch(s -> s.type == type);
                int border = (type == selType) ? 0xFF00AAAA
                        : added ? 0xFFFFAA00
                        : (mx >= cx && mx < cx + CELL && my >= cy && my < cy + CELL) ? 0xFFFFFFFF
                        : 0xFF505050;
                g.fill(cx, cy, cx + CELL, cy + 1, border);
                g.fill(cx, cy + CELL - 1, cx + CELL, cy + CELL, border);
                g.fill(cx, cy, cx + 1, cy + CELL, border);
                g.fill(cx + CELL - 1, cy, cx + CELL, cy + CELL, border);
                // 图案预览（预计算点集映射到格子中心区）
                drawShapePreview(g, cx + 16, cy + 11, i);
                // 短文本名（lang 缺失回退枚举名）
                String name = Component.translatable("cnpcplus.dropfx.shape." + type.name()).getString();
                int w = font.width(name);
                g.drawString(font, name, cx + Math.max(1, (CELL - w) / 2), cy + CELL - 9, 0xE0E0E0, false);
            }
            // 底部横向滚动条（内容超视口才显示）
            if (maxScroll() > 0) {
                int barY = sy + viewH + 1;
                g.fill(sx, barY, sx + viewW, barY + BAR_H - 1, 0x80000000);
                g.fill(sx + thumbX(), barY, sx + thumbX() + thumbW(), barY + BAR_H - 1, 0xFFAAAAAA);
            }
        }

        /**
         * 把预计算单位点集画成小图案：填充类只画点；轮廓类连折线（闭合类
         * 首尾再补一段）。cx/cy=图案中心，映射比例 8px（单位坐标±1→±8px）。
         */
        private void drawShapePreview(GuiGraphics g, int cx, int cy, int shapeIndex) {
            double[][] pts = SHAPE_PREVIEW[shapeIndex];
            if (pts == null || pts.length == 0) return;
            ShapeType type = ShapeType.values()[shapeIndex];
            int color = 0xFFE8E8E8;
            // 实心/散布类连线会交叉乱：固定种子下只画点也直观
            boolean dotsOnly = type == ShapeType.DISC || type == ShapeType.RANDOM
                    || type == ShapeType.SQUARE_FILL || type == ShapeType.GRID;
            boolean closed = isClosedShape(type);
            // 先画点（保证任何形状都有可见点阵）
            for (int i = 0; i < pts.length; i++) {
                int px = cx + (int) Math.round(pts[i][0] * 8);
                int py = cy + (int) Math.round(pts[i][1] * 8);
                g.fill(px - 1, py - 1, px + 1, py + 1, color);
            }
            if (!dotsOnly) {
                int segs = closed ? pts.length : pts.length - 1;
                for (int i = 0; i < segs; i++) {
                    double[] a = pts[i], b = pts[(i + 1) % pts.length];
                    int x0 = cx + (int) Math.round(a[0] * 8), y0 = cy + (int) Math.round(a[1] * 8);
                    int x1 = cx + (int) Math.round(b[0] * 8), y1 = cy + (int) Math.round(b[1] * 8);
                    int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
                    for (int s = 0; s <= steps; s++) {
                        double f = steps == 0 ? 0 : (double) s / steps;
                        int px = (int) Math.round(x0 + (x1 - x0) * f);
                        int py = (int) Math.round(y0 + (y1 - y0) * f);
                        g.fill(px, py, px + 1, py + 1, color);
                    }
                }
            }
        }
    }

    /** 闭合轮廓类（画廊图案首尾补线判定）。 */
    private static boolean isClosedShape(ShapeType t) {
        return switch (t) {
            case CIRCLE, SQUARE, TRIANGLE, PENTAGON, HEXAGON, PENTA_STAR, HEX_STAR,
                 DIAMOND, RECT_FRAME, ELLIPSE, HEART, RING_DOTS, DIAMOND_STAR -> true;
            default -> false;
        };
    }

    // ===== 右下块：参数（滚动骨架，标签自绘，EditBox 叠加跟随） =====

    private final class ParamList extends ObjectSelectionList<ParamList.Entry> {

        ParamList() {
            // 内容 10 行×18=180 > 可视 92 → 溢出 88px，滚动条明显（233d-UI4 每参数一行）
            // 233d-UI7 宽 206→192（右面板内缘）
            super(Minecraft.getInstance(), 192, 92, 0, 92, PARAM_ROW);
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
                        // 行9：类型名 + 当前索引/总数（233d-UI6 画廊后 < > 切换编辑）
                        ShapePreset s = SubGuiDropFx.this.currentShape();
                        DropFxConfig cfg = SubGuiDropFx.this.currentConfig();
                        int n = cfg == null ? 0 : cfg.shapes.size();
                        String t = (s == null ? "-" : s.type.name())
                                + " " + (n == 0 ? 0 : SubGuiDropFx.this.selectedShapeIndex + 1) + "/" + n;
                        g.drawString(font, t, left + 2, ly, c, false);
                    }
                }
            }
        }
    }

    /** 选中/增删变化后重建列表条目（画廊边框态每帧实时算，无需 rebuild）。 */
    private void rebuildLists() {
        this.particleList.rebuild();
        if (this.paramList != null) this.paramList.rebuild();
    }
}
