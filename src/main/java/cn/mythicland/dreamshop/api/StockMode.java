package cn.mythicland.dreamshop.api;

/**
 * Whether a shop consumes its plugin-managed stock.
 */
public enum StockMode {

    /**
     * The private virtual chest is the source of truth for available stock.
     */
    NORMAL("普通库存"),

    /**
     * The shop can trade without consuming product stock.
     */
    UNLIMITED("无限库存");

    private final String displayName;

    StockMode(String displayName) {
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
