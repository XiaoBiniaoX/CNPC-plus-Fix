package bin.cnpcplus.mixin.skin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.client.renderer.RenderNPCInterface;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * 修复「NPC 用玩家姓名作为皮肤时有概率加载不上，加载上后过一段时间又掉回默认皮」。
 *
 * <p>根因在 CNPC 原版 {@code RenderNPCInterface.getTextureLocation}（反编译 :222-249）：
 * 它只在 {@code npc.textureLocation == null} 时算一次，算的时候用的是
 * {@code SkinManager.getInsecureSkin(profile)}。而这个方法在 1.21.1 是非阻塞的：
 * 内部 {@code getOrLoad(profile).getNow(null)} 拿不到已完成结果就立刻返回
 * {@code DefaultPlayerSkin.get(profile)}（原版 SkinManager:74-77）。皮肤要走后台线程解包 +
 * HTTP 下载，首帧几乎必然没好，于是 CNPC 把「默认皮」当成最终结果写死进 {@code textureLocation}，
 * 真皮下载完成后没有任何回调来清空它 —— 这就是「概率加载不上」。
 *
 * <p>「过一段时间掉落」是同一处的第二个侧面：{@code DataDisplay.readToNBT}（:189-191）在
 * display 数据变化时会把 {@code textureLocation} 置空，而服务端任何 {@code updateClient()}
 * 都会触发它；此时若 {@code SkinManager} 那个 15 秒 {@code expireAfterAccess} 缓存
 * （原版 SkinManager:47-48）已经淘汰，重算又落回默认皮并再次写死。
 * 「tp 走开再回来」能恢复，是因为实体重建时缓存正好是完成态。
 *
 * <p>修法：接管 skinType==1（玩家名皮肤）这一支，改用 {@code getOrLoad} 判 {@code isDone()}，
 * <b>只有真正拿到完成态才写入 textureLocation</b>；没好就本帧返回默认皮但不缓存，下一帧继续重试。
 * 每帧调用 {@code getOrLoad} 本身还会刷新那个 15 秒 access 窗口，顺带压住了 TTL 淘汰。
 */
@Mixin(value = RenderNPCInterface.class, remap = false)
public class MixinRenderNPCInterfaceSkin {

    @Inject(method = "getTextureLocation(Lnoppes/npcs/entity/EntityNPCInterface;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("HEAD"), cancellable = true)
    private void cnpcplus$playerSkin(EntityNPCInterface npc, CallbackInfoReturnable<ResourceLocation> cir) {
        if (npc == null) return;
        // 已经有确定结果时不插手，避免和原版逻辑抢。
        if (npc.textureLocation != null) return;
        if (npc.display == null || npc.display.skinType != 1) return;

        GameProfile profile = npc.display.playerProfile;
        if (profile == null) return;
        // profile 还没解析出 UUID（DataDisplay.readToNBT:173 会先建一个 id 为 null 的裸 profile，
        // 随后由 loadProfile 异步补全）。此时查缓存没有意义，先返回默认皮但不缓存，
        // 等 profile 补全后的某一帧再正常加载 —— 关键是绝不能让原版把默认皮写死。
        if (profile.getId() == null) {
            cir.setReturnValue(DefaultPlayerSkin.getDefaultTexture());
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.getSkinManager() == null) return;

        CompletableFuture<PlayerSkin> future;
        try {
            future = mc.getSkinManager().getOrLoad(profile);
        } catch (Exception e) {
            // 取不到就交回原版处理，绝不让渲染因为皮肤崩掉。
            return;
        }
        if (future == null) return;

        if (!future.isDone() || future.isCompletedExceptionally()) {
            // 还没下载好：本帧先用默认皮顶着，但绝不写进 textureLocation，
            // 否则就复刻了原版那个「把未完成态当最终结果缓存」的缺陷。
            cir.setReturnValue(DefaultPlayerSkin.getDefaultTexture());
            return;
        }

        PlayerSkin skin = future.getNow(null);
        if (skin == null || skin.texture() == null) {
            cir.setReturnValue(DefaultPlayerSkin.getDefaultTexture());
            return;
        }

        // 拿到的仍可能是「该 profile 没有 textures 属性」时的默认皮。
        // 这种情况同样不缓存：profile 可能还在异步补全（DataDisplay.loadProfile 的客户端分支），
        // 补全后下一帧就能拿到真皮。
        if (cnpcplus$isDefault(skin, profile)) {
            cir.setReturnValue(skin.texture());
            return;
        }

        npc.textureLocation = skin.texture();
        cir.setReturnValue(npc.textureLocation);
    }

    /** 判断拿到的是不是该 profile 的兜底默认皮（Steve/Alex 那一套）。 */
    @Unique
    private static boolean cnpcplus$isDefault(PlayerSkin skin, GameProfile profile) {
        try {
            PlayerSkin fallback = DefaultPlayerSkin.get(profile);
            return fallback != null && fallback.texture() != null
                    && fallback.texture().equals(skin.texture());
        } catch (Exception e) {
            return false;
        }
    }
}
