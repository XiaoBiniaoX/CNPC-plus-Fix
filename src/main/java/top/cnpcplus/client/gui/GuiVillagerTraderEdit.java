package top.cnpcplus.client.gui;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.packets.Packets;
import noppes.npcs.packets.server.SPacketNpcRoleSave;
import top.cnpcplus.roles.RoleVillagerTrader;
import top.cnpcplus.roles.TradeData;
import top.cnpcplus.villagertrade.EditableMerchant;
import top.cnpcplus.villagertrade.EditableMerchantMenu;
import top.cnpcplus.villagertrade.EditMode;
import top.cnpcplus.villagertrade.network.PacketEditInventoryCommit;
import top.cnpcplus.villagertrade.network.VillagerEditPacketHandler;
import top.cnpcplus.mixin.MerchantScreenAccessor;

public class GuiVillagerTraderEdit extends MerchantScreen {

    private final EntityNPCInterface npc;
    private final RoleVillagerTrader role;
    private final EditableMerchant merchant;
    private final EditableMerchantMenu merchantMenu;
    private final List<TradeData> trades;

    private Button btnAdd;
    private Button btnDelete;
    private Button btnLimit;
    private Button btnNameOnly;
    private EditBox limitCountBox;
    private EditBox refreshTimeBox;
    private int lastShopItem = -1;
    private ItemStack[] invSnapshot;

    public GuiVillagerTraderEdit(EntityNPCInterface npc, EditableMerchantMenu menu, Inventory inv, Component title, EditableMerchant merchant) {
        super(menu, inv, title);
        this.npc = npc;
        this.role = (RoleVillagerTrader) npc.role;
        this.merchantMenu = menu;
        this.trades = role.getTrades();
        this.merchant = merchant;
        takeSnapshot();
    }

    /**
     * 根因修复：默认 slotClicked → gameMode.handleInventoryMouseClick 实际执行在
     * player.containerMenu（InventoryMenu@0，与我们 containerId=0 撞 id）上，
     * 用的却是本菜单的槽位索引 → 双端改错槽 = 幽灵物品/拿不起来（carried 落在 InventoryMenu）。
     * 覆写后直接在本菜单本地执行：不走 gameMode、不发包、不碰 InventoryMenu。
     * 实时写回（哈基彬要求"写入后实时修改，反应进缓冲层"）：每次点击后槽位
     * 有变化就立即 sync + saveTrades，不再等 mouseReleased。
     */
    @Override
    protected void slotClicked(Slot slot, int slotId, int button, ClickType clickType) {
        int id = slot != null ? slot.index : slotId;
        ItemStack[] before = tradeSlotSnapshot();
        merchantMenu.clicked(id, button, clickType, Minecraft.getInstance().player);
        if (lastShopItem >= 0 && lastShopItem < merchant.getOffers().size() && tradeSlotsChanged(before)) {
            merchantMenu.syncFromSlots(lastShopItem);
            syncTradesFromOffers();
        }
    }

    private ItemStack[] tradeSlotSnapshot() {
        var c = ((top.cnpcplus.mixin.MerchantMenuAccessor) merchantMenu).getTradeContainer();
        return new ItemStack[]{c.getItem(0).copy(), c.getItem(1).copy(), c.getItem(2).copy()};
    }

    private boolean tradeSlotsChanged(ItemStack[] before) {
        var c = ((top.cnpcplus.mixin.MerchantMenuAccessor) merchantMenu).getTradeContainer();
        for (int i = 0; i < 3; i++) {
            if (!ItemStack.matches(before[i], c.getItem(i))) return true;
        }
        return false;
    }

    private void takeSnapshot() {
        var lp = Minecraft.getInstance().player;
        if (lp == null) return;
        var inv = lp.getInventory();
        invSnapshot = new ItemStack[inv.items.size() + inv.armor.size() + inv.offhand.size()];
        int i = 0;
        for (var s : inv.items) invSnapshot[i++] = s.copy();
        for (var s : inv.armor) invSnapshot[i++] = s.copy();
        for (var s : inv.offhand) invSnapshot[i++] = s.copy();
    }

