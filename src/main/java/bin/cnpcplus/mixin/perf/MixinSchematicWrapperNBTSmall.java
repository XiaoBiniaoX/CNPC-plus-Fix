package bin.cnpcplus.mixin.perf;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import noppes.npcs.schematics.ISchematic;
import noppes.npcs.schematics.SchematicWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 优化 #1 的第四部分：把蓝图预览的<b>传输</b>上限从 25000 提到 100000。
 *
 * <h2>为什么渲染上限和传输上限是两件事</h2>
 * 客户端预览用的蓝图数据不是从文件读的，是服务端打包送过来的。
 * 1.21.1 的链路（NeoForge PayloadRegistrar 注册的自定义载荷，不再是 Forge SimpleChannel）：
 * <pre>
 * SPacketSchematicsTileSet.handle()                       // 服务端
 *   → tile.setSchematic(SchematicController.load(name))
 *   → Packets.send(player, new PacketGuiData(tile.getSchematic().getNBTSmall()))
 * PacketGuiData.handle()                                  // 客户端
 *   → GuiBlockBuilder.setGuiData(compound)
 *     → for (i &lt; list.size()) states.add(NbtUtils.readBlockState(...))
 *     → this.selected = new ISchematic(){ getBlockState(i) =&gt; states.get(i) }
 * </pre>
 *
 * {@code SchematicWrapper.getNBTSmall()}（反编译源码 182-199 行）的循环条件是
 * {@code i < this.size && i < 25000}。也就是说客户端 {@code states} 列表最多 25000 项，
 * 而 {@code GuiBlockBuilder$1.getBlockState(i)} 就是 {@code states.get(i)} ——
 * 索引超过就抛 {@code IndexOutOfBoundsException}。
 *
 * <p>换句话说：<b>原版那个 25000 与其说是性能上限，不如说是越界保护</b>。
 * 光把渲染上限提到 100000 而不动传输，得到的只会是异常而不是更多方块。
 *
 * <h2>为什么不能直接把 25000 改成 100000</h2>
 * 载荷最终还是走 vanilla 的 {@code ClientboundCustomPayloadPacket}。
 * 1.21.1 的 {@code ClientboundCustomPayloadPacket.MAX_PAYLOAD_SIZE} 仍是
 * <b>1048576 字节（1 MB）</b>（javap 已核），超过这个大小的包会被<b>直接踢下线</b>
 * （{@code Payload may not be larger than ...}）。
 *
 * <p>单个方块状态的 NBT 大小差异极大：
 * <ul>
 *   <li>空气/结构空洞走 {@code list.add(new CompoundTag())}，只占 1 字节（{@code TAG_End}）</li>
 *   <li>无属性方块（如 {@code minecraft:stone}）约 25-30 字节</li>
 *   <li>属性多的方块（如各朝向的栅栏、红石线）可以到 100 字节以上</li>
 * </ul>
 * 100000 个密集方块最坏情况能到 3 MB 以上，必然爆包。
 * 原版的 25000 × 30 ≈ 750 KB 已经贴着上限了，只是因为真实蓝图里空气占绝大多数才没炸。
 *
 * <h2>本类怎么做</h2>
 * 上限提到 100000，同时加一道<b>字节预算</b>（{@link #BYTE_BUDGET}）。
 * 逐个累计估算大小，超预算就停下 —— 于是：
 * <ul>
 *   <li>稀疏蓝图（绝大多数情况，空气占大头）能完整送到 100000 个方块</li>
 *   <li>病态密集蓝图会在安全点截断，而不是把玩家踢下线</li>
 * </ul>
 *
 * <p><b>截断之后客户端的风险（务必知悉）</b>：
 * 1.21.1 客户端 {@code GuiBlockBuilder$1.getBlockState(i)} 就是 {@code states.get(i)}，
 * <b>没有</b>对 {@code i &gt;= states.size()} 做越界保护；
 * 而客户端 {@code ClientEventHandler.onRenderTick} 的渲染循环是
 * {@code i &lt; size && i &lt; 25000} —— 注意它的 {@code size} 是
 * <b>服务端送来的完整蓝图尺寸</b>，不是客户端 {@code states} 的实际长度。
 * 所以一旦字节预算触发截断（{@code states.size()} &lt; 请求的 {@code size}），
 * 客户端在渲染或查询超出部分时会 {@code IndexOutOfBoundsException}。
 * 这是<b>风险项</b>，不是崩溃保证：真实蓝图中空气占绝大多数、
 * 900 KB 预算通常足够送到 100000 项不截断，但病态密集蓝图可能触发。
 * 需要实机验证（见交付报告）。本批次不含客户端类，客户端侧的防护
 * （如把渲染循环的上界改成 {@code states.size()}）留待后续批次处理。
 *
 * <p>另外注意：NPC 实际建造用的是<b>服务端</b>从文件加载的完整蓝图
 * （{@code TileBuilder.getBlock()} 读 {@code this.schematic}），
 * 不经过这个包。所以传输截断只影响预览的可见范围，不影响建造结果。
 *
 * <p><b>1.21.1 适配说明</b>：1.20.1 反编译的 {@code getNBTSmall} 用
 * {@code Blocks.BARRIER} 作为「写空 CompoundTag」的第二个条件；
 * 1.21.1 字节码的条件是
 * {@code block == Blocks.AIR || block == Blocks.STRUCTURE_VOID}。
 * 本类按 1.21.1 对齐（{@code AIR || STRUCTURE_VOID}）。
 * <b>这是本次移植相对 1.20.1 源码的必要适配。</b>
 *
 * <p><b>1.21.1 已核签名一致</b>（javap）：
 * {@code public getNBTSmall()}（唯一重载）、{@code public ISchematic schema}、
 * {@code public int size} 存在；方法体循环条件仍为 {@code i &lt; size && i &lt; 25000}，
 * 空标签写入条件为 {@code AIR || STRUCTURE_VOID}（非 1.20.1 的 BARRIER）。
 */
@Mixin(value = SchematicWrapper.class, remap = false)
public abstract class MixinSchematicWrapperNBTSmall {

    /** 传输方块上限，与渲染上限对齐。 */
    @Unique private static final int TRANSFER_LIMIT = 100_000;

    /**
     * 字节预算。1 MB 是硬上限（{@code ClientboundCustomPayloadPacket.MAX_PAYLOAD_SIZE}
     * = 1048576），这里留出 ~15% 余量给包头、
     * Width/Height/Length/SchematicName 等字段以及 NBT 压缩前的临时膨胀。
     */
    @Unique private static final int BYTE_BUDGET = 900_000;

    @Shadow public ISchematic schema;
    @Shadow public int size;

    @Inject(method = "getNBTSmall", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$largerPreviewPayload(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag compound = new CompoundTag();
        compound.putShort("Width", this.schema.getWidth());
        compound.putShort("Height", this.schema.getHeight());
        compound.putShort("Length", this.schema.getLength());
        compound.putString("SchematicName", this.schema.getName());

        ListTag list = new ListTag();
        int limit = Math.min(this.size, TRANSFER_LIMIT);
        int bytes = 0;

        for (int i = 0; i < limit; i++) {
            BlockState state;
            try {
                state = this.schema.getBlockState(i);
            } catch (Exception e) {
                break;
            }

            CompoundTag entry;
            // 1.21.1 原版条件是 AIR || STRUCTURE_VOID（1.20.1 是 AIR || BARRIER）。
            if (state == null || state.getBlock() == Blocks.AIR
                    || state.getBlock() == Blocks.STRUCTURE_VOID) {
                // 与原版 191-194 行一致：空气和结构空洞写空 CompoundTag。
                // 客户端 NbtUtils.readBlockState 遇到没有 "Name" 键的空标签会返回
                // Blocks.AIR.defaultBlockState()，所以语义正确。
                entry = new CompoundTag();
            } else {
                entry = NbtUtils.writeBlockState(state);
            }

            int cost = cnpcplus$estimate(entry);
            if (bytes + cost > BYTE_BUDGET) {
                // 预算耗尽。停在这里，客户端能显示已送到的部分。
                break;
            }
            bytes += cost;
            list.add(entry);
        }

        compound.put("Data", list);
        cir.setReturnValue(compound);
    }

    /**
     * 估算一个 CompoundTag 序列化后的字节数。
     *
     * <p>只需要「不低估」，不需要精确 —— 低估会爆包，高估只是少送几个方块。
     * 这里按 NBT 的实际编码规则算：
     * 每个具名标签 = 1（类型字节）+ 2（名字长度 u16）+ 名字 UTF-8 字节数 + 载荷；
     * 字符串载荷 = 2（长度 u16）+ UTF-8 字节数；
     * 复合标签结尾额外 1 字节（{@code TAG_End}）。
     *
     * <p>UTF-8 字节数用 {@code length() * 3} 作上界（BMP 字符最多 3 字节），
     * 而方块 ID 和属性名实际都是 ASCII，所以这是个宽松的安全边界。
     */
    @Unique
    private static int cnpcplus$estimate(CompoundTag tag) {
        int total = 1; // TAG_End
        for (String key : tag.getAllKeys()) {
            total += 1 + 2 + key.length() * 3;
            Tag value = tag.get(key);
            if (value instanceof StringTag) {
                total += 2 + value.getAsString().length() * 3;
            } else if (value instanceof CompoundTag nested) {
                total += cnpcplus$estimate(nested);
            } else {
                // writeBlockState 只会产生 String 和 Compound，这里给个宽松兜底。
                total += 64;
            }
        }
        return total;
    }
}
