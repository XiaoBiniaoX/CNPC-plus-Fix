package bin.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DataDisplay.class, priority = 500)
public class DataDisplayMixin {

    @Shadow(remap = false)
    EntityNPCInterface npc;

    @Inject(method = "readToNBT", at = @At("HEAD"), remap = false)
    private void cnpcplus$stripEfModelWhenDead(CompoundTag nbttagcompound, CallbackInfo ci) {
        if (npc.isKilled() && nbttagcompound.contains("efModel")) {
            nbttagcompound.remove("efModel");
        }
    }

    /**
     * 修复 isVisibleTo(IPlayer) 无限递归。
     *
     * <p>CNPC 原版 isVisibleTo(IPlayer) 内部调用 this.isVisibleTo(player)，
     * 编译器因参数类型为 IPlayer 而选中自身重载，导致栈溢出。
     * 此处 HEAD 注入，将调用重定向到正确的 isVisibleTo(ServerPlayer) 重载。
     */
    @Inject(method = "isVisibleTo(Lnoppes/npcs/api/entity/IPlayer;)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$fixInfiniteRecursion(IPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof ServerPlayer sp) {
            cir.setReturnValue(((DataDisplay) (Object) this).isVisibleTo(sp));
        }
    }
}