    /** 编辑期间的背包改动只存在于客户端，恢复快照防止与服务端脱节产生永久幽灵。 */
    private void restoreSnapshot() {
        if (invSnapshot == null) return;
        var lp = Minecraft.getInstance().player;
        if (lp == null) return;
        var inv = lp.getInventory();
        int i = 0;
        for (int j = 0; j < inv.items.size(); j++) inv.items.set(j, invSnapshot[i++].copy());
        for (int j = 0; j < inv.armor.size(); j++) inv.armor.set(j, invSnapshot[i++].copy());
        for (int j = 0; j < inv.offhand.size(); j++) inv.offhand.set(j, invSnapshot[i++].copy());
        merchantMenu.setCarried(ItemStack.EMPTY);
    }

    @Override
    protected void init() {
        EditMode.active = true;
        super.init();

        int lx = this.leftPos - 100;
        int ly = this.topPos + 10;

        this.btnAdd = this.addRenderableWidget(Button.builder(
            Component.literal("+ 添加交易"), b -> onAddTrade())
            .bounds(lx, ly, 95, 20).build());

        this.btnDelete = this.addRenderableWidget(Button.builder(
            Component.literal("删除选中"), b -> onDeleteTrade())
            .bounds(lx, ly + 25, 95, 20).build());

        this.btnLimit = this.addRenderableWidget(Button.builder(
            Component.literal("限购: 关"), b -> onToggleLimit())
            .bounds(lx, ly + 50, 95, 20).build());

        // 仅名字检查：mod 物品 NBT 每次变化时，匹配只校验物品 id + 展示名（忽略 NBT）
        this.btnNameOnly = this.addRenderableWidget(Button.builder(
            Component.translatable("cnpcplus.trader.nameonly.off"), b -> onToggleNameOnly())
            .bounds(lx, ly + 95, 95, 20).build());

        this.limitCountBox = new EditBox(this.font, lx, ly + 75, 40, 16, Component.literal("限购次数"));
        this.limitCountBox.setMaxLength(4);
        this.limitCountBox.setFilter(s -> s.matches("\\d*"));
        this.limitCountBox.setTooltip(Tooltip.create(Component.literal("这是限购多少次？")));
        this.limitCountBox.setResponder(s -> {
            int idx = lastShopItem;
            if (idx >= 0 && idx < trades.size()) {
                try {
                    trades.get(idx).limitCount = Integer.parseInt(s.isEmpty() ? "0" : s);
                    maybeArmRefresh(trades.get(idx));
                    saveTrades();
                } catch (NumberFormatException ignored) {}
            }
        });
        this.addRenderableWidget(this.limitCountBox);

        this.refreshTimeBox = new EditBox(this.font, lx + 50, ly + 75, 45, 16, Component.literal("刷新秒"));
        this.refreshTimeBox.setMaxLength(5);
        this.refreshTimeBox.setFilter(s -> s.matches("\\d*"));
        this.refreshTimeBox.setTooltip(Tooltip.create(Component.literal("耗尽后恢复购买的等待时间，单位：秒（不是tick）")));
        this.refreshTimeBox.setResponder(s -> {
            int idx = lastShopItem;
            if (idx >= 0 && idx < trades.size()) {
                try {
                    trades.get(idx).refreshInterval = Float.parseFloat(s.isEmpty() ? "0" : s);
                    maybeArmRefresh(trades.get(idx));
                    saveTrades();
                } catch (NumberFormatException ignored) {}
            }
        });
        this.addRenderableWidget(this.refreshTimeBox);

        // 不默认选中条目：必须点击条目选中后才允许修改
        // （shopItem 字段默认=0 且 init 不重置，靠 lastShopItem 区分"真选中"）。
        updateButtons();
    }

