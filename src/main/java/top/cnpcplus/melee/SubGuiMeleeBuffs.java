package top.cnpcplus.melee;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import noppes.npcs.entity.data.DataMelee;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ICustomScrollListener;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 近战附加 BUFF 的多选界面（哈基彬需求 A3）。
 *
 * <h3>布局参照</h3>
 * 结构照 CNPC 自己的「药水效果职业」界面 {@code GuiNpcHealer}（反编译 68-120 行）：
 * 左「可用效果」、右「已选效果」、中间搬运按钮。
 * 但那个界面**没有时长控件**（{@code JobHealer.java:99} 把时长硬编码成 100 ticks）
 * 且等级上限只有 3，所以只借布局，字段自己配齐。
 *
 * <p>具体坐标沿用哈基彬在 1.12.2 上定稿的那一套（420×240）。
 *
 * <h3>效果来源</h3>
 * {@code BuiltInRegistries.MOB_EFFECT} 全注册表，所以 mods 加的效果自动出现，
 * 这正是需求里「可以获取到在 Minecraft 注册的所有药水效果，包括 mods」。
 * 存注册名字符串而不是数字 id，理由见 {@link MeleeBuffStore} 类注释。
 *
 * <h3>为什么继承 GuiBasic</h3>
 * 1.20.1 的 CNPC 没有 {@code SubGuiInterface} 这个类，子界面基类就是 {@code GuiBasic}
 * （{@code SubGuiNpcMeleeProperties} 自己也是它的子类）。
 * 由父界面用 {@code setSubGui} 打开，关闭时 {@code GuiWrapper.close()}（反编译 236-253 行）
 * 会先调本界面的 {@code save()}，再调父界面的 {@code subGuiClosed} + {@code initGui()}，
 * 于是父界面按钮上的「已选 N 个」文字自动刷新，不需要自己管返回逻辑。
 *
 * <h3>数据落盘路径</h3>
 * 本界面只改内存里的 {@link MeleeBuffStore}。真正落盘发生在玩家关闭 NPC 属性主界面时的
 * {@code SPacketMenuSave(STATS, stats.save(...))}（{@code GuiNpcStats.java:175}），
 * 而 BUFF 表已由 {@code MixinDataMeleeBuffNBT} 挂进 {@code DataMelee} 的标签里，
 * 所以随之一起过河、一起进存档，无需新增任何网络包。
 */
