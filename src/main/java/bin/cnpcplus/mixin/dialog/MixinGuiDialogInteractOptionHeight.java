package bin.cnpcplus.mixin.dialog;

import noppes.npcs.client.gui.player.GuiDialogInteract;
import noppes.npcs.controllers.data.Dialog;
import noppes.npcs.controllers.data.DialogOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;
import java.util.List;

/**
 * 修复：删除（禁用）对话选项后，玩家侧对话界面的文本区位置与选项占位收不回。
 *
 * 现象（哈基彬原话）：不开轮盘时新增对话选项会把文本界面往上顶；但撤回选项、
 * 删掉旧选项后重开该 NPC 的对话界面，文本区仍停在上面，被删掉的选项虽然不
 * 显示，占位却依旧被拉大。
 *
 * 根因（证据）：
 *  - {@code GuiDialogInteract.calculateRowHeight()} 用的是
 *    {@code this.dialog.options.size()}，即 Map 里的**全部**条目：
 *      dialogHeight = ySize - 3*fh - 4;
 *      if (size > 3) dialogHeight -= (size - 3) * fh;
 *  - 但真正渲染的行数取的是已过滤的 {@code this.options} 列表，
 *    过滤条件是 {@code option != null && option.isAvailable(player)}。
 *    两个计数不一致，差额就是那块收不回的空白。
 *  - CNPC **从不 remove 选项**（全反编译目录 options.remove/clear 零命中）。
 *    编辑器里点一下空槽就会 new 一个 DialogOption 放进 map，界面也没有删除
 *    按钮，「撤回」只能把 optionType 改成 2（disabled）。
 *  - 而 {@code Dialog.writeToNBTPartial} 无过滤地把所有条目连 OptionSlot
 *    一起写盘，读回来原样重建，所以 size() 永久不减 —— 重开界面高度照旧。
 *  - 开轮盘时走的是固定高度 {@code ySize - 58}，与选项数无关，
 *    这解释了为什么只有不开轮盘才出问题。
 *
 * 修法：只把 calculateRowHeight 里那两处 {@code options.size()} 换成
 * 「实际可显示的选项数」。用 @Redirect 拦 HashMap.size() 调用点，不写 ordinal
 * 即可同时覆盖判断与算术两处，改动面最小。
 *
 * 有效性判定与 appendDialog 的过滤条件逐字一致（{@code isAvailable}）：
 *   optionType == 2        → 不计（禁用，唯一无条件不显示的）
 *   optionType == 0/3/4    → 计入（关闭对话 / 角色 / 命令，都会显示）
 *   optionType == 1        → 仅当目标对话存在且对该玩家可用才计入
 * 不能只判 {@code optionType != 2}：那会漏掉「指向已删除对话的 type 1」，
 * 而 appendDialog 是会把它过滤掉的，仍然导致高度与行数不一致。
 *
 * 不改 NBT 写入（不去清理 map 里的空条目）：轮盘的方位是硬编码 slot 0..5，
 * slot 编号有语义，压缩 key 会破坏轮盘布局，而且那属于改存档格式。
 * 纯客户端修布局即可完全解决该现象。
 */
@Mixin(value = GuiDialogInteract.class, remap = false)
public class MixinGuiDialogInteractOptionHeight {

    /** 已过滤的可显示选项 slot 列表，由 appendDialog 填充。 */
    @Shadow(remap = false)
    private List<Integer> options;

    /**
     * 返回实际参与渲染的选项数量。
     *
     * calculateRowHeight 的三个调用点（initGui、选完一项之后、appendDialog 末尾）
     * 都发生在 this.options 填充完毕之后，且该字段声明时已初始化为空 ArrayList，
     * 所以直接用它的 size 是安全的；万一为 null 再回落到原始 map 大小并自行过滤。
     */
    @Redirect(
            method = "calculateRowHeight",
            at = @At(value = "INVOKE", target = "Ljava/util/HashMap;size()I"),
            remap = false,
            require = 1)
    private int cnpcplus$visibleOptionCount(HashMap<Integer, DialogOption> map) {
        List<Integer> visible = this.options;
        if (visible != null) {
            return visible.size();
        }
        if (map == null) {
            return 0;
        }
        // 兜底路径：自行按 appendDialog 的条件统计一次。
        GuiDialogInteract self = (GuiDialogInteract) (Object) this;
        int count = 0;
        for (DialogOption option : map.values()) {
            if (option != null && option.isAvailable(self.player)) {
                count++;
            }
        }
        return count;
    }
}
