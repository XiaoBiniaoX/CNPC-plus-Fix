package bin.cnpcplus.mixin.bossbar;

import bin.cnpcplus.bossbar.BossBarData;
import bin.cnpcplus.bossbar.BossBarSettings;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DataDisplay.class, remap = false)
public abstract class MixinDataDisplayBossBar {
    @Shadow EntityNPCInterface npc;
    @Inject(method = "readToNBT", at = @At("TAIL"), require = 1)
    private void cnpcplus$load(NBTTagCompound tag, CallbackInfo ci) { BossBarData.put(npc, BossBarSettings.read(tag)); }
    @Inject(method = "writeToNBT", at = @At("RETURN"), require = 1)
    private void cnpcplus$save(NBTTagCompound tag, CallbackInfoReturnable<NBTTagCompound> cir) { BossBarData.get((DataDisplay) (Object) this).write(cir.getReturnValue()); }
    @Inject(method = "setBossbar", at = @At("TAIL"), require = 1)
    private void cnpcplus$mode(int mode, CallbackInfo ci) { BossBarData.nativeMode(npc); }
    @Inject(method = "setBossColor", at = @At("TAIL"), require = 1)
    private void cnpcplus$legacyColor(int color, CallbackInfo ci) { BossBarData.put(npc, new BossBarSettings(npc.display.getBossbar(), BossBarSettings.legacyColor(npc.display.getBossColor()))); }
}
