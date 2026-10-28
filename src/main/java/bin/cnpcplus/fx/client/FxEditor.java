package bin.cnpcplus.fx.client;

import bin.cnpcplus.common.ISubGuiWheel;
import bin.cnpcplus.fx.FxConfig;
import bin.cnpcplus.fx.FxPresets;
import bin.cnpcplus.fx.FxShape;
import bin.cnpcplus.fx.ShapeType;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.config.GuiUtils;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.client.gui.util.ITextfieldListener;
import noppes.npcs.client.gui.util.SubGuiInterface;
import org.lwjgl.input.Mouse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 粒子编辑器：左列粒子、上方双行形状画廊、下方可滚动参数区。
 *
 * 1.12.2 全部粒子都画在 textures/particle/particles.png 的 8x8 格子里，
 * 索引来自原版 ParticleManager 注册链（见 测试脚本/extract_particle_icons.py）。
 * 少数没有该图集精灵的粒子各自用独立贴图或物品图标显示，不再出现 "?"。
 */
public final class FxEditor extends SubGuiInterface implements ITextfieldListener, ISubGuiWheel {
    private static final ResourceLocation ICONS = new ResourceLocation("minecraft", "textures/particle/particles.png");
    private static final ResourceLocation EXPLOSION = new ResourceLocation("minecraft", "textures/entity/explosion.png");
    private static final ResourceLocation SWEEP = new ResourceLocation("minecraft", "textures/entity/sweep.png");
    private static final ResourceLocation FOOTPRINT = new ResourceLocation("minecraft", "textures/particle/footprint.png");
    /** 粒子列表行高：16px 图标 + 上下各 1px 留白。 */
    private static final int ROW = 18;
    private static final String[] LABELS = {"Radius", "RotSpeed", "Rotation", "RotSpeedX", "RotationX", "RotSpeedZ",
            "RotationZ", "InsetAmt", "Duration", "Density", "Ox", "Oy", "Oz"};
    /** particles.png 精灵索引，逐条取自 1.12.2 字节码里的 setParticleTextureIndex 字面量。 */
    private static final Map<EnumParticleTypes, Integer> SPRITES = new EnumMap<>(EnumParticleTypes.class);
    /** 不走图集的粒子：它们本身就是物品/方块贴图，用对应图标更准确。 */
    private static final Map<EnumParticleTypes, ItemStack> ICON_ITEMS = new EnumMap<>(EnumParticleTypes.class);

    static {
        put(EnumParticleTypes.EXPLOSION_NORMAL, 7);
        put(EnumParticleTypes.SPIT, 7);
        put(EnumParticleTypes.WATER_BUBBLE, 32);
        put(EnumParticleTypes.WATER_SPLASH, 19);
        put(EnumParticleTypes.WATER_WAKE, 19);
        put(EnumParticleTypes.WATER_DROP, 19);
        put(EnumParticleTypes.SUSPENDED, 0);
        put(EnumParticleTypes.SUSPENDED_DEPTH, 0);
        put(EnumParticleTypes.CRIT, 65);
        put(EnumParticleTypes.CRIT_MAGIC, 65);
        put(EnumParticleTypes.SMOKE_NORMAL, 7);
        put(EnumParticleTypes.SMOKE_LARGE, 7);
        put(EnumParticleTypes.SPELL, 128);
        put(EnumParticleTypes.SPELL_INSTANT, 144);
        put(EnumParticleTypes.SPELL_MOB, 128);
        put(EnumParticleTypes.SPELL_MOB_AMBIENT, 128);
        put(EnumParticleTypes.SPELL_WITCH, 144);
        put(EnumParticleTypes.DRIP_WATER, 113);
        put(EnumParticleTypes.DRIP_LAVA, 112);
        put(EnumParticleTypes.VILLAGER_ANGRY, 81);
        put(EnumParticleTypes.VILLAGER_HAPPY, 82);
        put(EnumParticleTypes.TOWN_AURA, 0);
        put(EnumParticleTypes.NOTE, 64);
        put(EnumParticleTypes.ENCHANTMENT_TABLE, 224);
        put(EnumParticleTypes.FLAME, 48);
        put(EnumParticleTypes.LAVA, 49);
        put(EnumParticleTypes.CLOUD, 7);
        put(EnumParticleTypes.FALLING_DUST, 7);
        put(EnumParticleTypes.HEART, 80);
        put(EnumParticleTypes.DRAGON_BREATH, 5);
        put(EnumParticleTypes.END_ROD, 176);
        put(EnumParticleTypes.FIREWORKS_SPARK, 176);
        put(EnumParticleTypes.TOTEM, 176);
        put(EnumParticleTypes.DAMAGE_INDICATOR, 67);

        ICON_ITEMS.put(EnumParticleTypes.PORTAL, new ItemStack(Items.ENDER_PEARL));
        ICON_ITEMS.put(EnumParticleTypes.REDSTONE, new ItemStack(Items.REDSTONE));
        ICON_ITEMS.put(EnumParticleTypes.SNOWBALL, new ItemStack(Items.SNOWBALL));
        ICON_ITEMS.put(EnumParticleTypes.SNOW_SHOVEL, new ItemStack(Items.SNOWBALL));
        ICON_ITEMS.put(EnumParticleTypes.SLIME, new ItemStack(Items.SLIME_BALL));
        ICON_ITEMS.put(EnumParticleTypes.ITEM_CRACK, new ItemStack(Items.IRON_PICKAXE));
        ICON_ITEMS.put(EnumParticleTypes.ITEM_TAKE, new ItemStack(Items.IRON_PICKAXE));
        ICON_ITEMS.put(EnumParticleTypes.BLOCK_CRACK, new ItemStack(Blocks.STONE));
        ICON_ITEMS.put(EnumParticleTypes.BLOCK_DUST, new ItemStack(Blocks.STONE));
        ICON_ITEMS.put(EnumParticleTypes.BARRIER, new ItemStack(Blocks.BARRIER));
        ICON_ITEMS.put(EnumParticleTypes.MOB_APPEARANCE, new ItemStack(Items.ENDER_EYE));
    }

