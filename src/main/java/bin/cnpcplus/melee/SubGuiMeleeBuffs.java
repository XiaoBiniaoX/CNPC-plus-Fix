package bin.cnpcplus.melee;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.Potion;
import noppes.npcs.client.gui.util.GuiCustomScroll;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.client.gui.util.ICustomScrollListener;
import noppes.npcs.client.gui.util.ITextfieldListener;
import noppes.npcs.client.gui.util.SubGuiInterface;
import noppes.npcs.entity.data.DataMelee;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 近战附加 BUFF 的多选界面（哈基彬需求 B-3）。
 *
 * <h3>布局参照</h3>
 * 抄的是 CNPC 自己的「药水效果职业」界面 {@code GuiNpcHealer} 的双列表结构：
 * 左边「可用效果」、右边「已选效果」、中间四个搬运按钮。
 * 但那个界面的等级上限是 3、而且**没有时长控件**（{@code JobHealer.java:99}
 * 把时长硬编码成 100 ticks），所以只能借布局，字段要自己配齐。
 *
 * <h3>效果来源</h3>
 * {@code Potion.REGISTRY} 全注册表，所以 mods 加的效果自动出现，
 * 这正是需求里「可以获取到在 Minecraft 注册的所有药水效果，包括 mods」。
 * 存的是注册名字符串而不是数字 id，理由见 {@link MeleeBuffStore} 类注释。
 *
 * <h3>为什么是 SubGuiInterface 而不是独立 GuiScreen</h3>
 * 它由 {@code SubGuiNpcMeleeProperties}（本身也是 SubGui）用
 * {@code setSubGui} 打开，关闭时原版 {@code close()} 链会自动回到父界面并
 * 触发父界面的 {@code subGuiClosed}，父界面再重建自己的控件，
 * 按钮上的「已选 N 个」文字就自动刷新了。不需要自己管返回逻辑。
 *
 * <h3>数据落盘路径</h3>
 * 本界面只改内存里的 {@link MeleeBuffStore}。真正的落盘发生在
 * 玩家关闭 NPC 属性主界面时的 {@code MainmenuStatsSave} —— 那条包发送的是
 * {@code npc.stats.writeToNBT(...)} 全量 NBT，而 BUFF 表已经被
 * {@code MixinDataMeleeBuffNBT} 挂进 {@code DataMelee} 的复合标签里，
 * 所以随之一起过河、一起进存档，无需新增任何网络包。
 */
