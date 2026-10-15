package top.cnpcplus.villagertrade;

/**
 * 由 MixinMerchantOfferNameOnly 实现到 MerchantOffer 上的条目标记：
 * "仅名字检查"——NBT 每次都变的 mod 物品专用，匹配只校验物品 id + 展示名。
 * buildOffers 构造时打标；isRequiredItem 拦截时读标。
 */
public interface NameOnlyOffer {
    void cnpcplus$setNameOnly(boolean value);
    boolean cnpcplus$isNameOnly();
}