    private static void put(EnumParticleTypes type, int sprite) { SPRITES.put(type, sprite); }

    private final Consumer<List<FxConfig>> sink;
    private final List<FxConfig> edit;
    /** 投掷物编辑器才有：跟随开关的读取/写回；其它入口传 null，面板不出现。 */
    private final BooleanSupplier follow;
    private final Consumer<Boolean> followSink;
    /** 跟随开关的本地状态：和形状一样，点"完成"才写回，取消不影响原设置。 */
    private boolean followValue;
    private final List<EnumParticleTypes> particles = new ArrayList<>();
    private final double[][] previews = new double[ShapeType.values().length][];
    private String particle;
    private int shapeIndex = -1, particleScroll, galleryScroll, paramScroll, drag;
    private int leftWidth, rightX, rightWidth, cellWidth, visibleParticles;

    public FxEditor(Consumer<List<FxConfig>> sink, List<FxConfig> current) {
        this(sink, current, null, null);
    }

    /** 投掷物入口：多一个"粒子跟随投掷物"开关和图案预设。 */
    public FxEditor(Consumer<List<FxConfig>> sink, List<FxConfig> current,
                    BooleanSupplier follow, Consumer<Boolean> followSink) {
        this.sink = sink;
        this.follow = follow;
        this.followSink = followSink;
        this.followValue = follow != null && follow.getAsBoolean();
        this.edit = FxConfig.copy(current);
        this.drawDefaultBackground = false;
        this.closeOnEsc = true;
        for (EnumParticleTypes type : EnumParticleTypes.values()) if (type.getArgumentCount() == 0) particles.add(type);
        particles.sort(Comparator.comparing(FxConfig::id));
        for (ShapeType type : ShapeType.values()) previews[type.ordinal()] = type.sample(32, 0);
        if (!edit.isEmpty()) { particle = edit.get(0).particleId; shapeIndex = 0; }
    }

