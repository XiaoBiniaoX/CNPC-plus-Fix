package bin.cnpcplus.melee.client;

import bin.cnpcplus.melee.MeleeBuff;
import bin.cnpcplus.melee.MeleeBuffUtil;
import bin.cnpcplus.melee.MeleeBuffsAccess;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 近战附加 BUFF 的多选界面。
 *
 * <p>布局与交互按 1.12.2 已完成的实现对齐（哈基彬要求以 1.12.2 的 UI 为准）：
 * 左侧「可用效果」、右侧「已选效果」、中间三个搬运按钮，顶部等级与时长两个输入框。
 * 参照的是 CNPC 自己的药水效果职业界面 {@code GuiNpcHealer} 的双列表结构，
 * 但那个界面等级上限只有 3、且时长是硬编码的，所以只借布局、字段自己配齐。
 *
 * <p>用 {@code setUnsortedList} 而不是 {@code setList}：后者会强制排序，
 * 而右侧「已选」要保持插入顺序，左侧「可用」按注册表顺序遍历也不希望被二次打乱。
 *
 * <p>列表点击走 {@code ICustomScrollListener}（{@code GuiCustomScrollNop:318-320}），
 * 不走 {@code doubleClicked}，所以必须实现这个接口才收得到事件。
 * 单击「已选」条目会把它的等级/时长回填到输入框，双击则用输入框当前值覆盖它，
 * 这样改一个已有条目不必先删再加。双击左侧「可用」= 直接加入。
 *
 * <p>本界面只改内存里的 BUFF 表，不自己发包。落盘走父链的
 * {@code SPacketMenuSave(STATS, stats.save(tag))} 全量 NBT，
 * BUFF 表已由 {@code MixinDataMeleeBuffs} 挂进 {@code DataMelee} 的标签里。
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

    private final DataMelee stats;

    private GuiCustomScrollNop available;
    private GuiCustomScrollNop chosen;

    /** 显示名 → 注册名。列表给玩家看的是本地化名字，回写要用注册名。 */
    private final Map<String, String> availableNames = new LinkedHashMap<>();
    /** 「效果名 等级 (时长s)」→ 注册名。 */
    private final Map<String, String> chosenNames = new LinkedHashMap<>();

    private int amplifier = 0;
    private int seconds = 5;

    public SubGuiMeleeBuffs(DataMelee stats) {
        this.stats = stats;
        this.setBackground("menubg.png");
        this.imageWidth = 420;
        this.imageHeight = 240;
        this.closeOnEsc = true;
    }

    private List<MeleeBuff> buffs() {
        return ((MeleeBuffsAccess) this.stats).cnpcplus$getMeleeBuffs();
    }

    @Override
    public void init() {
        super.init();

        this.addLabel(new GuiLabel(0, I18n.get("cnpcplus.melee.buffs"),
                this.guiLeft + 4, this.guiTop + 6));

        // 等级：0 = I 级，与原版 MobEffectInstance 的 amplifier 语义一致。
        this.addLabel(new GuiLabel(1, I18n.get("cnpcplus.melee.amp"),
                this.guiLeft + 150, this.guiTop + 11));
        GuiTextFieldNop ampField = new GuiTextFieldNop(FIELD_AMP, this,
                this.guiLeft + 210, this.guiTop + 6, 40, 18, "" + this.amplifier);
        this.addTextField(ampField);
        ampField.numbersOnly = true;
        ampField.setMinMaxDefault(0, MeleeBuff.MAX_AMP, 0);

        // 时长单位是秒，与原版近战那个「时间」框一致（施加时才 ×20 变 tick）。
        this.addLabel(new GuiLabel(2, I18n.get("cnpcplus.melee.duration"),
                this.guiLeft + 262, this.guiTop + 11));
        GuiTextFieldNop timeField = new GuiTextFieldNop(FIELD_SECONDS, this,
                this.guiLeft + 310, this.guiTop + 6, 50, 18, "" + this.seconds);
        this.addTextField(timeField);
        timeField.numbersOnly = true;
        timeField.setMinMaxDefault(1, MeleeBuff.MAX_SECONDS, 5);

        if (this.available == null) {
            this.available = new GuiCustomScrollNop(this, SCROLL_AVAILABLE);
            this.available.setSize(150, 170);
        }
        this.available.guiLeft = this.guiLeft + 4;
        this.available.guiTop = this.guiTop + 40;
        this.addScroll(this.available);
        this.addLabel(new GuiLabel(11, I18n.get("cnpcplus.melee.availableBuffs"),
                this.guiLeft + 4, this.guiTop + 30));

        if (this.chosen == null) {
            this.chosen = new GuiCustomScrollNop(this, SCROLL_SELECTED);
            this.chosen.setSize(190, 170);
        }
        this.chosen.guiLeft = this.guiLeft + 226;
        this.chosen.guiTop = this.guiTop + 40;
        this.addScroll(this.chosen);
        this.addLabel(new GuiLabel(12, I18n.get("cnpcplus.melee.chosenBuffs"),
                this.guiLeft + 226, this.guiTop + 30));

        cnpcplus$rebuildLists();

        this.addButton(new GuiButtonNop((IGuiInterface) this, BTN_ADD,
                this.guiLeft + 158, this.guiTop + 60, 60, 20, ">"));
        this.addButton(new GuiButtonNop((IGuiInterface) this, BTN_REMOVE,
                this.guiLeft + 158, this.guiTop + 84, 60, 20, "<"));
        this.addButton(new GuiButtonNop((IGuiInterface) this, BTN_CLEAR,
                this.guiLeft + 158, this.guiTop + 112, 60, 20, "<<"));
        this.addButton(new GuiButtonNop((IGuiInterface) this, BTN_DONE,
                this.guiLeft + 330, this.guiTop + 216, 84, 20, "gui.done"));
    }

    /** 按当前 BUFF 表刷新两侧列表。已选的效果不再出现在左侧。 */
    private void cnpcplus$rebuildLists() {
        this.availableNames.clear();
        this.chosenNames.clear();

        List<MeleeBuff> current = buffs();
        List<String> chosenIds = new ArrayList<>();
        for (MeleeBuff b : current) {
            if (b != null && b.effectId != null) chosenIds.add(b.effectId);
        }

        List<String> availableList = new ArrayList<>();
        for (String id : MeleeBuffUtil.allEffectIds()) {
            if (chosenIds.contains(id)) continue;
            String display = cnpcplus$displayName(id);
            // 万一两个 mod 的效果本地化名撞了，用注册名兜底保证键唯一。
            if (this.availableNames.containsKey(display)) {
                display = display + " [" + id + "]";
            }
            this.availableNames.put(display, id);
            availableList.add(display);
        }
        this.available.setUnsortedList(availableList);

        List<String> chosenList = new ArrayList<>();
        for (MeleeBuff b : current) {
            if (b == null || b.effectId == null) continue;
            String display = cnpcplus$displayName(b.effectId)
                    + " " + I18n.get("enchantment.level." + (b.amp + 1))
                    + " (" + b.seconds + "s)";
            if (this.chosenNames.containsKey(display)) {
                display = display + " [" + b.effectId + "]";
            }
            this.chosenNames.put(display, b.effectId);
            chosenList.add(display);
        }
        this.chosen.setUnsortedList(chosenList);
    }

    /** 本地化显示名。没有翻译时 I18n 原样返回键，这时用注册名更可读。 */
    private static String cnpcplus$displayName(String id) {
        String key = MeleeBuffUtil.descriptionIdOf(id);
        if (key == null) return id;
        String name = I18n.get(key);
        return name.equals(key) ? id : name;
    }

    private MeleeBuff cnpcplus$find(String id) {
        for (MeleeBuff b : buffs()) {
            if (b != null && id.equals(b.effectId)) return b;
        }
        return null;
    }

    /** 加入或覆盖一条。同一效果只保留一条，覆盖时保持原位置。 */
    private void cnpcplus$put(String id) {
        List<MeleeBuff> list = buffs();
        for (int i = 0; i < list.size(); i++) {
            MeleeBuff b = list.get(i);
            if (b != null && id.equals(b.effectId)) {
                list.set(i, new MeleeBuff(id, this.amplifier, this.seconds));
                return;
            }
        }
        if (list.size() >= MeleeBuff.MAX_ENTRIES) return;
        list.add(new MeleeBuff(id, this.amplifier, this.seconds));
    }

    private void cnpcplus$remove(String id) {
        List<MeleeBuff> list = buffs();
        for (int i = 0; i < list.size(); i++) {
            MeleeBuff b = list.get(i);
            if (b != null && id.equals(b.effectId)) {
                list.remove(i);
                return;
            }
        }
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button == null) return;
        // 点按钮前先把输入框的值收进来，否则玩家改完等级直接点 ">" 会用到旧值。
        cnpcplus$readFields();

        if (button.id == BTN_ADD) {
            if (this.available.hasSelected()) {
                String id = this.availableNames.get(this.available.getSelected());
                if (id != null) cnpcplus$put(id);
            }
            cnpcplus$refresh();
        } else if (button.id == BTN_REMOVE) {
            if (this.chosen.hasSelected()) {
                String id = this.chosenNames.get(this.chosen.getSelected());
                if (id != null) cnpcplus$remove(id);
            }
            cnpcplus$refresh();
        } else if (button.id == BTN_CLEAR) {
            buffs().clear();
            cnpcplus$refresh();
        } else if (button.id == BTN_DONE) {
            this.close();
        }
    }

    private void cnpcplus$refresh() {
        this.available.clearSelection();
        this.chosen.clearSelection();
        this.init();
    }

    /**
     * 单击「已选」条目：把它的等级/时长回填到输入框，方便查看和微调。
     */
    @Override
    public void scrollClicked(double mouseX, double mouseY, int button, GuiCustomScrollNop scroll) {
        if (scroll != this.chosen || !this.chosen.hasSelected()) return;
        String id = this.chosenNames.get(this.chosen.getSelected());
        if (id == null) return;
        MeleeBuff buff = cnpcplus$find(id);
        if (buff == null) return;
        this.amplifier = buff.amp;
        this.seconds = buff.seconds;
        GuiTextFieldNop amp = getTextField(FIELD_AMP);
        if (amp != null) amp.setValue(String.valueOf(this.amplifier));
        GuiTextFieldNop time = getTextField(FIELD_SECONDS);
        if (time != null) time.setValue(String.valueOf(this.seconds));
    }

    /**
     * 双击左侧 = 直接加入（省一次点 ">"）；双击右侧 = 用当前输入框的值覆盖该条。
     */
    @Override
    public void scrollDoubleClicked(String selection, GuiCustomScrollNop scroll) {
        if (selection == null) return;
        cnpcplus$readFields();

        if (scroll == this.available) {
            String id = this.availableNames.get(selection);
            if (id == null) return;
            cnpcplus$put(id);
            cnpcplus$refresh();
            return;
        }
        if (scroll != this.chosen) return;
        String id = this.chosenNames.get(selection);
        if (id == null) return;
        cnpcplus$put(id);
        cnpcplus$refresh();
    }

    private void cnpcplus$readFields() {
        GuiTextFieldNop amp = getTextField(FIELD_AMP);
        if (amp != null) {
            this.amplifier = MeleeBuff.clampAmp(amp.getInteger());
        }
        GuiTextFieldNop time = getTextField(FIELD_SECONDS);
        if (time != null) {
            this.seconds = MeleeBuff.clampSeconds(time.getInteger());
        }
    }

    @Override
    public void unFocused(GuiTextFieldNop textfield) {
        if (textfield == null) return;
        if (textfield.id == FIELD_AMP) {
            this.amplifier = MeleeBuff.clampAmp(textfield.getInteger());
        } else if (textfield.id == FIELD_SECONDS) {
            this.seconds = MeleeBuff.clampSeconds(textfield.getInteger());
        }
    }

    /** 本界面不自己发包，落盘走父链的 STATS 全量 NBT。详见类注释。 */
    @Override
    public void save() {
        cnpcplus$readFields();
    }

    @Override
    public void elementClicked() {
    }

    @Override
    public Screen getSubGui() {
        return super.getSubGui();
    }
}
