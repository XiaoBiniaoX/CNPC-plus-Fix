package bin.cnpcplus.mixin.lines;

import bin.cnpcplus.lines.MeleeLineStore;
import net.minecraft.client.gui.GuiButton;
import noppes.npcs.client.NoppesUtil;
import noppes.npcs.client.gui.GuiNPCLinesEdit;
import noppes.npcs.client.gui.advanced.GuiNPCLinesMenu;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.controllers.data.Lines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 台词菜单里新增「近战打击」入口，与「交互」「击杀」等同一界面同一交互方式。
 *
 * 原版按钮布局（全部 x = guiLeft + 85，y 每级 +23）：
 *   id 0  y+20  lines.world        世界台词
 *   id 1  y+43  lines.attack       攻击台词（锁定目标时播，不是命中时）
 *   id 2  y+66  lines.interact     交互台词
 *   id 5  y+89  lines.killed       被击杀台词
 *   id 6  y+112 lines.kill         击杀台词
 *   id 7  y+135 lines.npcinteract  NPC 互聊
 *   id 16 y+152 lines.random       随机 / 顺序开关（标签在 y+157）
 *
 * 第一列 y=152 已被随机开关占用，父类 ySize=200 也没有余量，
 * 因此新按钮放到右侧第二列 guiLeft+240 的首行，与第一列首行对齐。
 * 这样不动任何原版控件的位置，也不会与随机开关重叠。
 *
 * 复用原生 {@code GuiNPCLinesEdit} —— 它是**分类无关**的，只吃一个 Lines
 * 引用（8 条上限、文本框 + 音效框 + 选择按钮），所以编辑界面与交互方式
 * 与其余台词完全一致，无需另写 GUI。
 *
 * 保存也不用改：原版 save() 发的是 {@code npc.advanced.writeToNBT(...)} 全量
 * NBT，近战台词已由 MixinDataAdvancedMeleeLines 挂进该 NBT。
 *
 * 按钮 id 取 8：原版已用 0/1/2/5/6/7/16，8 未被占用，且原版
 * {@code actionPerformed} 对未知 id 不做任何处理（一串独立 if，末尾
 * {@code if (id != 16) return;}），所以不会误触发。
 */
@Mixin(value = GuiNPCLinesMenu.class, remap = false)
public class MixinGuiNPCLinesMenuMelee {

    /** 新按钮 id，避开原版已用的 0/1/2/5/6/7/16。 */
    private static final int CNPCPLUS_MELEE_BUTTON = 8;

    @Inject(method = "func_73866_w_", at = @At("TAIL"), remap = false, require = 1)
    private void cnpcplus$addMeleeButton(CallbackInfo ci) {
        GuiNPCLinesMenu self = (GuiNPCLinesMenu) (Object) this;
        // 位置修正（哈基彬实测：原先左边压住第一列按钮、右边溢出 UI）。
        //
        // 原因：GuiNpcButton 的 4 参构造 (id,x,y,text) 直接走原版 GuiButton，
        // 宽度是原版默认 200。原版第一列按钮在 guiLeft+85，宽 200，
        // 因此占据 x = 85..285；我原先放在 guiLeft+240 就落在这段里，
        // 且 240+200=440 已超出父类 xSize=420。
        //
        // 改用 5 参构造显式给宽高，并与第一列右端（85+200=285）留出间距：
        // x = guiLeft+290、宽 120 → 占 290..410，正好落在 420 宽度内不溢出。
        self.addButton(new GuiNpcButton(CNPCPLUS_MELEE_BUTTON,
                self.guiLeft + 290, self.guiTop + 20, 120, 20, "cnpcplus.lines.melee"));
    }

    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void cnpcplus$openMeleeLines(GuiButton button, CallbackInfo ci) {
        if (button == null || button.id != CNPCPLUS_MELEE_BUTTON) {
            return;
        }
        GuiNPCLinesMenu self = (GuiNPCLinesMenu) (Object) this;
        Lines lines = MeleeLineStore.get(self.npc.advanced);
        if (lines == null) {
            return;
        }
        NoppesUtil.openGUI(self.player, new GuiNPCLinesEdit(self.npc, lines));
        ci.cancel();
    }
}
