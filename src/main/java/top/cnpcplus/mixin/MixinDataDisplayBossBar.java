package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.bossbar.BossBarData;
import top.cnpcplus.bossbar.BossBarSettings;

@Mixin(value = DataDisplay.class, remap = false)
public abstract class MixinDataDisplayBossBar {
    @Shadow EntityNPCInterface npc;

    @Inject(method = "readToNBT", at = @At("TAIL"), require = 1)
    private void cnpcplus$loadBossBar(CompoundTag tag, CallbackInfo ci) {
        BossBarData.put(npc, BossBarSettings.read(tag));
    }

    @Inject(method = "save", at = @At("RETURN"), require = 1)
    private void cnpcplus$saveBossBar(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        BossBarData.get((DataDisplay) (Object) this).write(cir.getReturnValue());
    }

    @Inject(method = "setBossbar", at = @At("TAIL"), require = 1)
    private void cnpcplus$nativeMode(int mode, CallbackInfo ci) {
        BossBarData.nativeMode(npc, npc.display.getBossbar());
    }

    @Inject(method = "setBossColor", at = @At("TAIL"), require = 1)
    private void cnpcplus$nativeColor(int color, CallbackInfo ci) {
        BossBarData.put(npc, new BossBarSettings(npc.display.getBossbar(), BossBarSettings.legacyColor(color)));
    }
}
