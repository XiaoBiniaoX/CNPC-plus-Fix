package top.cnpcplus.config.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import top.cnpcplus.config.CnpcPlusConfigData;
import top.cnpcplus.config.CnpcPlusServerConfig;
import top.cnpcplus.config.NpcAiOptimizerConfig;
import top.cnpcplus.config.ServerConfigAccess;
import top.cnpcplus.config.network.ConfigPacketHandler;
import top.cnpcplus.config.network.PacketUpdateServerConfig;
import top.cnpcplus.perf.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Better_Modlist / Forge ConfigScreenFactory 用的客户端配置界面。
 * 客户端配置改完即生效；SERVER 配置通过 {@link PacketUpdateServerConfig}
 * 交给服务端做权限校验后写入 cnpcplus-server.toml，并立即被服务端逻辑热读取。
 */
public class CnpcPlusConfigScreen extends Screen {

    private static final int ROW_H = 22;

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private double scroll;
    private int contentHeight;

    private boolean serverEditable;
    private boolean serverRealArmor;
    private EditBox serverTeleportRangeBox;
    private EditBox serverMaxHiredBox;
    private EditBox serverHireMessageBox;

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

        serverEditable = minecraft != null && minecraft.level != null && minecraft.getConnection() != null;
        addHeader(serverEditable
                ? "服务端配置 cnpcplus-server.toml（需管理员）"
                : "服务端配置（进入世界后可改）");
        addServerBool("（NPC）真实护甲值",
                ServerConfigAccess.bool(CnpcPlusServerConfig.RealArmorEnabled, false));
        serverTeleportRangeBox = addServerInt("随从瞬移跟随距离",
                ServerConfigAccess.integer(CnpcPlusServerConfig.FollowerTeleportRange, 12));
        serverMaxHiredBox = addServerInt("玩家最大雇佣NPC数",
                ServerConfigAccess.integer(CnpcPlusServerConfig.MaxHiredNpcs, 30));
        serverHireMessageBox = addServerString("雇佣超限提示",
                ServerConfigAccess.string(CnpcPlusServerConfig.HireLimitMessage, "你雇佣的人过多"));

        addHeader("性能配置 cnpcplus-perf.toml");
        addDouble("阴影半径上限", Config.shadowValue());
        addBool("轮廓缓存", Config.outlineEnabledValue());
        addInt("轮廓缓存项数", Config.outlineCapacityValue());
        addBool("蓝图预览缓存", Config.previewEnabledValue());
        addDouble("预览每帧毫秒", Config.budgetMillisValue());
        addInt("预览每帧方块", Config.blocksPerFrameValue());

        addHeader("NPC AI 优化（cnpcplus-npcfix.toml）");
        addBool("NPC AI 优化总开关", NpcAiOptimizerConfig.ENABLED);
        addInt("仇恨搜索间隔 tick", NpcAiOptimizerConfig.TARGET_SEARCH_INTERVAL_TICKS);
        addBool("仇恨搜索错峰", NpcAiOptimizerConfig.STAGGER_TARGET_SEARCHES);
        addInt("有效仇恨范围上限", NpcAiOptimizerConfig.MAX_EFFECTIVE_AGGRO_RANGE);
        addBool("脚本关闭仍触发 UpdateEvent", NpcAiOptimizerConfig.UPDATE_EVENT_WHEN_SCRIPTS_DISABLED);
        addInt("UpdateEvent 间隔 tick", NpcAiOptimizerConfig.UPDATE_EVENT_INTERVAL_TICKS);
        addBool("脚本关闭仍扫描碰撞", NpcAiOptimizerConfig.COLLIDE_SCAN_WHEN_SCRIPTS_DISABLED);
        addInt("碰撞扫描间隔 tick", NpcAiOptimizerConfig.COLLIDE_SCAN_INTERVAL_TICKS);

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

    private void addServerBool(String label, boolean initial) {
        this.serverRealArmor = initial;
        Button btn = Button.builder(Component.literal(initial ? "true" : "false"), b -> {
            this.serverRealArmor = !this.serverRealArmor;
            b.setMessage(Component.literal(this.serverRealArmor ? "true" : "false"));
        }).bounds(0, 0, 56, 18).build();
        btn.active = this.serverEditable;
        rows.add(new Row(label, null, btn));
        addRenderableWidget(btn);
    }

    private EditBox addServerInt(String label, int initial) {
        EditBox box = new EditBox(font, 0, 0, 80, 18, Component.literal(label));
        box.setValue(String.valueOf(initial));
        box.setMaxLength(8);
        box.setEditable(this.serverEditable);
        rows.add(new Row(label, box, null));
        addRenderableWidget(box);
        return box;
    }

    private EditBox addServerString(String label, String initial) {
        EditBox box = new EditBox(font, 0, 0, 200, 18, Component.literal(label));
        if (initial != null && initial.length() > 128) initial = initial.substring(0, 128);
        box.setValue(initial == null ? "" : initial);
        box.setMaxLength(128);
        box.setEditable(this.serverEditable);
        rows.add(new Row(label, box, null));
        addRenderableWidget(box);
        return box;
    }

    private void saveAll() {
        // perf 快照字段在 set() 后不会自动刷新，手动 apply 一次；
        // 主配置多数是每次 get() 热读，set() 即生效。
        Config.apply();
        // 合成分类改完要重载分类缓存
        top.cnpcplus.craftingview.Config.reload();
        saveServerConfig();
        saveAiOptimizerConfig();
    }

    private void saveAiOptimizerConfig() {
        if (minecraft == null || minecraft.getSingleplayerServer() == null) return;
        try {
            NpcAiOptimizerConfig.SPEC.save();
        } catch (Throwable ignored) {
        }
    }

    private void saveServerConfig() {
        if (!this.serverEditable) return;
        int range = Mth.clamp(parseInt(this.serverTeleportRangeBox.getValue(), 12), 1, 64);
        int maxHired = Mth.clamp(parseInt(this.serverMaxHiredBox.getValue(), 30), 1, 10000);
        String message = this.serverHireMessageBox.getValue();
        ConfigPacketHandler.CHANNEL.sendToServer(
                new PacketUpdateServerConfig(this.serverRealArmor, range, maxHired, message));
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
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
