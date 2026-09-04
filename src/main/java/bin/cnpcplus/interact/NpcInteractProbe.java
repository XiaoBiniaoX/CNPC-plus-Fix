package bin.cnpcplus.interact;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import noppes.npcs.controllers.data.Dialog;
import noppes.npcs.controllers.data.DialogOption;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * 判断一只 NPC 在客户端看来「是否确实没有任何右键交互内容」。
 *
 * 背景：CNPC 的 {@code processInteract} 无论有没有交互内容都返回 true
 * （客户端分支只看 {@code !isAttacking()}，服务端分支那句 return true 也在
 * if/else 链之外），于是原版 {@code Minecraft.rightClickMouse} 在
 * {@code interactWithEntity} 返回 SUCCESS 后就直接 return，
 * 后面那段 {@code processRightClick}（拉弓 / 吃食物 / 喝药水）永不执行。
 *
 * 关键前提（已由字节码确认）：
 * {@code PlayerControllerMP.interactWithEntity} 是**先无条件发出**
 * {@code CPacketUseEntity}，之后才计算返回值。所以客户端返回 false
 * **不会**影响服务端的交互处理 —— 对话、任务、角色界面照旧由服务端打开。
 * 客户端返回值只决定「本地是否额外执行手持物品的使用」。
 * 这让本修复的失败代价很小：最坏情况是服务端开了界面、同时物品也被使用，
 * 而不是交互被吞掉。
 *
 * 判定所需数据客户端都有：
 *  - {@code advanced.role}：writeSpawnData 白名单里有 "Role" 键
 *  - {@code npc.dialogs}：由 DataAdvanced.readToNBT 从 "NPCDialogOptions" 还原
 *  - {@code advanced.interactLines}：同属 DataAdvanced
 * 唯一拿不到的是任务交付状态（需服务端 PlayerData）。这属于已知残留，
 * 因上述「包先发」的性质，其后果仅为多用一次物品，不影响任务交付。
 *
 * 判定采取**保守**策略：任何一项拿不准就当作「有交互内容」，
 * 宁可保持原版行为，也不误吞玩家本该触发的对话。
 *
 * 本类仅客户端使用（由 client 混入调用），标注 SideOnly 防止被服务端加载。
 */
@SideOnly(Side.CLIENT)
public class NpcInteractProbe {

    /**
     * @return true 表示这只 NPC 目前没有任何右键交互内容，可以放行手持物品。
     */
    public static boolean hasNothingToInteract(EntityNPCInterface npc) {
        if (npc == null || npc.advanced == null) {
            return false;
        }
        // 战斗中：原版本来就走 !isAttacking()，交给原版处理，不插手。
        if (npc.isAttacking()) {
            return false;
        }
        // 有角色（商人、银行、雇佣兵、对话角色…）一律视为有交互内容。
        if (npc.advanced.role != 0) {
            return false;
        }
        // 交互台词**不再**参与判定。
        //
        // 哈基彬实测反馈：「最主要的就是这个交互台词，交互台词 NPC 生成自带，
        // 你这样我很难办」。证据确认此话为真：
        // EntityNPCInterface.java:386-388 在构造器里就会写入
        // `interactLines.lines.put(0, new Line(CustomNpcs.DefaultInteractLine))`，
        // 而 CustomNpcs.java:374 的默认值是 "Hello @p"。
        // 也就是说几乎每一只 NPC 天生带交互台词，若把它当作「有交互内容」，
        // 右键放行就等于永不生效。
        //
        // 取舍：台词只是「说一句话」，不打开任何界面、不消耗物品、不改状态，
        // 与「拉弓 / 吃食物 / 喝药水」并不互斥 —— 服务端仍会照常收到
        // CPacketUseEntity 并播放台词（该包在计算返回值前就已无条件发出）。
        // 因此放行手持物品不会让玩家失去台词，两者可以同时发生。
        // 真正会被「吞掉」的是对话、角色界面和任务交付，那三项仍然优先。
        // 任一可用对话 → 有交互内容。判定与 EntityNPCInterface.getDialog 一致。
        if (npc.dialogs != null && !npc.dialogs.isEmpty()) {
            EntityPlayer player = Minecraft.getMinecraft().player;
            for (DialogOption option : npc.dialogs.values()) {
                if (option == null || !option.hasDialog()) {
                    continue;
                }
                Dialog dialog = option.getDialog();
                if (dialog == null) {
                    continue;
                }
                if (dialog.availability == null || dialog.availability.isAvailable(player)) {
                    return false;
                }
            }
        }
        return true;
    }
}
