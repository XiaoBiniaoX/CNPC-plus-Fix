package top.cnpcplus.mixin;

import top.cnpcplus.perf.client.ShadowRadiusAccess;
import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * EntityRenderer.shadowRadius 的字段访问器。
 * 用 mojmap 名 shadowRadius + 默认 remap，由 AP 写入 refmap（生产 = f_114477_）；
 * 直接写 SRG 名 + remap=false 会被本工作区的 AP 校验拒绝（dev 下字段名是 shadowRadius）。
 * 外部调用通过包外 ShadowRadiusAccess，不直接加载 Mixin 接口。
 */
@Mixin(EntityRenderer.class)
public interface EntityRendererShadowAccess extends ShadowRadiusAccess {
    @Override
    @Accessor("shadowRadius")
    void cnpcyouhua$setShadowRadius(float value);
}