    @Override
    public void initGui() {
        this.xSize = Math.min(400, this.width - 8);
        this.ySize = Math.min(262, this.height - 16);
        super.initGui();
        this.leftWidth = (this.xSize - 12) / 2;
        this.rightX = this.guiLeft + leftWidth + 10;
        this.rightWidth = this.xSize - leftWidth - 10;
        this.cellWidth = (rightWidth - 8) / 5;
        this.visibleParticles = Math.max(1, listHeight() / ROW);
        if (particle == null && !particles.isEmpty()) particle = FxConfig.id(particles.get(0));
        for (int i = 0; i < LABELS.length; i++) {
            GuiNpcTextField field = new GuiNpcTextField(100 + i, this, this.getFontRenderer(), 0, 0,
                    Math.min(74, rightWidth / 2), 16, "");
            field.setMaxStringLength(16);
            this.addTextField(field);
        }
        int bottom = this.guiTop + this.ySize - 24;
        int buttonWidth = (rightWidth - 10) / 4;
        String[] buttons = {"gui.done", "gui.cancel", "cnpcplus.fx.delshape", "cnpcplus.fx.delparticle"};
        for (int i = 0; i < 4; i++) this.addButton(new GuiNpcButton(20 + i,
                rightX + 2 + i * (buttonWidth + 2), bottom, buttonWidth, 20, buttons[i]));
        this.addButton(new GuiNpcButton(24, rightX + rightWidth - 65, this.guiTop + 108, 18, 16, "<"));
        this.addButton(new GuiNpcButton(25, rightX + rightWidth - 45, this.guiTop + 108, 18, 16, ">"));
        this.addButton(new GuiNpcButton(26, rightX + 4, this.guiTop + this.ySize - 44, rightWidth - 8, 16, ""));
        if (follow != null) {
            // 投掷物专属：跟随开关放在左列底部（不挤压参数区），预设占右列一行。
            this.addButton(new GuiNpcButton(30, this.guiLeft + 4, this.guiTop + this.ySize - 22, leftWidth - 8, 18, ""));
            int presetWidth = (rightWidth - 11) / 4;
            for (int i = 0; i < FxPresets.KEYS.length; i++) this.addButton(new GuiNpcButton(31 + i,
                    rightX + 4 + i * (presetWidth + 1), this.guiTop + this.ySize - 66, presetWidth, 16,
                    FxPresets.langKey(i)));
        }
        clampScrolls();
        refresh();
    }

    private FxConfig config() {
        for (FxConfig config : edit) if (config.particleId.equals(particle)) return config;
        return null;
    }

    private FxShape shape() {
        FxConfig config = config();
        return config != null && shapeIndex >= 0 && shapeIndex < config.shapes.size() ? config.shapes.get(shapeIndex) : null;
    }

    /** 选中粒子后立刻保证有配置和第一个形状，参数区才有内容可改。 */
    private FxConfig configOrCreate() {
        FxConfig config = config();
        if (config != null) return config;
        if (edit.size() >= 16 || particle == null) return null;
        config = new FxConfig(particle);
        edit.add(config);
        return config;
    }

    private void selectParticle(String id) {
        flush();
        particle = id;
        FxConfig config = config();
        shapeIndex = config == null || config.shapes.isEmpty() ? -1 : 0;
        paramScroll = 0;
        refresh();
    }

    private void refresh() {
        FxShape shape = shape();
        for (int i = 0; i < LABELS.length; i++) this.getTextField(100 + i).setText(shape == null ? "" : Double.toString(shape.values[i]));
        this.getButton(26).setDisplayText(shape != null && shape.inset ? "cnpcplus.fx.inset" : "cnpcplus.fx.outset");
        this.getButton(26).enabled = shape != null;
        if (follow != null && this.getButton(30) != null) {
            this.getButton(30).setDisplayText(followValue ? "cnpcplus.fx.follow.on" : "cnpcplus.fx.follow.off");
        }
        clampScrolls();
        positionFields();
    }

    private void clampScrolls() {
        particleScroll = clamp(particleScroll, particles.size() - visibleParticles);
        galleryScroll = clamp(galleryScroll, galleryMax());
        paramScroll = clamp(paramScroll, LABELS.length - paramRows());
    }

    private void positionFields() {
        FxShape shape = shape();
        int width = Math.min(74, rightWidth / 2);
        for (int i = 0; i < LABELS.length; i++) {
            GuiNpcTextField field = this.getTextField(100 + i);
            field.x = rightX + rightWidth - width - 12;
            field.y = this.guiTop + 128 + (i - paramScroll) * 18;
            boolean visible = shape != null && field.y >= this.guiTop + 128 && field.y + 16 <= this.guiTop + this.ySize - 48;
            field.enabled = visible;
            field.canEdit = visible;
            field.setVisible(visible);
        }
    }

    @Override
    public void unFocused(GuiNpcTextField field) {
        FxShape shape = shape();
        if (shape == null) return;
        for (int i = 0; i < LABELS.length; i++) if (this.getTextField(100 + i) == field) {
            try { shape.set(i, Double.parseDouble(field.getText().trim())); }
            catch (NumberFormatException ignored) { }
            field.setText(Double.toString(shape.values[i]));
            return;
        }
    }

