package top.cnpcplus.mixin;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.bossbar.BossBarData;

@Mixin(value = ServerBossEvent.class, remap = false)
public abstract class MixinServerBossEventRgb {
    @Inject(method = "addPlayer", at = @At("TAIL"), require = 1)
    private void cnpcplus$addColor(ServerPlayer player, CallbackInfo ci) {
        ServerBossEvent boss = (ServerBossEvent) (Object) this;
        BossBarData.sync(boss, player, !boss.isVisible());
    }

    @Inject(method = "removePlayer", at = @At("TAIL"), require = 1)
    private void cnpcplus$removeColor(ServerPlayer player, CallbackInfo ci) { BossBarData.sync((ServerBossEvent) (Object) this, player, true); }

    @Inject(method = "setVisible", at = @At("HEAD"), require = 1)
    private void cnpcplus$changeVisibility(boolean visible, CallbackInfo ci) {
        ServerBossEvent boss = (ServerBossEvent) (Object) this;
        if (visible != boss.isVisible()) for (ServerPlayer player : boss.getPlayers()) BossBarData.sync(boss, player, !visible);
    }
}
