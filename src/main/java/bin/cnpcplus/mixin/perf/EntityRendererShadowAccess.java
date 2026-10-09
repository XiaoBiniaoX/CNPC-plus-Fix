package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.perf.client.ShadowRadiusAccess;
import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 锁定 MC 1.21.1 的生产字段名（mojmap：{@code shadowRadius}；无 SRG 中间名）；
 * 无 refmap 依赖，生产目标检查直接验证该成员。
 * 外部调用通过包外 ShadowRadiusAccess，不直接加载 Mixin 接口。
 */
@Mixin(value = EntityRenderer.class, remap = false)
public interface EntityRendererShadowAccess extends ShadowRadiusAccess {
    @Override
    @Accessor(value = "shadowRadius", remap = false)
    void cnpcplus$setShadowRadius(float value);
}