    private void flush() {
        for (int i = 0; i < LABELS.length; i++) {
            GuiNpcTextField field = this.getTextField(100 + i);
            if (field != null && field.canEdit) unFocused(field);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        flush();
        FxConfig config = config();
        if (button.id == 20) { sink.accept(FxConfig.copy(edit)); if (followSink != null) followSink.accept(followValue); this.close(); }
        else if (button.id == 21) this.close();
        else if (button.id == 22 && shape() != null) {
            config.shapes.remove(shapeIndex);
            shapeIndex = config.shapes.isEmpty() ? -1 : Math.min(shapeIndex, config.shapes.size() - 1);
            if (config.shapes.isEmpty()) edit.remove(config);
        } else if (button.id == 23 && config != null) { edit.remove(config); shapeIndex = -1; }
        else if ((button.id == 24 || button.id == 25) && config != null && !config.shapes.isEmpty()) {
            shapeIndex = Math.floorMod(shapeIndex + (button.id == 24 ? -1 : 1), config.shapes.size());
        } else if (button.id == 26 && shape() != null) shape().inset = !shape().inset;
        else if (button.id == 30 && follow != null) followValue = !followValue;
        else if (button.id >= 31 && button.id <= 34) applyPreset(button.id - 31);
        refresh();
    }

    /**
     * 预设一键铺好形状（会覆盖当前粒子的形状），并顺手打开跟随——
     * 这几个图案就是为了"跟着投掷物飞"设计的，不打开跟随时只是原地播。
     */
    private void applyPreset(int index) {
        FxConfig config = configOrCreate();
        if (config == null) return;
        config.shapes.clear();
        config.shapes.addAll(FxPresets.build(index));
        shapeIndex = 0;
        followValue = true;
    }

    // ---- 几何：绘制与命中检测共用同一套计算，避免画出去了点不到 ----

    private int listTrackX() { return this.guiLeft + leftWidth - 7; }
    /** 列表从标题下沿 guiTop+18 开始，18px 一行，正好给 16x16 图标留 1px 上下留白。 */
    private int listTop() { return this.guiTop + 18; }
    /** 投掷物模式下底部多一个跟随开关，列表让出 22px。 */
    private int listHeight() { return this.ySize - 23 - (follow != null ? 22 : 0); }
    private int galleryTop() { return this.guiTop + 86; }
    private int galleryHeight() { return 7; }
    private int paramTop() { return this.guiTop + 128; }
    /** 预设行占掉底部一条，参数区相应缩短（滚动条仍然可用）。 */
    private int paramHeight() { return this.ySize - (follow != null ? 70 : 50) - 128; }
    private int galleryMax() { return Math.max(0, (ShapeType.values().length + 1) / 2 - 5); }
    private int paramRows() { return Math.max(1, paramHeight() / 18); }

    private static int thumbSize(int track, int total, int visible) {
        if (total <= visible) return track;
        return Math.max(8, track * visible / total);
    }

    private static int thumbStart(int track, int thumb, int scroll, int total, int visible) {
        if (total <= visible) return 0;
        return (track - thumb) * scroll / Math.max(1, total - visible);
    }

    private static int scrollFrom(int position, int track, int thumb, int total, int visible) {
        if (total <= visible) return 0;
        int span = Math.max(1, track - thumb);
        return Math.max(0, Math.min(total - visible, (position - thumb / 2) * (total - visible) / span));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        positionFields();
        pollDrag(mouseX, mouseY);
        panel(this.guiLeft, this.guiTop, leftWidth, this.ySize);
        panel(rightX, this.guiTop, rightWidth, 100);
        panel(rightX, this.guiTop + 104, rightWidth, this.ySize - 104);
        this.getFontRenderer().drawString(I18n.format("cnpcplus.fx.particles"), this.guiLeft + 5, this.guiTop + 5, 0x303030);
        drawRect(this.guiLeft + 4, this.guiTop + 18, this.guiLeft + leftWidth - 9, this.guiTop + this.ySize - 5, 0xFF333333);
        for (int row = 0; row < visibleParticles && row + particleScroll < particles.size(); row++) {
            EnumParticleTypes type = particles.get(row + particleScroll);
            String id = FxConfig.id(type);
            int y = listTop() + row * ROW;
            if (id.equals(particle)) drawRect(this.guiLeft + 4, y, this.guiLeft + leftWidth - 9, y + ROW, 0xFF3D6068);
            drawIcon(type, this.guiLeft + 6, y + 1);
            boolean configured = edit.stream().anyMatch(c -> c.particleId.equals(id));
            String name = this.getFontRenderer().trimStringToWidth(type.getParticleName(), leftWidth - 38);
            this.getFontRenderer().drawString(name, this.guiLeft + 26, y + 5, configured ? 0xFFB65A : 0xFFFFFF);
        }
        vBar(listTrackX(), listTop(), listHeight(), particleScroll, particles.size(), visibleParticles);
        this.getFontRenderer().drawString(I18n.format("cnpcplus.fx.shapes"), rightX + 4, this.guiTop + 5, 0x303030);
        FxConfig config = config();
        for (int column = 0; column < 5; column++) for (int row = 0; row < 2; row++) {
            int index = (column + galleryScroll) * 2 + row;
            if (index >= ShapeType.values().length) continue;
            ShapeType type = ShapeType.values()[index];
            int x = rightX + 4 + column * cellWidth, y = this.guiTop + 17 + row * 32;
            int color = shape() != null && shape().type == type ? 0xFF3CD9E6
                    : config != null && config.shapes.stream().anyMatch(p -> p.type == type) ? 0xFFFFA83C : 0xFF666666;
            drawRect(x, y, x + cellWidth - 2, y + 30, color);
            drawRect(x + 1, y + 1, x + cellWidth - 3, y + 29, 0xFF333333);
            double[] points = previews[index];
            for (int j = 0; j < points.length; j += 2) {
                int px = x + cellWidth / 2 + (int) (points[j] * 7), py = y + 9 + (int) (points[j + 1] * 7);
                drawRect(px, py, px + 1, py + 1, 0xFFE0F0F0);
            }
            String text = I18n.format("cnpcplus.fx.shape." + type.name());
            this.getFontRenderer().drawString(this.getFontRenderer().trimStringToWidth(text, cellWidth - 3), x + 1, y + 21, 0xFFFFFF);
        }
        hBar(rightX + 4, galleryTop(), rightWidth - 8, galleryScroll, (ShapeType.values().length + 1) / 2, 5);
        this.getFontRenderer().drawString(I18n.format("cnpcplus.fx.parameters"), rightX + 4, this.guiTop + 112, 0x303030);
        for (int i = 0; i < LABELS.length; i++) {
            GuiNpcTextField field = this.getTextField(100 + i);
            if (field.enabled) this.getFontRenderer().drawString(
                    this.getFontRenderer().trimStringToWidth(I18n.format("cnpcplus.fx." + LABELS[i]), field.x - rightX - 7),
                    rightX + 4, field.y + 4, 0x303030);
        }
        vBar(rightX + rightWidth - 7, paramTop(), paramHeight(), paramScroll, LABELS.length, paramRows());
        super.drawScreen(mouseX, mouseY, partialTicks);
        FxShape shape = shape();
        if (shape != null && mouseX >= rightX + 4 && mouseX < rightX + rightWidth - 9
                && mouseY >= paramTop() && mouseY < paramTop() + paramHeight()) {
            int field = paramScroll + (mouseY - paramTop()) / 18;
            if (field < LABELS.length) GuiUtils.drawHoveringText(Arrays.asList(I18n.format("cnpcplus.fx.tip." + LABELS[field])),
                    mouseX, mouseY, this.width, this.height, 220, this.getFontRenderer());
        }
    }

    /**
     * 画单个粒子图标。图集 particles.png 是 128x128、8x8 一格，这里把一格放大到 16x16。
     *
     * 旧写法 {@code drawModalRectWithCustomSizedTexture(x, y, sprite%16*8, sprite/16*8, 16, 16, 256, 256)}
     * 把 textureWidth 当成 256（实际 128），左边界比例从 c/16 变成 c/32 ——
     * 所有图标都取到了右边错的格子、右半边图集永远用不到，这就是"粒子类型显示很离谱"的真因。
     * 这里改成显式 UV 四边形，源格尺寸和贴图尺寸各自独立。
     */
    private void drawIcon(EnumParticleTypes type, int x, int y) {
        Integer sprite = SPRITES.get(type);
        if (sprite != null) {
            cell(ICONS, x, y, sprite % 16 * 8, sprite / 16 * 8, 8, 128, 128);
            return;
        }
        if (type == EnumParticleTypes.EXPLOSION_LARGE || type == EnumParticleTypes.EXPLOSION_HUGE) {
            // explosion.png 是 128x128 的 8x8 帧表，取中心最亮的一帧而不是把整张压成 16x16。
            cell(EXPLOSION, x, y, 48, 48, 16, 128, 128);
            return;
        }
        if (type == EnumParticleTypes.SWEEP_ATTACK) { cell(SWEEP, x, y, 0, 0, 32, 128, 32); return; }
        if (type == EnumParticleTypes.FOOTSTEP) { cell(FOOTPRINT, x, y, 0, 0, 8, 8, 8); return; }
        ItemStack stack = ICON_ITEMS.get(type);
        if (stack == null) { cell(ICONS, x, y, 0, 0, 8, 128, 128); return; }
        RenderHelper.enableGUIStandardItemLighting();
        this.itemRender.renderItemAndEffectIntoGUI(stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.color(1, 1, 1, 1);
    }

    /** 把贴图上 (u,v) 起 size×size 的一格铺满 16x16 的图标位置。 */
    private void cell(ResourceLocation texture, int x, int y, int u, int v, int size, int sheetWidth, int sheetHeight) {
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
        this.mc.getTextureManager().bindTexture(texture);
        float u0 = u / (float) sheetWidth, v0 = v / (float) sheetHeight;
        float u1 = (u + size) / (float) sheetWidth, v1 = (v + size) / (float) sheetHeight;
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + 16, 0).tex(u0, v1).endVertex();
        buffer.pos(x + 16, y + 16, 0).tex(u1, v1).endVertex();
        buffer.pos(x + 16, y, 0).tex(u1, v0).endVertex();
        buffer.pos(x, y, 0).tex(u0, v0).endVertex();
        tessellator.draw();
    }

    public static void panel(int x, int y, int w, int h) {
        drawRect(x, y, x + w, y + h, 0xFF111111);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF555555);
        drawRect(x + 1, y + 1, x + w - 2, y + h - 2, 0xFFFFFFFF);
        drawRect(x + 3, y + 3, x + w - 3, y + h - 3, 0xFFC6C6C6);
    }

