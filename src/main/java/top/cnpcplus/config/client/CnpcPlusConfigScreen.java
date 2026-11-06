package top.cnpcplus.config.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import top.cnpcplus.config.CnpcPlusConfigData;
import top.cnpcplus.config.CnpcPlusServerConfig;
import top.cnpcplus.config.NpcAiOptimizerConfig;
import top.cnpcplus.config.NpcInteractionConfig;
import top.cnpcplus.config.ServerConfigAccess;
import top.cnpcplus.config.network.ConfigPacketHandler;
import top.cnpcplus.config.network.PacketUpdateServerConfig;
import top.cnpcplus.config.network.PacketNpcInteractionConfig;
import top.cnpcplus.perf.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Better_Modlist / Forge ConfigScreenFactory 用的客户端配置界面。
 * 客户端配置改完即生效；SERVER 配置通过 {@link PacketUpdateServerConfig}
 * 交给服务端做权限校验后写入对应配置文件，并立即被服务端逻辑热读取。
 */
public class CnpcPlusConfigScreen extends Screen {

    private static final int ROW_H = 22;

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private double scroll;
    private int contentHeight;

    private boolean serverEditable;
    private boolean serverRealArmor;
    private boolean allowInvisibleInteraction;
    private boolean interactionChanged;
    private EditBox serverTeleportRangeBox;
    private EditBox serverMaxHiredBox;

    private record Row(String label, EditBox box, Button toggle) {
        static Row header(String label) { return new Row(label, null, null); }
        boolean isHeader() { return box == null && toggle == null; }
    }

