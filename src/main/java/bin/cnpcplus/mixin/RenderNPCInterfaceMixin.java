package bin.cnpcplus.mixin;

import bin.cnpcplus.config.CnpcPlusConfig;
import bin.cnpcplus.perf.client.FontWidthCache;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import noppes.npcs.client.renderer.RenderNPCInterface;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * NPC 名字遮挡。
 *
 * 实现方式：可见性剔除，而不是修改字体深度模式。
 *
 * 背景（诊断日志实证）：CNPC 的 renderLivingLabel 每帧参数完全恒定
 * （light/背景色/颜色/坐标不变，仅名字两遍绘制）。但只要让文字参与深度测试
 * （Font.DisplayMode.NORMAL 或 POLYGON_OFFSET），就会出现随视角转动部分字符缺失。
 * 三次深度模式方案均失败，故改为离散判定：
 *
 * - 玩家与 NPC 之间有方块或其他生物阻挡 → 取消整段名字绘制。
 * - 无阻挡 → 完全保持 CNPC 原版绘制，不改任何参数。
 *
 * 这样不存在深度精度竞争，字符不可能缺失。
 */
@Mixin(RenderNPCInterface.class)
public class RenderNPCInterfaceMixin {

    @Inject(method = "renderLivingLabel", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void cnpcplus$hideWhenBlocked(EntityNPCInterface npc, PoseStack matrixStack, MultiBufferSource buffer,
                                          int light, CallbackInfo ci) {
        if (!CnpcPlusConfig.NPC_NAMES_OBSCURED.get()) return;
        Player player = Minecraft.getInstance().player;
        if (player == null || npc == null) return;
        if (!player.hasLineOfSight(npc) || cnpcplus$blockedByLivingEntity(player, npc)) {
            ci.cancel();
        }
    }

    private static boolean cnpcplus$blockedByLivingEntity(Player player, EntityNPCInterface npc) {
        Vec3 start = player.getEyePosition();
        Vec3 end = npc.position().add(0.0, npc.getBbHeight() + 0.5, 0.0);
        AABB search = new AABB(start, end).inflate(0.5);
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, search,
                entity -> entity != player && entity != npc && !entity.isSpectator())) {
            if (entity.getBoundingBox().inflate(0.1).clip(start, end).isPresent()) return true;
        }
        return false;
    }

    /**
     * 标题缓存上限。同时在场的不同标题不会多，超过就退回每帧构造（行为不变，只是不省）。
     * 语义取自 1.20.1 临时优化 {@code MixinRenderNPCInterfaceLabel}（真冲突合并项）。
     */
    @Unique private static final int MAX_TITLES = 256;

    /** {@code 已替换的标题串 -> 构造好的 Component}；仅客户端渲染线程访问，不需同步。 */
    @Unique private static final Map<String, MutableComponent> TITLE_POOL = new HashMap<>(32);

    /**
     * 替换 {@code Component.translatable(String)}：& → § 颜色码 + 已替换串缓存。
     *
     * <p>缓存语义取自 1.20.1 临时优化（{@code MixinRenderNPCInterfaceLabel}）：
     * 标题变化频率是「管理员改一次配置」级别，而本调用每帧、每个可见名牌都执行。
     * 静态池而不是实例字段：{@code RenderNPCInterface} 按实体类型单例，
     * 不同标题的 NPC 交替渲染时实例字段等价于容量 1 的缓存。
     * 上限 256，超过退回每帧构造（行为不变，只是不省）。
     *
     * <p>缓存的 {@code MutableComponent} 只被用作 {@code append} 参数（不动参数本身）
     * 与 {@code Font.width}/{@code drawInBatch}（只读），跨帧跨 NPC 共享安全。
     *
     * <p>{@code require = 0}：纯分配优化，CNPC 改写标题构造时静默失效即可。
     */
    @Redirect(method = "renderLivingLabel", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"), remap = false, require = 0)
    private MutableComponent cnpcplus$titleColor(String key) {
        String cacheKey = key.indexOf('&') >= 0 ? key.replace('&', '§') : key;
        MutableComponent cached = TITLE_POOL.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        MutableComponent built = key == cacheKey ? Component.translatable(key) : Component.literal(cacheKey);
        if (TITLE_POOL.size() < MAX_TITLES) {
            TITLE_POOL.put(cacheKey, built);
        }
        return built;
    }

    /**
     * 缓存 {@code Font.width(FormattedText)} 结果（1.21.1 字节码实证的名牌热点）。
     *
     * <p>{@code renderLivingLabel} 每帧对<b>同一个</b>标题 Component 调两次
     * {@code Font.width}（偏移 240/289），对名字 Component 同样两次（偏移 347/397）——
     * 而 {@code Font.width} 内部是 {@code StringSplitter} 的完整文本布局，
     * 每个可见名牌每帧 4 次全量布局。名字/标题都是静态内容，这是纯重复计算
     * （关掉名字显示 FPS 明显回升的主要原因）。
     *
     * <p>处理器委托给 {@link FontWidthCache}（值语义 WeakHashMap + 资源重载清空），
     * 见该类 javadoc。{@code require = 0}：纯性能优化，失配静默退回原版路径。
     */
    @Redirect(method = "renderLivingLabel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I"), remap = false, require = 0)
    private int cnpcplus$cachedFontWidth(Font font, FormattedText text) {
        return FontWidthCache.width(font, text);
    }
}
