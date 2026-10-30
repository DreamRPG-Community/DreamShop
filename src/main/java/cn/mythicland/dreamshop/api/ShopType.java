package cn.mythicland.dreamshop.api;

/**
 * Direction of the shop's inventory flow.
 */
public enum ShopType {

    /**
     * The shop gives the product and receives coins.
     */
    SELLING("出售商店"),

    /**
     * The shop receives the product and gives coins.
     */
    BUYING("收购商店");

    private final String displayName;

    ShopType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the Chinese display name used in menus and signs.
     *
     * @return display name
     */
    public String displayName() {
        return displayName;
    }
}
