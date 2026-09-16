package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.config.CnpcPlusConfig;
import bin.cnpcplus.common.GlobalRadiusGuard;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 鎬ц兘闂 5 鐨勭浜屽鍐欏叆鐐癸細{@code EntityCustomNpc.updateHitbox()} offset 209銆? *
 * 涓?{@link MixinEntityNPCGlobalRadius} 鍚屾瀯锛岀悊鐢变笌銆屼负浠€涔堜笉鐢? * {@code @Redirect} 鎷?PUTSTATIC銆嶇殑 {@code VerifyError} 鏁欒瑙侀偅涓被鐨勬敞閲娿€? *
 * <h3>涓轰粈涔堣鍗曠嫭涓€涓贩鍏?/h3>
 * {@code EntityCustomNpc} 閲嶅啓浜?{@code updateHitbox} 骞舵湁鑷繁鐨勪竴浠? * {@code putstatic}锛坥ffset 209锛夛紝鐖剁被鐨勬贩鍏ョ涓嶅埌瀛愮被鐨勬柟娉曚綋銆? * {@code EntityCustomNpc} 鏄渶甯哥敤鐨?NPC 绫诲瀷锛屽疄闄呬笂鏄富瑕佹薄鏌撴簮锛? * 涓ゅ蹇呴』涓€璧峰鐞嗐€? *
 * <h3>涓庣埗绫绘贩鍏ョ殑宓屽鍏崇郴</h3>
 * {@code EntityCustomNpc.updateHitbox} offset 95 浼氳皟 {@code super.updateHitbox()}锛? * 浜庢槸鐖剁被閭ｄ釜娣峰叆鐨?HEAD/TAIL 浼氬湪鏈被鐨?HEAD/TAIL 涔嬮棿鎵ц銆? * 涓よ€呭叡鐢?{@link MixinEntityNPCGlobalRadius} 鐨?ThreadLocal 娣卞害璁℃暟锛? * 鎵€浠ュ彧鏈夋渶澶栧眰锛堟湰绫荤殑 TAIL锛夎礋璐ｅ疄闄呰繕鍘燂紝涓嶄細涓€旀妸鍊兼敼鍥炲幓銆? */
@Mixin(value = EntityCustomNpc.class, remap = false)
public class MixinEntityCustomNpcGlobalRadius {

    @Inject(method = "updateHitbox", at = @At("HEAD"), remap = false, require = 1)
    private void cnpcplus$recordRadiusBaseline(CallbackInfo ci) {
        if (!CnpcPlusConfig.isKeepGlobalEntityRadius()) return;
        GlobalRadiusGuard.enter();
    }

    @Inject(method = "updateHitbox", at = @At("TAIL"), remap = false, require = 1)
    private void cnpcplus$restoreRadiusBaseline(CallbackInfo ci) {
        if (!CnpcPlusConfig.isKeepGlobalEntityRadius()) return;
        GlobalRadiusGuard.exit();
    }
}