    private static void vBar(int x, int y, int height, int scroll, int total, int visible) {
        drawRect(x, y, x + 4, y + height, 0xFF333333);
        int thumb = thumbSize(height, total, visible);
        int start = thumbStart(height, thumb, scroll, total, visible);
        drawRect(x, y + start, x + 4, y + start + thumb, 0xFFAAAAAA);
    }

    private static void hBar(int x, int y, int width, int scroll, int total, int visible) {
        drawRect(x, y, x + width, y + 5, 0xFF333333);
        int thumb = thumbSize(width, total, visible);
        int start = thumbStart(width, thumb, scroll, total, visible);
        drawRect(x + start, y, x + start + thumb, y + 5, 0xFFAAAAAA);
    }

    @Override
    public void mouseClicked(int x, int y, int button) throws IOException {
        if (button != 0) { super.mouseClicked(x, y, button); return; }
        // 先吃掉自定义区域，CNPC 的文本框/按钮处理不会再抢走这些点击。
        if (hitList(x, y)) {
            drag = 1;
            particleScroll = scrollFrom(y - listTop(), listHeight(), thumbSize(listHeight(), particles.size(), visibleParticles),
                    particles.size(), visibleParticles);
            return;
        }
        if (hitGalleryBar(x, y)) {
            drag = 2;
            galleryScroll = clamp(scrollFrom(x - (rightX + 4), rightWidth - 8, thumbSize(rightWidth - 8, (ShapeType.values().length + 1) / 2, 5),
                    (ShapeType.values().length + 1) / 2, 5), galleryMax());
            return;
        }
        if (hitParamBar(x, y)) {
            drag = 3;
            flush();
            paramScroll = scrollFrom(y - paramTop(), paramHeight(), thumbSize(paramHeight(), LABELS.length, paramRows()),
                    LABELS.length, paramRows());
            positionFields();
            return;
        }
        if (listRows(x, y)) return;
        if (galleryCells(x, y)) return;
        super.mouseClicked(x, y, button);
    }