    @Override
    public void removed() {
        // 关面板前兜底写回：防最后一次按下的编辑没等到 mouseReleased 就被 setScreen 掐掉
        if (lastShopItem >= 0 && lastShopItem < merchant.getOffers().size()) {
            merchantMenu.syncFromSlots(lastShopItem);
            syncTradesFromOffers();
        }
        EditMode.active = false;
        super.removed(); // 原版 menu.removed 只归还 ServerPlayer 的 carried，本地是 no-op
        var lp = Minecraft.getInstance().player;
        if (lp == null) return;
        if (Minecraft.getInstance().hasSingleplayerServer()) {
            // 阶段223：单机=提交编辑期背包改动（原 restoreSnapshot 回滚导致"复制物品失效"，
            // 所有槽位点击都在本地执行、服务端不知情 → 只有幽灵）。carried 先并回背包再算 diff。
            ItemStack carried = merchantMenu.getCarried();
            if (!carried.isEmpty() && lp.getInventory().add(carried)) {
                merchantMenu.setCarried(ItemStack.EMPTY);
            }
            commitInventoryChanges();
        } else {
            // 专用服：维持 215/216 安全语义——回滚客户端，防提交包变成改背包作弊通道
            restoreSnapshot();
        }
    }

    /** 关面板 diff：编辑期背包改动（对比开面板快照）发服务端落盘。 */
    private void commitInventoryChanges() {
        if (invSnapshot == null) return;
        var lp = Minecraft.getInstance().player;
        if (lp == null) return;
        var inv = lp.getInventory();
        List<PacketEditInventoryCommit.Entry> changes = new java.util.ArrayList<>();
        int i = 0;
        int armorBase = inv.items.size();
        int offhandBase = armorBase + inv.armor.size();
        for (int j = 0; j < inv.items.size(); j++, i++) {
            if (!ItemStack.matches(invSnapshot[i], inv.items.get(j))) {
                changes.add(new PacketEditInventoryCommit.Entry(i, inv.items.get(j).copy()));
            }
        }
        for (int j = 0; j < inv.armor.size(); j++, i++) {
            if (!ItemStack.matches(invSnapshot[i], inv.armor.get(j))) {
                changes.add(new PacketEditInventoryCommit.Entry(armorBase + j, inv.armor.get(j).copy()));
            }
        }
        for (int j = 0; j < inv.offhand.size(); j++, i++) {
            if (!ItemStack.matches(invSnapshot[i], inv.offhand.get(j))) {
                changes.add(new PacketEditInventoryCommit.Entry(offhandBase + j, inv.offhand.get(j).copy()));
            }
        }
        if (!changes.isEmpty()) {
            VillagerEditPacketHandler.CHANNEL.sendToServer(new PacketEditInventoryCommit(changes));
        }
    }

    private void saveTrades() {
        role.saveTrades(trades);
        Packets.sendServer(new SPacketNpcRoleSave(role.save(new CompoundTag())));
    }

    private void syncTradesFromOffers() {
        MerchantOffers offers = merchant.getOffers();
        int oldSize = trades.size();
        List<TradeData> newTrades = new java.util.ArrayList<>();
        for (int i = 0; i < offers.size(); i++) {
            MerchantOffer o = offers.get(i);
            TradeData t = new TradeData(o.getCostA().copy(), o.getCostB().copy(), o.getResult().copy(), o.getMaxUses(), o.getXp(), o.getPriceMultiplier());
            if (i < oldSize) {
                TradeData old = trades.get(i);
                t.limitEnabled = old.limitEnabled;
                t.limitCount = old.limitCount;
                t.refreshInterval = old.refreshInterval;
                t.uses = old.uses;
                t.limitResetAt = old.limitResetAt;
                t.nameOnly = old.nameOnly;
            }
            newTrades.add(t);
        }
        trades.clear();
        trades.addAll(newTrades);
        saveTrades();
    }

