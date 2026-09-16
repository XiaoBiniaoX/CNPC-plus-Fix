package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.config.CnpcPlusConfig;
import bin.cnpcplus.common.GlobalRadiusGuard;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 鎬ц兘闂 5锛氭敼鍏ㄥ眬 {@code World.MAX_ENTITY_RADIUS} 涓斿彧澧炰笉鍑忋€? *
 * <h3>鏍瑰洜锛坖avap 瀛楄妭鐮佸疄璇侊級</h3>
 * 鍏?jar 鍙湁涓ゅ {@code putstatic MAX_ENTITY_RADIUS}锛? * {@code EntityNPCInterface.updateHitbox()} offset 192 涓? * {@code EntityCustomNpc.updateHitbox()} offset 209銆備袱澶勫舰鐘剁浉鍚岋細
 * <pre>
 * 161-167: width / 2 鈫?double
 * 173: getstatic  World.MAX_ENTITY_RADIUS
 * 176: dcmpl
 * 177: ifle 195                 鈫?鍙湁銆屽彉澶с€嶆墠 fall-through 鍒板啓鍏? * 192: putstatic  World.MAX_ENTITY_RADIUS
 * 195: 锛堟柟娉曠户缁埌 return锛屾病鏈夌浜屾鍐欏叆锛屼篃娌℃湁浠讳綍澶嶄綅锛? * </pre>
 * 杩欐槸 {@code World} 鐨?**static** 瀛楁锛團orge 鍔犵殑锛宼srg 鏃犳槧灏勬潯鐩級锛? * vanilla 榛樿 2.0锛岃**鎵€鏈?mod** 鐨?{@code getEntitiesWithinAABB} 鐢ㄤ綔
 * 鍚戝 padding 鍐冲畾鎵灏戝尯鍧椼€備竴涓ぇ Size 鐨?NPC 鍑虹幇杩囦竴娆★紝
 * 灏辨妸鍏ㄦ湇鎵€鏈夊疄浣撴煡璇㈢殑 padding 姘镐箙鎶珮鍒拌繘绋嬮€€鍑恒€? *
 * <h3>涓轰粈涔堜笉鐢?@Redirect 鎷?PUTSTATIC锛堥鐗堝穿婧冩暀璁級</h3>
 * 棣栫増鐢?{@code @Redirect} + {@code @At("FIELD", opcode = PUTSTATIC)}锛? * 瀹炴満鍚姩鍗?{@code VerifyError}锛? * <pre>
 * java.lang.VerifyError: Bad type on operand stack
 *   Location: noppes/npcs/entity/EntityNPCInterface.updateHitbox()V @193: swap
 *   Reason:   Type double_2nd (current frame, stack[1]) is not assignable
 *             to category1 type
 *   stack: { double, double_2nd, 'noppes/npcs/entity/EntityNPCInterface' }
 * </pre>
 * {@code MAX_ENTITY_RADIUS} 鏄?**double**锛堝崰涓や釜鏍堟Ы锛宑ategory-2锛夈€? * Mixin 涓?{@code @Redirect} 鍒伴潤鎬佸瓧娈靛啓鍏ユ墍鐢熸垚鐨勬ˉ鎺ヤ唬鐮侀噷鏈?{@code swap}
 * 鎸囦护锛岃€?JVM 鐨?{@code swap} **鍙兘鎿嶄綔 category-1 绫诲瀷**锛坕nt/float/寮曠敤锛夛紝
 * 閬囧埌 double/long 灏辫繃涓嶄簡瀛楄妭鐮佹牎楠屻€傝繖鏄?Mixin 瀵? * `double`/`long` 闈欐€佸瓧娈?Redirect 鐨?*宸茬煡闄愬埗**锛屼笉鏄?target 鍐欓敊銆? *
 * <h3>鏀圭敤鐨勬柟妗堬細TAIL 杩樺師</h3>
 * 涓嶆嫤鍐欏叆锛岃€屾槸鍦?{@code updateHitbox} 杩斿洖鍓嶆妸鍏ㄥ眬鍊艰繕鍘熸垚鎴戜滑璁板綍鐨勫熀绾匡細
 * <ul>
 *   <li>棣栨杩愯鏃惰涓嬭繘鍏ユ柟娉曞墠鐨勫€间綔涓哄熀绾匡紙閫氬父灏辨槸 vanilla 鐨?2.0锛? *       鎴栧叾浠?mod 鍚堢悊璁剧疆鐨勫€硷級锛?/li>
 *   <li>鏂规硶鎵ц瀹岃嫢鍙戠幇鍊艰鎶珮浜嗭紝鐩存帴鍐欏洖鍩虹嚎銆?/li>
 * </ul>
 * 鏁堟灉涓庢嫤鍐欏叆绛変环锛堝叏灞€瀛楁淇濇寔涓嶈 CNPC 鎶珮锛夛紝浣嗗彧鐢ㄦ櫘閫氱殑
 * {@code putstatic}锛堟垜浠嚜宸变唬鐮侀噷鐨勮祴鍊硷紝涓嶇粡 Mixin 妗ユ帴锛夛紝
 * 涓嶈Е鍙?category-2 鐨?{@code swap} 闂銆? *
 * <h3>鍩虹嚎鎬庝箞鍙栨墠涓嶄細璇激鍒殑 mod</h3>
 * 鍙湪銆屾湰娆¤皟鐢?*纭疄**鎶婂€兼姮楂樹簡銆嶆椂鎵嶈繕鍘燂紝涓旇繕鍘熷埌銆岃繘鍏ユ湰鏂规硶鏃剁殑鍊笺€嶃€? * 鎵€浠ワ細
 * <ul>
 *   <li>鍒殑 mod 鎶珮鐨勫€间笉浼氳鎴戜滑鎶规帀锛堥偅鍙戠敓鍦ㄦ湰鏂规硶涔嬪锛夛紱</li>
 *   <li>CNPC 鑷繁鎶珮鐨勪細琚珛鍒绘敹鍥炪€?/li>
 * </ul>
 *
 * <h3>鍝堝熀褰殑瑕佹眰</h3>
 * 銆屾垜涓嶅笇鏈涗綘瀵?Size 鍋氬嚭闄愬埗锛屾垜甯屾湜浣犲浠栫殑鏌ヨ鍋氬嚭浼樺寲銆嶁€斺€? * 鏈柟妗堜笉纰?{@code display.getSize()}銆佷笉鏀?NPC 鑷韩 width/height锛? * 鍙樆姝㈠畠姹℃煋鍏ㄥ眬鏌ヨ padding銆? *
 * <h3>涓庢棦鏈夋贩鍏ョ殑浜ら泦锛堝凡鏍稿疄鏃犲啿绐侊級</h3>
 * {@code mixin/lifecycle/MixinEntityNPCInterfaceHitbox} 涔熷湪
 * {@code updateHitbox} 鐨?TAIL 娉ㄥ叆锛堝案浣撳幓纰版挒绠?+ 閲嶅缓 AABB锛夈€? * 涓や釜 TAIL handler 閮藉彧璇诲啓鍚勮嚜鍏冲績鐨勪笢瑗匡細閭ｄ釜鍔ㄥ疄浣撶殑 width 涓?AABB锛? * 鏈被鍙姩 {@code World} 鐨勯潤鎬佸瓧娈碉紝浜掍笉骞叉壈锛岄『搴忔棤鍏炽€? *
 * <h3>鍙挙鍥?/h3>
 * cfg 鐨?{@code keepGlobalEntityRadius}锛岄粯璁ゅ紑銆傚叧鎺夊嵆鎭㈠鍘熺増琛屼负銆? *
 * <h3>鏈嶅姟绔畨鍏?/h3>
 * {@code updateHitbox} 涓ょ閮借皟鐢ㄣ€傛湰娣峰叆娉ㄥ唽 common 渚э紝
 * 鍙敤 {@code World} 涓?noppes 瀹炰綋绫诲瀷锛屾棤瀹㈡埛绔紩鐢ㄣ€? */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCGlobalRadius {

    /**
     * 宓屽娣卞害涓庡熀绾跨姸鎬佺敱 {@link GlobalRadiusGuard} 鎸佹湁銆?     *
     * 涓轰粈涔堜笉鏀惧湪娣峰叆绫婚噷锛氭湰绫讳笌 {@code MixinEntityCustomNpcGlobalRadius}
     * 瑕佸叡浜悓涓€浠界姸鎬侊紝鑰岃法 mixin 璁块棶瀛楁浼氳 Mixin 杞崲鍣ㄦ棤娉曡В鏋?     * 锛坒indings 闃舵 23 鐨勫穿婧冩暀璁級銆?     */
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