    private boolean hitList(int x, int y) {
        return x >= listTrackX() && x < listTrackX() + 4 && y >= listTop() && y < listTop() + listHeight();
    }

    private boolean hitGalleryBar(int x, int y) {
        return x >= rightX + 4 && x < rightX + rightWidth - 4 && y >= galleryTop() && y < galleryTop() + 5;
    }

    private boolean hitParamBar(int x, int y) {
        return x >= rightX + rightWidth - 7 && x < rightX + rightWidth - 3 && y >= paramTop() && y < paramTop() + paramHeight();
    }

    private boolean listRows(int x, int y) {
        if (x < this.guiLeft + 4 || x >= this.guiLeft + leftWidth - 9 || y < listTop() || y >= this.guiTop + this.ySize - 5) return false;
        int index = particleScroll + (y - listTop()) / ROW;
        if (index < 0 || index >= particles.size()) return true;
        selectParticle(FxConfig.id(particles.get(index)));
        return true;
    }

    private boolean galleryCells(int x, int y) {
        if (x < rightX + 4 || x >= rightX + 4 + cellWidth * 5 || y < this.guiTop + 17 || y >= this.guiTop + 81) return false;
        int index = ((x - rightX - 4) / cellWidth + galleryScroll) * 2 + (y - this.guiTop - 17) / 32;
        if (index < 0 || index >= ShapeType.values().length) return true;
        flush();
        FxConfig config = configOrCreate();
        if (config == null) return true;
        ShapeType type = ShapeType.values()[index];
        shapeIndex = -1;
        for (int i = 0; i < config.shapes.size(); i++) if (config.shapes.get(i).type == type) shapeIndex = i;
        if (shapeIndex < 0) {
            FxShape shape = new FxShape();
            shape.type = type;
            config.shapes.add(shape);
            shapeIndex = config.shapes.size() - 1;
            if (config.shapes.size() == 1) paramScroll = 0;
        }
        refresh();
        return true;
    }

