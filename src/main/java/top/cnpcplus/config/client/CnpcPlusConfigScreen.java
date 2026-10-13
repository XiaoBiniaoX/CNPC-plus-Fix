package top.cnpcplus.config.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import top.cnpcplus.config.CnpcPlusConfigData;
import top.cnpcplus.perf.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Better_Modlist / Forge ConfigScreenFactory 用的客户端配置界面。
 * 只编辑 cnpcplus.toml 与 cnpcplus-perf.toml 中改完即生效的字段；
 * 保存时 ConfigValue.set() 会经 NightConfig autosave 落盘，perf 侧再手动 apply() 刷快照。
 * SERVER 配置（cnpcplus-server.toml）不在此界面，须在服务端存档配置目录修改。
 */
public class CnpcPlusConfigScreen extends Screen {

    private static final int ROW_H = 22;

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private double scroll;
    private int contentHeight;

    private record Row(String label, EditBox box, Button toggle) {
        static Row header(String label) { return new Row(label, null, null); }
        boolean isHeader() { return box == null && toggle == null; }
    }

    public CnpcPlusConfigScreen(Screen parent) {
        super(Component.literal("CNPCplus 配置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rows.clear();
        scroll = 0;

        addHeader("主配置 cnpcplus.toml");
        addBool("合成视图侧栏", CnpcPlusConfigData.CraftingViewEnabled);
        addString("合成分类", CnpcPlusConfigData.CraftingCategories);
        addString("配方模糊规则", CnpcPlusConfigData.RecipeFuzzyMatchRules);
        addDouble("对话框NPC缩放", CnpcPlusConfigData.DialogNpcScale);
        addInt("对话框NPC X", CnpcPlusConfigData.DialogNpcPosX);
        addInt("对话框NPC Y", CnpcPlusConfigData.DialogNpcPosY);
        addDouble("对话框选项缩放", CnpcPlusConfigData.DialogOptionScale);
        addInt("对话框选项 X", CnpcPlusConfigData.DialogOptionPosX);
        addInt("对话框选项 Y", CnpcPlusConfigData.DialogOptionPosY);
        addInt("对话框选项间距", CnpcPlusConfigData.DialogOptionSpacing);
        addDouble("诗人口量 0~1", CnpcPlusConfigData.BardVolume);
        addInt("诗人看门狗秒", CnpcPlusConfigData.BardWatchdogSeconds);

        addHeader("性能配置 cnpcplus-perf.toml");
        addDouble("阴影半径上限", Config.shadowValue());
        addBool("轮廓缓存", Config.outlineEnabledValue());
        addInt("轮廓缓存项数", Config.outlineCapacityValue());
        addBool("蓝图预览缓存", Config.previewEnabledValue());
        addDouble("预览每帧毫秒", Config.budgetMillisValue());
        addInt("预览每帧方块", Config.blocksPerFrameValue());

        contentHeight = rows.size() * ROW_H;

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> {
            saveAll();
            minecraft.setScreen(parent);
        }).bounds(width / 2 - 100, this.height - 26, 200, 20).build());

        layoutRows();
    }

    private void addHeader(String label) {
        rows.add(Row.header(label));
    }

    private void addString(String label, net.minecraftforge.common.ForgeConfigSpec.ConfigValue<String> value) {
        EditBox box = new EditBox(font, 0, 0, 140, 18, Component.literal(label));
        String cur = value.get();
        box.setValue(cur == null ? "" : cur);
        box.setMaxLength(2048);
        box.setResponder(text -> value.set(text));
        rows.add(new Row(label, box, null));
        addRenderableWidget(box);
    }

    private void addInt(String label, net.minecraftforge.common.ForgeConfigSpec.IntValue value) {
        EditBox box = new EditBox(font, 0, 0, 80, 18, Component.literal(label));
        box.setValue(String.valueOf(value.get()));
        box.setMaxLength(16);
        box.setResponder(text -> {
            try { value.set(Integer.parseInt(text.trim())); } catch (NumberFormatException ignored) {}
        });
        rows.add(new Row(label, box, null));
        addRenderableWidget(box);
    }

    private void addDouble(String label, net.minecraftforge.common.ForgeConfigSpec.DoubleValue value) {
        EditBox box = new EditBox(font, 0, 0, 80, 18, Component.literal(label));
        box.setValue(String.valueOf(value.get()));
        box.setMaxLength(16);
        box.setResponder(text -> {
            try { value.set(Double.parseDouble(text.trim())); } catch (NumberFormatException ignored) {}
        });
        rows.add(new Row(label, box, null));
        addRenderableWidget(box);
    }

    private void addBool(String label, net.minecraftforge.common.ForgeConfigSpec.BooleanValue value) {
        boolean[] state = { value.get() };
        Button btn = Button.builder(Component.literal(state[0] ? "true" : "false"), b -> {
            state[0] = !state[0];
            value.set(state[0]);
            b.setMessage(Component.literal(state[0] ? "true" : "false"));
        }).bounds(0, 0, 56, 18).build();
        rows.add(new Row(label, null, btn));
        addRenderableWidget(btn);
    }

    private void saveAll() {
        // perf 快照字段在 set() 后不会自动刷新，手动 apply 一次；
        // 主配置多数是每次 get() 热读，set() 即生效。
        Config.apply();
        // 合成分类改完要重载分类缓存
        top.cnpcplus.craftingview.Config.reload();
    }

    private void layoutRows() {
        int top = 30;
        int left = 20;
        int labelW = 130;
        int boxX = left + labelW;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int y = top + i * ROW_H - (int) scroll;
            boolean visible = y + ROW_H > 28 && y < height - 30;
            if (r.box != null) {
                r.box.visible = visible;
                r.box.setPosition(boxX, y + 2);
            }
            if (r.toggle != null) {
                r.toggle.visible = visible;
                r.toggle.setPosition(boxX, y + 1);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double maxScroll = Math.max(0, contentHeight - (height - 60));
        scroll = Math.max(0, Math.min(maxScroll, scroll - delta * ROW_H));
        layoutRows();
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(0, 24, width, 26, 0xFF404040);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);

        int left = 20;
        int top = 30;
        int boxX = left + 130;
        g.enableScissor(0, 28, width, height - 30);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int y = top + i * ROW_H - (int) scroll;
            if (y + ROW_H <= 28 || y >= height - 30) continue;
            if (r.isHeader()) {
                g.drawString(font, r.label, left, y + 7, 0xFFAA00, false);
            } else {
                g.drawString(font, r.label, left, y + 7, 0xFFFFFF, false);
            }
        }
        g.disableScissor();

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