public class SubGuiMeleeBuffs extends GuiBasic
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

    private GuiCustomScrollNop available;
    private GuiCustomScrollNop selected;

    /** 显示名 → 注册名。列表里给玩家看的是本地化名字，回写要用注册名。 */
    private final Map<String, String> availableNames = new HashMap<>();
    /** 「效果名 等级 (时长s)」→ 注册名。 */
    private final Map<String, String> selectedNames = new HashMap<>();

    private int amplifier = 0;
    private int seconds = 5;

    public SubGuiMeleeBuffs(DataMelee melee) {
        this.melee = melee;
        this.setBackground("menubg.png");
        this.imageWidth = 420;
        this.imageHeight = 240;
        this.closeOnEsc = true;
    }

    @Override
    public void m_7856_() {
        super.m_7856_();

        this.addLabel(new GuiLabel(0, "cnpcplus.melee.buffs",
                this.guiLeft + 4, this.guiTop + 6));

        // 等级：0 = I 级，与原版 MobEffectInstance 的 amplifier 语义一致。
        this.addLabel(new GuiLabel(1, "stats.amplify", this.guiLeft + 150, this.guiTop + 11));
        this.addTextField(new GuiTextFieldNop(FIELD_AMP, this,
                this.guiLeft + 210, this.guiTop + 6, 40, 18, this.amplifier + ""));
        this.getTextField(FIELD_AMP).numbersOnly = true;
        this.getTextField(FIELD_AMP).setMinMaxDefault(0, MeleeBuffStore.MAX_AMPLIFIER, 0);

        // 时长单位是秒，与原版近战那个「时间」框一致（施加时才 ×20 变 ticks）。
        this.addLabel(new GuiLabel(2, "gui.time", this.guiLeft + 262, this.guiTop + 11));
        this.addTextField(new GuiTextFieldNop(FIELD_SECONDS, this,
                this.guiLeft + 310, this.guiTop + 6, 50, 18, this.seconds + ""));
        this.getTextField(FIELD_SECONDS).numbersOnly = true;
        this.getTextField(FIELD_SECONDS).setMinMaxDefault(1, MeleeBuffStore.MAX_SECONDS, 5);

        if (this.available == null) {
            this.available = new GuiCustomScrollNop(this, SCROLL_AVAILABLE);
            this.available.setSize(150, 170);
        }
        this.available.guiLeft = this.guiLeft + 4;
        this.available.guiTop = this.guiTop + 40;
        this.addScroll(this.available);
        this.addLabel(new GuiLabel(11, "beacon.availableEffects",
                this.guiLeft + 4, this.guiTop + 30));

        if (this.selected == null) {
            this.selected = new GuiCustomScrollNop(this, SCROLL_SELECTED);
            this.selected.setSize(190, 170);
        }
        this.selected.guiLeft = this.guiLeft + 226;
        this.selected.guiTop = this.guiTop + 40;
        this.addScroll(this.selected);
        this.addLabel(new GuiLabel(12, "beacon.currentEffects",
                this.guiLeft + 226, this.guiTop + 30));

        cnpcplus$rebuildLists();

        this.addButton(new GuiButtonNop(this, BTN_ADD, this.guiLeft + 158, this.guiTop + 60, 60, 20, ">"));
        this.addButton(new GuiButtonNop(this, BTN_REMOVE, this.guiLeft + 158, this.guiTop + 84, 60, 20, "<"));
        this.addButton(new GuiButtonNop(this, BTN_CLEAR, this.guiLeft + 158, this.guiTop + 112, 60, 20, "<<"));
        this.addButton(new GuiButtonNop(this, BTN_DONE, this.guiLeft + 330, this.guiTop + 216, 84, 20, "gui.done"));
    }

    /**
     * 重建两个列表。
     *
     * <p>用 {@code setUnsortedList} 而不是 {@code setList}：后者会按
     * {@code NaturalOrderComparator} 强制排序，而右侧「已选」希望保持插入顺序
     * （{@link MeleeBuffStore} 用的是 LinkedHashMap）。左侧「可用」按注册表顺序遍历，
     * 也不希望被二次打乱。
     *
     * <p>注意列表项**不能**直接放 lang 键：{@code GuiCustomScrollNop.drawItems}
     * （反编译 224 行）会对每一项做 {@code I18n.get(...)}。我们塞的是已经翻译好的
     * 显示名，翻译表里查不到就原样返回，正好是想要的效果。
     */
    private void cnpcplus$rebuildLists() {
        this.availableNames.clear();
        this.selectedNames.clear();

        Map<String, int[]> chosen = MeleeBuffStore.get(this.melee);

        List<String> availableList = new ArrayList<>();
        for (MobEffect effect : BuiltInRegistries.MOB_EFFECT) {
            String registry = MeleeBuffStore.nameOf(effect);
            if (registry == null) continue;
            if (chosen.containsKey(registry)) continue;
            String display = cnpcplus$displayName(effect, registry);
            // 万一两个 mod 的效果本地化名撞了，用注册名兜底保证键唯一。
            if (this.availableNames.containsKey(display)) {
                display = display + " [" + registry + "]";
            }
            this.availableNames.put(display, registry);
            availableList.add(display);
        }
        this.available.setUnsortedList(availableList);

        List<String> selectedList = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : chosen.entrySet()) {
            MobEffect effect = MeleeBuffStore.resolve(entry.getKey());
            String base = effect != null
                    ? cnpcplus$displayName(effect, entry.getKey())
                    // 对应 mod 已被移除：仍然显示出来，让玩家能手动删掉。
                    : entry.getKey();
            String display = base
                    + " " + I18n.get("enchantment.level." + (entry.getValue()[0] + 1))
                    + " (" + entry.getValue()[1] + "s)";
            if (this.selectedNames.containsKey(display)) {
                display = display + " [" + entry.getKey() + "]";
            }
            this.selectedNames.put(display, entry.getKey());
            selectedList.add(display);
        }
        this.selected.setUnsortedList(selectedList);
    }

    private String cnpcplus$displayName(MobEffect effect, String registry) {
        // MobEffect.getDisplayName() 直接返回已翻译的 Component（1.20.1 有这个方法，
        // 1.12.2 那边要手动 I18n.format(getName())）。
        String name = effect.getDisplayName().getString();
        if (name == null || name.isEmpty()) return registry;
        // 没有翻译时 Component 会原样吐出 descriptionId，这时用注册名更可读。
        return name.equals(effect.getDescriptionId()) ? registry : name;
    }

    @Override
    public void unFocused(GuiTextFieldNop textfield) {
        // 1.20.1 的 GuiTextFieldNop.id 是 public，可以直接读（1.12.2 那边是 private，
        // 只能用对象身份比较）。
        if (textfield.id == FIELD_AMP) {
            this.amplifier = MeleeBuffStore.clampAmplifier(textfield.getInteger());
        } else if (textfield.id == FIELD_SECONDS) {
            this.seconds = MeleeBuffStore.clampSeconds(textfield.getInteger());
        }
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        // 点按钮前先把输入框里的值收进来，否则玩家改完等级直接点 ">" 会用到旧值。
        cnpcplus$readFields();

        if (button.id == BTN_ADD) {
            if (this.available.hasSelected()) {
                String registry = this.availableNames.get(this.available.getSelected());
                if (registry != null) {
                    MeleeBuffStore.put(this.melee, registry, this.amplifier, this.seconds);
                }
            }
            cnpcplus$clearSelections();
            this.m_7856_();
        } else if (button.id == BTN_REMOVE) {
            if (this.selected.hasSelected()) {
                String registry = this.selectedNames.get(this.selected.getSelected());
                if (registry != null) {
                    MeleeBuffStore.remove(this.melee, registry);
                }
            }
            cnpcplus$clearSelections();
            this.m_7856_();
        } else if (button.id == BTN_CLEAR) {
            MeleeBuffStore.clear(this.melee);
            cnpcplus$clearSelections();
            this.m_7856_();
        } else if (button.id == BTN_DONE) {
            this.close();
        }
    }

    /**
     * 清掉两个列表的选中态。
     *
     * <p>**不能用 {@code clearSelection()}**：1.20.1 的那个方法会
     * {@code this.list = new ArrayList<>()}（反编译 408-411 行），
     * 连列表内容一起清空 —— 与方法名完全不符。1.12.2 那边是直接写
     * {@code scroll.selected = -1} 的 public 字段，这版字段是 private，
     * 所以用 {@code setSelectedIndex(-1)}。
     */
    private void cnpcplus$clearSelections() {
        if (this.available != null) this.available.setSelectedIndex(-1);
        if (this.selected != null) this.selected.setSelectedIndex(-1);
    }

    /**
     * 单击列表：把选中项的当前值回填到输入框，方便查看/微调。
     *
     * <p>{@code GuiCustomScrollNop} 的点击走
     * {@code ICustomScrollListener.scrollClicked / scrollDoubleClicked}
     * （反编译 316-323 行），构造时若 parent 实现了该接口会自动挂上（{@code :81-83}）。
     */
    @Override
    public void scrollClicked(double mouseX, double mouseY, int button, GuiCustomScrollNop scroll) {
        if (scroll != this.selected || !this.selected.hasSelected()) return;
        String registry = this.selectedNames.get(this.selected.getSelected());
        if (registry == null) return;
        int[] value = MeleeBuffStore.get(this.melee).get(registry);
        if (value == null) return;
        this.amplifier = value[0];
        this.seconds = value[1];
        GuiTextFieldNop amp = this.getTextField(FIELD_AMP);
        if (amp != null) amp.setValue(String.valueOf(this.amplifier));
        GuiTextFieldNop time = this.getTextField(FIELD_SECONDS);
        if (time != null) time.setValue(String.valueOf(this.seconds));
    }

    /**
     * 双击左侧 = 直接加入；双击右侧 = 用当前输入框的等级/时长覆盖该条目。
     *
     * <p>这样改一个已有条目不必先删再加。原版 {@code GuiNpcHealer} 没有这个便利，
     * 但它也不支持时长，条目改动成本低；这里两个参数都要调，值得加。
     */
    @Override
    public void scrollDoubleClicked(String selection, GuiCustomScrollNop scroll) {
        cnpcplus$readFields();
        if (scroll == this.available) {
            String registry = this.availableNames.get(selection);
            if (registry == null) return;
            MeleeBuffStore.put(this.melee, registry, this.amplifier, this.seconds);
            cnpcplus$clearSelections();
            this.m_7856_();
            return;
        }
        if (scroll != this.selected) return;
        String registry = this.selectedNames.get(selection);
        if (registry == null) return;
        MeleeBuffStore.put(this.melee, registry, this.amplifier, this.seconds);
        this.m_7856_();
    }

    private void cnpcplus$readFields() {
        GuiTextFieldNop amp = this.getTextField(FIELD_AMP);
        if (amp != null && amp.isInteger()) {
            this.amplifier = MeleeBuffStore.clampAmplifier(amp.getInteger());
        }
        GuiTextFieldNop time = this.getTextField(FIELD_SECONDS);
        if (time != null && time.isInteger()) {
            this.seconds = MeleeBuffStore.clampSeconds(time.getInteger());
        }
    }

    /**
     * 本界面不自己发包，数据落盘走父链的 STATS 菜单包（详见类注释）。
     */
    @Override
    public void save() {
        cnpcplus$readFields();
    }
}