    private void onAddTrade() {
        merchant.addOffer(new MerchantOffer(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, 16, 2, 0.05f));
        syncTradesFromOffers();
        rebuildWidgets();
        int newIdx = merchant.getOffers().size() - 1;
        ((MerchantScreenAccessor) this).setShopItem(newIdx);
        merchantMenu.loadOfferToSlots(newIdx);
        lastShopItem = newIdx;
        updateButtons();
    }

    private void onDeleteTrade() {
        int idx = lastShopItem;
        if (idx >= 0 && idx < merchant.getOffers().size()) {
            merchant.removeOffer(idx);
            syncTradesFromOffers();
            if (!merchant.getOffers().isEmpty()) {
                int newIdx = Math.min(idx, merchant.getOffers().size() - 1);
                ((MerchantScreenAccessor) this).setShopItem(newIdx);
                merchantMenu.loadOfferToSlots(newIdx);
                lastShopItem = newIdx;
            } else {
                ((MerchantScreenAccessor) this).setShopItem(-1);
                lastShopItem = -1;
            }
            rebuildWidgets();
            updateButtons();
        }
    }

    private void onToggleLimit() {
        int idx = lastShopItem;
        if (idx >= 0 && idx < trades.size()) {
            TradeData t = trades.get(idx);
            t.limitEnabled = !t.limitEnabled;
            if (t.limitEnabled) {
                maybeArmRefresh(t);
            } else {
                // 关限购 = 回归原版 16 次语义：清掉限购累计，防残留 uses 削短原版槽位
                t.uses = 0;
                t.limitResetAt = 0;
            }
            saveTrades();
            updateButtons();
        }
    }

    /**
     * 阶段224根因修复：耗尽后才配置刷新秒/次数时，limitResetAt 恒 0 → buildOffers
     * 重置条件永不满足 → "进入编辑页关闭，重新进玩家页，限购未重置"。
     * 设置项变化时立即启动计时（与服务端 buildOffers 开页启动双路幂等，值相同时互不干扰）。
     */
    private void maybeArmRefresh(TradeData t) {
        if (t.limitEnabled && t.uses >= Math.max(1, t.limitCount)
                && t.refreshInterval > 0 && t.limitResetAt <= 0) {
            t.limitResetAt = System.currentTimeMillis() + (long) (t.refreshInterval * 1000.0);
        }
    }

    private void onToggleNameOnly() {
        int idx = lastShopItem;
        if (idx >= 0 && idx < trades.size()) {
            TradeData t = trades.get(idx);
            t.nameOnly = !t.nameOnly;
            saveTrades();
            updateButtons();
        }
    }

    private int getSelectedTradeIndex() {
        return ((MerchantScreenAccessor) this).getShopItem();
    }

