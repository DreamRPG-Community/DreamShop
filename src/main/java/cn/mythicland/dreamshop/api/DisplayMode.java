package cn.mythicland.dreamshop.api;

/**
 * Visual representation shown beside a shop.
 */
public enum DisplayMode {

    /**
     * Only update the shop sign.
     */
    SIGN("仅牌子"),

    /**
     * Update the sign and expose the product as a display item.
     */
    SIGN_AND_ITEM("牌子 + 物品展示"),

    /**
     * Disable plugin-owned display updates.
     */
    OFF("关闭展示");

    private final String displayName;

    DisplayMode(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the display name.
     *
     * @return display name
     */
    public String displayName() {
        return displayName;
    }
}
