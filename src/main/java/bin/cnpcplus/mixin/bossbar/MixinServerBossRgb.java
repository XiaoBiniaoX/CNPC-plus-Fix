package bin.cnpcplus.mixin.bossbar;

import bin.cnpcplus.bossbar.BossBarData;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.BossInfoServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossInfoServer.class)
public abstract class MixinServerBossRgb {
    @Shadow private boolean visible;
    @Inject(method = "addPlayer", at = @At("TAIL"), require = 1)
    private void cnpcplus$add(EntityPlayerMP player, CallbackInfo ci) { BossBarData.sync((BossInfoServer) (Object) this, player, !visible); }
    @Inject(method = "removePlayer", at = @At("TAIL"), require = 1)
    private void cnpcplus$remove(EntityPlayerMP player, CallbackInfo ci) { BossBarData.sync((BossInfoServer) (Object) this, player, true); }
    @Inject(method = "setVisible", at = @At("HEAD"), require = 1)
    private void cnpcplus$visible(boolean next, CallbackInfo ci) {
        if (next == visible) return;
        BossInfoServer boss = (BossInfoServer) (Object) this;
        for (EntityPlayerMP player : boss.getPlayers()) BossBarData.sync(boss, player, !next);
    }
}
