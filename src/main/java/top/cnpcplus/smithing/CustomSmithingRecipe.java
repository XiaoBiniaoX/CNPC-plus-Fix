package top.cnpcplus.smithing;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.level.Level;

/**
 * 自定义锻造配方（227 继承原版 SmithingTransformRecipe，229 严格槽位模式，230 加仅名字检查）：
 * - 匹配 = **逐槽严格**：配方槽非空时物品+NBT 全等才命中（精确匹配）；
 *   配方槽为空时容器槽**也必须为空**（1/2 槽配方按相同空槽模式合成，多放一个槽不出货）。
 * - 230 nameOnly=true → **仅名字检查**：每槽只校验「物品id + 物品名字」两项
 *   （与 CNPC NoppesUtilPlayer.compareItemDetails 同链：先 getItem() 判 id，再 hoverName 判名字，
 *   含铁砧/铁砧改名的名字参与校验；附魔、耐久、其余自定义 NBT 字段不参与判定），
 *   空槽模式判定不变（配方空→台必须空）。
 * - 父类 Ingredient.of(stack)（item 级）负责槽位准入：空槽配方的空 Ingredient 放不进任何物品，
 *   与严格匹配配套；本类 matches（NBT 级）负责「放进后会不会出结果」。
 * NBT 不符时物品能放进去但不产出，与原版行为对玩家自然（不会出现「槽都进不去」的困惑）。
 *
 * <p>序列化走本 mod 注册的 cnpcplus:smithing_transform：fromNetwork 用 writeItem/readItem
 * 传输**完整 NBT** 的 4 个物品栈 + nameOnly 标志 → 客户端拿到的配方与服务端逐字节一致，
 * 两端 matches 结论一致（若沿用原版序列化器，网络传输会把 Ingredient 拆成 item 级、NBT 丢失，
 * 精确匹配只在服务端成立）。
 */
public class CustomSmithingRecipe extends SmithingTransformRecipe {

    private final ItemStack exactTemplate;
    private final ItemStack exactBase;
    private final ItemStack exactAddition;
    /** 父类的 result 字段是 net.minecraft 包私有（跨包读不到），网络序列化用本份引用。 */
    private final ItemStack exactResult;
    private final boolean nameOnly;

    public CustomSmithingRecipe(ResourceLocation id, ItemStack template, ItemStack base,
                                ItemStack addition, ItemStack result, boolean nameOnly) {
        super(id, Ingredient.of(template), Ingredient.of(base), Ingredient.of(addition), result);
        this.exactTemplate = template;
        this.exactBase = base;
        this.exactAddition = addition;
        this.exactResult = result;
        this.nameOnly = nameOnly;
    }

    /**
     * 严格槽位模式匹配（229 用户拍板，反转 228 的空槽通配）：
     * - 配方槽非空 → 容器槽必须是物品+NBT 全等的对应物品；
     * - 配方槽为空 → 容器槽**也必须为空**（isSameItemSameTags 双空=true 字节码实证）。
     * 即：1/2 槽配方只能按「相同空槽模式」合成，多放/少放一个槽都不出货。
     * 槽 0=模板 1=基座 2=升级材料，与原版 SmithingMenu 槽序一致。
     * 230 nameOnly=true → 每槽仅名字检查（校验物品id+物品名字，其余 NBT 字段不参与），空槽模式判定不变。
     */
    @Override
    public boolean matches(Container container, Level level) {
        if (this.nameOnly) {
            return nameMatch(container.getItem(0), this.exactTemplate)
                    && nameMatch(container.getItem(1), this.exactBase)
                    && nameMatch(container.getItem(2), this.exactAddition);
        }
        return ItemStack.isSameItemSameTags(container.getItem(0), this.exactTemplate)
                && ItemStack.isSameItemSameTags(container.getItem(1), this.exactBase)
                && ItemStack.isSameItemSameTags(container.getItem(2), this.exactAddition);
    }

    /**
     * 仅名字检查的逐槽判定（= CNPC NoppesUtilPlayer 同链，商户交易语义）：
     * ①物品id（getItem）必须相等；②物品名字（hoverName 字符串，含自定义命名）必须相等。
     * 空槽模式与 229 严格语义一致（配方空→台必须空）。附魔/耐久/其余自定义 NBT 字段不参与判定。
     */
    private static boolean nameMatch(ItemStack slot, ItemStack want) {
        if (want.isEmpty()) return slot.isEmpty();
        if (slot.isEmpty()) return false;
        if (slot.getItem() != want.getItem()) return false;
        return slot.getHoverName().getString().equals(want.getHoverName().getString());
    }

    /**
     * 空槽配方=合法形态（1/2 槽可注册），不能被父类 isIncomplete（三 ingredient
     * 任一 hasNoItems 即"不完整"，配方书/JEI 过滤用）误杀 —— 覆写恒 false。
     * 槽位准入不再覆写（229 回退 228 通配）：空槽的 Ingredient.of(EMPTY) test 恒 false
     * → 空槽放不进物品，与上面的严格判定配套（台端该槽必须空）。
     */
    @Override
    public boolean isIncomplete() {
        return false;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return top.cnpcplus.smithing.SmithingSerializers.SMITHING_TRANSFORM.get();
    }

    /**
     * 本配方专用序列化器：网络侧按「4 个完整物品栈」收发（保 NBT）；
     * JSON 侧没有来源——配方只在运行时注入，从不经过 datapack JSON。
     */
    public static class Serializer implements RecipeSerializer<CustomSmithingRecipe> {

        @Override
        public CustomSmithingRecipe fromJson(ResourceLocation id, com.google.gson.JsonObject json) {
            throw new UnsupportedOperationException(
                    "cnpcplus 自定义锻造配方只在运行时注入，不支持 datapack JSON 解析");
        }

        @Override
        public CustomSmithingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            ItemStack template = buf.readItem();
            ItemStack base = buf.readItem();
            ItemStack addition = buf.readItem();
            ItemStack result = buf.readItem();
            boolean nameOnly = buf.readBoolean();
            return new CustomSmithingRecipe(id, template, base, addition, result, nameOnly);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, CustomSmithingRecipe recipe) {
            buf.writeItem(recipe.exactTemplate);
            buf.writeItem(recipe.exactBase);
            buf.writeItem(recipe.exactAddition);
            buf.writeItem(recipe.exactResult);
            buf.writeBoolean(recipe.nameOnly);
        }
    }
}