    @Override
    protected void mouseClickMove(int x, int y, int button, long time) {
        if (drag == 0) { super.mouseClickMove(x, y, button, time); return; }
        dragTo(x, y);
    }

    /** 拖动条跟随。全部按绝对坐标重算，重复调用不会叠加偏移。 */
    private void dragTo(int x, int y) {
        if (drag == 1) particleScroll = scrollFrom(y - listTop(), listHeight(),
                thumbSize(listHeight(), particles.size(), visibleParticles), particles.size(), visibleParticles);
        else if (drag == 2) galleryScroll = clamp(scrollFrom(x - (rightX + 4), rightWidth - 8,
                thumbSize(rightWidth - 8, (ShapeType.values().length + 1) / 2, 5), (ShapeType.values().length + 1) / 2, 5), galleryMax());
        else if (drag == 3) {
            flush();
            paramScroll = scrollFrom(y - paramTop(), paramHeight(), thumbSize(paramHeight(), LABELS.length, paramRows()),
                    LABELS.length, paramRows());
            positionFields();
        }
    }

    /**
     * CNPC 的父界面只转发 mouseClicked，不转发 mouseClickMove/mouseReleased，
     * 所以拖动要在每帧自己看左键状态，否则松手后滚动条还粘着鼠标。
     */
    private void pollDrag(int mouseX, int mouseY) {
        if (drag == 0) return;
        if (!Mouse.isButtonDown(0)) { drag = 0; return; }
        dragTo(mouseX, mouseY);
    }

    @Override
    protected void mouseReleased(int x, int y, int button) { drag = 0; super.mouseReleased(x, y, button); }

    @Override
    public void cnpcplus$wheel(int delta, int eventX, int eventY) {
        int x = eventX * this.width / this.mc.displayWidth;
        int y = this.height - eventY * this.height / this.mc.displayHeight - 1;
        int step = delta > 0 ? -1 : 1;
        if (x >= this.guiLeft && x < this.guiLeft + leftWidth) {
            particleScroll = clamp(particleScroll + step, particles.size() - visibleParticles);
        } else if (x >= rightX && x < rightX + rightWidth) {
            if (y < this.guiTop + 102) galleryScroll = clamp(galleryScroll + step, galleryMax());
            else {
                flush();
                paramScroll = clamp(paramScroll + step, LABELS.length - paramRows());
                positionFields();
            }
        }
    }

    private static int clamp(int value, int max) { return Math.max(0, Math.min(Math.max(0, max), value)); }
}