    public CnpcPlusConfigScreen(Screen parent) {
        super(Component.translatable("cnpcplus.config.client.title"));
        this.parent = parent;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    @Override
    protected void init() {
        rows.clear();
        scroll = 0;

        serverEditable = minecraft != null && minecraft.level != null && minecraft.getConnection() != null;
        interactionChanged = false;
        allowInvisibleInteraction = ServerConfigAccess.bool(NpcInteractionConfig.ALLOW_INVISIBLE_NPC_INTERACTION, false);
        addHeader(tr("cnpcplus.config.client.header.interaction"));
        addServerBool(tr("cnpcplus.config.interaction.allowInvisibleNpc"), allowInvisibleInteraction, value -> {
            allowInvisibleInteraction = value;
            interactionChanged = true;
        }).setTooltip(Tooltip.create(Component.translatable("cnpcplus.config.interaction.allowInvisibleNpc.tooltip")));

        addHeader(tr("cnpcplus.config.client.header.main"));
        addBool(tr("cnpcplus.config.client.main.craftingView"), CnpcPlusConfigData.CraftingViewEnabled);
        addBool(tr("cnpcplus.config.client.main.inventoryFaction"), CnpcPlusConfigData.InventoryFactionTab);
        addBool(tr("cnpcplus.config.client.main.inventoryQuest"), CnpcPlusConfigData.InventoryQuestTab);
        addBool(tr("cnpcplus.config.client.main.wandHitbox"), CnpcPlusConfigData.WandNpcHitboxes);
        addString(tr("cnpcplus.config.client.main.craftingCategories"), CnpcPlusConfigData.CraftingCategories);
        addString(tr("cnpcplus.config.client.main.recipeFuzzy"), CnpcPlusConfigData.RecipeFuzzyMatchRules);
        addDouble(tr("cnpcplus.config.client.main.dialogNpcScale"), CnpcPlusConfigData.DialogNpcScale);
        addInt(tr("cnpcplus.config.client.main.dialogNpcX"), CnpcPlusConfigData.DialogNpcPosX);
        addInt(tr("cnpcplus.config.client.main.dialogNpcY"), CnpcPlusConfigData.DialogNpcPosY);
        addDouble(tr("cnpcplus.config.client.main.dialogOptionScale"), CnpcPlusConfigData.DialogOptionScale);
        addInt(tr("cnpcplus.config.client.main.dialogOptionX"), CnpcPlusConfigData.DialogOptionPosX);
        addInt(tr("cnpcplus.config.client.main.dialogOptionY"), CnpcPlusConfigData.DialogOptionPosY);
        addInt(tr("cnpcplus.config.client.main.dialogOptionSpacing"), CnpcPlusConfigData.DialogOptionSpacing);
        addDouble(tr("cnpcplus.config.client.main.bardVolume"), CnpcPlusConfigData.BardVolume);
        addInt(tr("cnpcplus.config.client.main.bardWatchdog"), CnpcPlusConfigData.BardWatchdogSeconds);

        addHeader(tr(serverEditable
                ? "cnpcplus.config.client.header.server"
                : "cnpcplus.config.client.header.serverLocked"));
        serverRealArmor = ServerConfigAccess.bool(CnpcPlusServerConfig.RealArmorEnabled, false);
        addServerBool(tr("cnpcplus.config.client.server.realArmor"), serverRealArmor,
                value -> serverRealArmor = value);
        serverTeleportRangeBox = addServerInt(tr("cnpcplus.config.client.server.followerRange"),
                ServerConfigAccess.integer(CnpcPlusServerConfig.FollowerTeleportRange, 12));
        serverMaxHiredBox = addServerInt(tr("cnpcplus.config.client.server.maxHired"),
                ServerConfigAccess.integer(CnpcPlusServerConfig.MaxHiredNpcs, 30));

        addHeader(tr("cnpcplus.config.client.header.perf"));
        addDouble(tr("cnpcplus.config.client.perf.shadow"), Config.shadowValue());
        addBool(tr("cnpcplus.config.client.perf.outline"), Config.outlineEnabledValue());
        addInt(tr("cnpcplus.config.client.perf.outlineCapacity"), Config.outlineCapacityValue());
        addBool(tr("cnpcplus.config.client.perf.preview"), Config.previewEnabledValue());
        addDouble(tr("cnpcplus.config.client.perf.budget"), Config.budgetMillisValue());
        addInt(tr("cnpcplus.config.client.perf.blocks"), Config.blocksPerFrameValue());

        addHeader(tr("cnpcplus.config.client.header.npcai"));
        addBool(tr("cnpcplus.config.client.ai.enabled"), NpcAiOptimizerConfig.ENABLED);
        addInt(tr("cnpcplus.config.client.ai.targetInterval"), NpcAiOptimizerConfig.TARGET_SEARCH_INTERVAL_TICKS);
        addBool(tr("cnpcplus.config.client.ai.stagger"), NpcAiOptimizerConfig.STAGGER_TARGET_SEARCHES);
        addInt(tr("cnpcplus.config.client.ai.aggroCap"), NpcAiOptimizerConfig.MAX_EFFECTIVE_AGGRO_RANGE);
        addBool(tr("cnpcplus.config.client.ai.updateEvent"), NpcAiOptimizerConfig.UPDATE_EVENT_WHEN_SCRIPTS_DISABLED);
        addInt(tr("cnpcplus.config.client.ai.updateInterval"), NpcAiOptimizerConfig.UPDATE_EVENT_INTERVAL_TICKS);
        addBool(tr("cnpcplus.config.client.ai.collideScan"), NpcAiOptimizerConfig.COLLIDE_SCAN_WHEN_SCRIPTS_DISABLED);
        addInt(tr("cnpcplus.config.client.ai.collideInterval"), NpcAiOptimizerConfig.COLLIDE_SCAN_INTERVAL_TICKS);

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

    private Button addServerBool(String label, boolean initial, Consumer<Boolean> setter) {
        boolean[] state = { initial };
        Button btn = Button.builder(Component.literal(initial ? "true" : "false"), b -> {
            state[0] = !state[0];
            setter.accept(state[0]);
            b.setMessage(Component.literal(state[0] ? "true" : "false"));
        }).bounds(0, 0, 56, 18).build();
        btn.active = this.serverEditable;
        rows.add(new Row(label, null, btn));
        addRenderableWidget(btn);
        return btn;
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

    private void saveAll() {
        // perf 快照字段在 set() 后不会自动刷新，手动 apply 一次；
        // 主配置多数是每次 get() 热读，set() 即生效。
        Config.apply();
        // 合成分类改完要重载分类缓存
        top.cnpcplus.craftingview.Config.reload();
        if (serverEditable && interactionChanged) {
            ConfigPacketHandler.CHANNEL.sendToServer(new PacketNpcInteractionConfig(allowInvisibleInteraction));
        }
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
        ConfigPacketHandler.CHANNEL.sendToServer(
                new PacketUpdateServerConfig(this.serverRealArmor, range, maxHired));
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