    private void updateButtons() {
        int idx = lastShopItem;
        boolean has = idx >= 0 && idx < trades.size();
        boolean limitOn = has && trades.get(idx).limitEnabled;
        if (this.btnLimit != null) {
            this.btnLimit.active = has;
            if (has) {
                this.btnLimit.setMessage(Component.literal(trades.get(idx).limitEnabled ? "限购: 开" : "限购: 关"));
            }
        }
        if (this.btnDelete != null) {
            this.btnDelete.active = has;
        }
        if (this.btnNameOnly != null) {
            this.btnNameOnly.active = has;
            if (has) {
                this.btnNameOnly.setMessage(Component.translatable(trades.get(idx).nameOnly
                    ? "cnpcplus.trader.nameonly.on" : "cnpcplus.trader.nameonly.off"));
            }
        }
        if (this.limitCountBox != null) {
            this.limitCountBox.setEditable(limitOn);
            this.limitCountBox.setVisible(limitOn);
            if (limitOn) {
                this.limitCountBox.setValue(String.valueOf(trades.get(idx).limitCount));
            }
        }
        if (this.refreshTimeBox != null) {
            this.refreshTimeBox.setEditable(limitOn);
            this.refreshTimeBox.setVisible(limitOn);
            if (limitOn) {
                this.refreshTimeBox.setValue(String.valueOf((int) trades.get(idx).refreshInterval));
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean result = super.mouseClicked(mouseX, mouseY, button);
        // 只有点击条目按钮（vanilla lambda → postButtonClick → tryMoveItems）才算选中：
        // shopItem 字段默认=0，裸比较会被"没选中"误判成条目0（抹除数据的根因）。
        int clicked = merchantMenu.takeEntryClick();
        if (clicked >= 0 && clicked < merchant.getOffers().size()) {
            merchantMenu.loadOfferToSlots(clicked);
            lastShopItem = clicked;
            updateButtons();
        }
        return result;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean result = super.mouseReleased(mouseX, mouseY, button);
        int curIdx = getSelectedTradeIndex();
        if (lastShopItem >= 0 && curIdx == lastShopItem && curIdx < merchant.getOffers().size()) {
            merchantMenu.syncFromSlots(lastShopItem);
            syncTradesFromOffers();
        }
        return result;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        boolean r = super.mouseScrolled(mouseX, mouseY, delta);
        clampScrollOff();
        return r;
    }

    /**
     * 根因A：vanilla mouseScrolled 只在 canScroll(size)=size&gt;7 时才更新 scrollOff，
     * 条目删到 ≤7 后 scrollOff 永久冻结在旧值 → 点击按钮 shopItem=button.index+越界值
     * → selectionHint 越界 → sync skip 静默丢写 + 条目无法选中。
     * 钳到合法区间 [0, max(0, size-7)] 自纠。
     */
    private void clampScrollOff() {
        MerchantScreenAccessor acc = (MerchantScreenAccessor) this;
        int max = Math.max(0, merchant.getOffers().size() - 7);
        int so = acc.getScrollOff();
        if (so < 0 || so > max) {
            int fixed = Math.max(0, Math.min(so, max));
            acc.setScrollOff(fixed);
            // 233d-UI9 日志清理：println→debug（默认不输出，只留 error 级）
            org.slf4j.LoggerFactory.getLogger("cnpcplus").debug("[vt] clamp scrollOff -> {}", fixed);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        clampScrollOff();
        int offerCount = merchant.getOffers().size();
        int idx = getSelectedTradeIndex();
        if (idx >= offerCount) {
            ((MerchantScreenAccessor) this).setShopItem(offerCount > 0 ? 0 : -1);
        }
        boolean tempFix = false;
        if (idx < 0 && offerCount > 0) {
            ((MerchantScreenAccessor) this).setShopItem(0);
            tempFix = true;
        }
        super.render(graphics, mouseX, mouseY, partialTicks);
        if (tempFix) {
            ((MerchantScreenAccessor) this).setShopItem(-1);
        }
        int lx = this.leftPos - 100;
        // 按钮列最低到 topPos+125（仅名字 105~125），223b 移到其下方，不再与按钮重叠
        int ly = this.topPos + 127;
        if ((lastShopItem < 0 || lastShopItem >= trades.size()) && !trades.isEmpty()) {
            graphics.drawString(this.font, "点击左侧条目选中", lx, ly, 0x808080, false);
        } else if (lastShopItem >= 0 && lastShopItem < trades.size()) {
            TradeData t = trades.get(lastShopItem);
            if (t.limitEnabled && t.uses >= Math.max(1, t.limitCount) && t.limitResetAt > 0) {
                // 223a：耗尽倒计时（refreshInterval 单位=秒，onTradeConsumed 按 *1000 落时刻）
                long remainMs = t.limitResetAt - System.currentTimeMillis();
                if (remainMs > 0) {
                    graphics.drawString(this.font, "还有 " + ((remainMs + 999) / 1000) + " 秒刷新",
                        lx, ly, 0xFF5555, false);
                } else {
                    graphics.drawString(this.font, "刷新已就绪", lx, ly, 0x55FF55, false);
                }
            }
        }
    }
}
