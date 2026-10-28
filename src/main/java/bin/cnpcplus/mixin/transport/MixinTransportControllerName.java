package bin.cnpcplus.mixin.transport;

import bin.cnpcplus.transport.TransportData;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.controllers.TransportController;
import noppes.npcs.controllers.data.TransportLocation;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.RoleTransporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 原生"传送师"编辑页的保存入口。它把打开界面时拿到的整份 TransportLocation
 * （包括旧名字）原样发回来，然后 {@code TransportController.saveLocation} 会新建一个
 * 对象替换掉表里的点位 —— 这就是可视化编辑改完名字又被"回退"的根因。
 *
 * <p>用名字版本号区分"旧快照"和"真的改名"：
 * <ul>
 *   <li>包里的版本号比服务端旧 → 改成服务端当前名字（旧快照，丢弃）；</li>
 *   <li>版本号相同但名字不同 → 玩家在原生页里改了名，接受并推进版本号；</li>
 *   <li>版本号相同且名字相同 → 原样通过。</li>
 * </ul>
 * 版本号由可视化编辑保存时推进，并随 transport.dat 一起落盘。
 */
@Mixin(value = TransportController.class, remap = false)
public abstract class MixinTransportControllerName {

    @Inject(method = "saveLocation", at = @At("HEAD"), require = 1)
    private void cnpcplus$guardStaleName(int categoryId, NBTTagCompound compound, EntityPlayerMP player,
                                         EntityNPCInterface npc, CallbackInfoReturnable<TransportLocation> cir) {
        if (compound == null || npc == null || !(npc.roleInterface instanceof RoleTransporter)) return;
        TransportController controller = TransportController.getInstance();
        if (controller == null) return;
        RoleTransporter role = (RoleTransporter) npc.roleInterface;
        int id = role.transportId >= 0 ? role.transportId : compound.getInteger("Id");
        TransportLocation current = id >= 0 ? controller.getTransport(id) : null;
        if (current == null) {
            // 表里没有这个 id：让原生方法走新建分支，避免它直接
            // this.locations.get(id).name 抛 NPE 把整整一次保存吞掉。
            if (compound.getInteger("Id") >= 0) compound.setInteger("Id", -1);
            return;
        }
        int incoming = Math.max(0, compound.getInteger(TransportData.NAME_STAMP));
        int known = TransportData.stamp(current);
        String name = compound.getString("Name");
        if (incoming < known) {
            compound.setString("Name", current.name);
            compound.setInteger(TransportData.NAME_STAMP, known);
        } else if (!name.equals(current.name)) {
            compound.setInteger(TransportData.NAME_STAMP, Math.max(known, incoming) + 1);
        }
    }
}