public class SubGuiMeleeBuffs extends SubGuiInterface
        implements ITextfieldListener, ICustomScrollListener {

    private static final int SCROLL_AVAILABLE = 0;
    private static final int SCROLL_SELECTED = 1;

    private static final int FIELD_AMP = 2;
    private static final int FIELD_SECONDS = 3;

    private static final int BTN_ADD = 11;
    private static final int BTN_REMOVE = 12;
    private static final int BTN_CLEAR = 14;
    private static final int BTN_DONE = 66;

    private final DataMelee melee;

    private GuiCustomScroll available;
    private GuiCustomScroll selected;

    /** 显示名 → 注册名。列表里给玩家看的是本地化名字，回写要用注册名。 */
    private final Map<String, String> availableNames = new HashMap<String, String>();
    /** 「效果名 等级 (时长s)」→ 注册名。 */
    private final Map<String, String> selectedNames = new HashMap<String, String>();

    private int amplifier = 0;
    private int seconds = 5;

    public SubGuiMeleeBuffs(DataMelee melee) {
        this.melee = melee;
        this.setBackground("menubg.png");
        this.xSize = 420;
        this.ySize = 240;
        this.closeOnEsc = true;
    }

    @Override
    public void initGui() {
        super.initGui();

        this.addLabel(new GuiNpcLabel(0, "cnpcplus.melee.buffs",
                this.guiLeft + 4, this.guiTop + 6));

        // 等级：0 = I 级，与原版 PotionEffect 的 amplifier 语义一致。
        this.addLabel(new GuiNpcLabel(1, "stats.amplify", this.guiLeft + 150, this.guiTop + 11));
        this.addTextField(new GuiNpcTextField(FIELD_AMP, this, this.fontRenderer,
                this.guiLeft + 210, this.guiTop + 6, 40, 18, this.amplifier + ""));
        this.getTextField(FIELD_AMP).numbersOnly = true;
        this.getTextField(FIELD_AMP).setMinMaxDefault(0, MeleeBuffStore.MAX_AMPLIFIER, 0);

        // 时长单位是秒，与原版近战那个「时间」框一致（施加时才 ×20 变 ticks）。
        this.addLabel(new GuiNpcLabel(2, "gui.time", this.guiLeft + 262, this.guiTop + 11));
        this.addTextField(new GuiNpcTextField(FIELD_SECONDS, this, this.fontRenderer,
                this.guiLeft + 310, this.guiTop + 6, 50, 18, this.seconds + ""));
        this.getTextField(FIELD_SECONDS).numbersOnly = true;
        this.getTextField(FIELD_SECONDS).setMinMaxDefault(1, MeleeBuffStore.MAX_SECONDS, 5);

        if (this.available == null) {
            this.available = new GuiCustomScroll(this, SCROLL_AVAILABLE);
            this.available.setSize(150, 170);
        }
        this.available.guiLeft = this.guiLeft + 4;
        this.available.guiTop = this.guiTop + 40;
        this.addScroll(this.available);
        this.addLabel(new GuiNpcLabel(11, "beacon.availableEffects",
                this.guiLeft + 4, this.guiTop + 30));

        if (this.selected == null) {
            this.selected = new GuiCustomScroll(this, SCROLL_SELECTED);
            this.selected.setSize(190, 170);
        }
        this.selected.guiLeft = this.guiLeft + 226;
        this.selected.guiTop = this.guiTop + 40;
        this.addScroll(this.selected);
        this.addLabel(new GuiNpcLabel(12, "beacon.currentEffects",
                this.guiLeft + 226, this.guiTop + 30));

        this.cnpcplus$rebuildLists();

        this.addButton(new GuiNpcButton(BTN_ADD, this.guiLeft + 158, this.guiTop + 60, 60, 20, ">"));
        this.addButton(new GuiNpcButton(BTN_REMOVE, this.guiLeft + 158, this.guiTop + 84, 60, 20, "<"));
        this.addButton(new GuiNpcButton(BTN_CLEAR, this.guiLeft + 158, this.guiTop + 112, 60, 20, "<<"));
        this.addButton(new GuiNpcButton(BTN_DONE, this.guiLeft + 330, this.guiTop + 216, 84, 20, "gui.done"));
    }

    /**
     * 重建两个列表。
     *
     * 用 {@code setUnsortedList} 而不是 {@code setList}：后者会强制排序，
     * 而右侧「已选」希望保持插入顺序（{@link MeleeBuffStore} 用的是 LinkedHashMap）。
     * 左侧「可用」本身按注册表顺序遍历，也不希望被二次打乱。
     */
    private void cnpcplus$rebuildLists() {
        this.availableNames.clear();
        this.selectedNames.clear();

        Map<String, int[]> chosen = MeleeBuffStore.get(this.melee);

        List<String> availableList = new ArrayList<String>();
        for (Potion potion : Potion.REGISTRY) {
            String registry = MeleeBuffStore.nameOf(potion);
            if (registry == null) continue;
            if (chosen.containsKey(registry)) continue;
            String display = cnpcplus$displayName(potion, registry);
            // 万一两个 mod 的效果本地化名撞了，用注册名兜底保证键唯一。
            if (this.availableNames.containsKey(display)) {
                display = display + " [" + registry + "]";
            }
            this.availableNames.put(display, registry);
            availableList.add(display);
        }
        this.available.setUnsortedList(availableList);

        List<String> selectedList = new ArrayList<String>();
        for (Map.Entry<String, int[]> entry : chosen.entrySet()) {
            Potion potion = MeleeBuffStore.resolve(entry.getKey());
            String base = potion != null
                    ? cnpcplus$displayName(potion, entry.getKey())
                    // 对应 mod 已被移除：仍然显示出来，让玩家能手动删掉。
                    : entry.getKey();
            String display = base
                    + " " + I18n.format("enchantment.level." + (entry.getValue()[0] + 1))
                    + " (" + entry.getValue()[1] + "s)";
            if (this.selectedNames.containsKey(display)) {
                display = display + " [" + entry.getKey() + "]";
            }
            this.selectedNames.put(display, entry.getKey());
            selectedList.add(display);
        }
        this.selected.setUnsortedList(selectedList);
    }

    private String cnpcplus$displayName(Potion potion, String registry) {
        String key = potion.getName();
        String name = I18n.format(key);
        // 没有翻译时 I18n 原样返回 key，这时用注册名更可读。
        return name.equals(key) ? registry : name;
    }

    /**
     * 注意：1.12.2 的 {@code GuiTextField.id} 是 private，拿不到。
     * 所以用「对象身份比较」而不是读 id —— 这是本项目已有的既定做法
     * （阶段 4 就踩过，见 {@code MixinGuiNpcTraderSetupPages} 的同款写法）。
     */
    @Override
    public void unFocused(GuiNpcTextField textfield) {
        if (this.getTextField(FIELD_AMP) == textfield) {
            this.amplifier = MeleeBuffStore.clampAmplifier(textfield.getInteger());
        } else if (this.getTextField(FIELD_SECONDS) == textfield) {
            this.seconds = MeleeBuffStore.clampSeconds(textfield.getInteger());
        }
    }

    @Override
    protected void actionPerformed(GuiButton guibutton) {
        // 点按钮前先把输入框里的值收进来，否则玩家改完等级直接点 ">" 会用到旧值。
        this.cnpcplus$readFields();

        int id = guibutton.id;
        if (id == BTN_ADD) {
            if (this.available.hasSelected()) {
                String registry = this.availableNames.get(this.available.getSelected());
                if (registry != null) {
                    MeleeBuffStore.put(this.melee, registry, this.amplifier, this.seconds);
                }
            }
            this.available.selected = -1;
            this.selected.selected = -1;
            this.initGui();
        } else if (id == BTN_REMOVE) {
            if (this.selected.hasSelected()) {
                String registry = this.selectedNames.get(this.selected.getSelected());
                if (registry != null) {
                    MeleeBuffStore.remove(this.melee, registry);
                }
            }
            this.available.selected = -1;
            this.selected.selected = -1;
            this.initGui();
        } else if (id == BTN_CLEAR) {
            MeleeBuffStore.clear(this.melee);
            this.available.selected = -1;
            this.selected.selected = -1;
            this.initGui();
        } else if (id == BTN_DONE) {
            this.close();
        }
    }

    /**
     * 单击列表：把选中项的当前值回填到输入框，方便查看/微调。
     *
     * 注意 {@code GuiCustomScroll} 的点击**不走** {@code GuiNPCInterface.doubleClicked()}
     * （那个只被 {@code GuiNPCStringSlot} 调用），而是走
     * {@code ICustomScrollListener.scrollClicked / scrollDoubleClicked}
     * （{@code GuiCustomScroll.java:221-225}）。所以必须实现这个接口，
     * 光重写 doubleClicked 是收不到事件的。
     */
    @Override
    public void scrollClicked(int mouseX, int mouseY, int button, GuiCustomScroll scroll) {
        if (scroll != this.selected || !this.selected.hasSelected()) return;
        String registry = this.selectedNames.get(this.selected.getSelected());
        if (registry == null) return;
        int[] value = MeleeBuffStore.get(this.melee).get(registry);
        if (value == null) return;
        // 回填到输入框，让玩家看到这一项现在是什么等级/时长。
        this.amplifier = value[0];
        this.seconds = value[1];
        GuiNpcTextField amp = this.getTextField(FIELD_AMP);
        if (amp != null) amp.setText(String.valueOf(this.amplifier));
        GuiNpcTextField time = this.getTextField(FIELD_SECONDS);
        if (time != null) time.setText(String.valueOf(this.seconds));
    }

    /**
     * 双击「已选」列表里的条目 = 用当前输入框的等级/时长覆盖它。
     *
     * 这样改一个已有条目不必先删再加。原版 {@code GuiNpcHealer} 没有这个便利，
     * 但它也不支持时长，条目改动成本低；这里两个参数都要调，值得加。
     */
    @Override
    public void scrollDoubleClicked(String selection, GuiCustomScroll scroll) {
        if (scroll == this.available) {
            // 双击左侧 = 直接加进来，省一次点 ">"。
            String registry = this.availableNames.get(selection);
            if (registry == null) return;
            this.cnpcplus$readFields();
            MeleeBuffStore.put(this.melee, registry, this.amplifier, this.seconds);
            this.available.selected = -1;
            this.selected.selected = -1;
            this.initGui();
            return;
        }
        if (scroll != this.selected) return;
        String registry = this.selectedNames.get(selection);
        if (registry == null) return;
        this.cnpcplus$readFields();
        MeleeBuffStore.put(this.melee, registry, this.amplifier, this.seconds);
        this.initGui();
    }

    private void cnpcplus$readFields() {
        GuiNpcTextField amp = this.getTextField(FIELD_AMP);
        if (amp != null && amp.isInteger()) {
            this.amplifier = MeleeBuffStore.clampAmplifier(amp.getInteger());
        }
        GuiNpcTextField time = this.getTextField(FIELD_SECONDS);
        if (time != null && time.isInteger()) {
            this.seconds = MeleeBuffStore.clampSeconds(time.getInteger());
        }
    }

    /**
     * 本界面不自己发包。
     *
     * 数据落盘走父链的 {@code MainmenuStatsSave}（全量 DataStats NBT），
     * BUFF 表已由 {@code MixinDataMeleeBuffNBT} 挂进 DataMelee 的标签里。
     * 详见类注释。
     */
    @Override
    public void save() {
        this.cnpcplus$readFields();
    }
}
