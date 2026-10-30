package cn.mythicland.dreamshop.api;

/**
 * Immutable shop search criteria.
 */
public record ShopQuery(
        String world,
        java.util.UUID owner,
        ShopType type,
        ShopStatus status
) {

    /**
     * Creates normalized nullable criteria.
     */
    public ShopQuery {
        world = normalizeWorld(world);
    }

    /**
     * Returns an unrestricted query.
     *
     * @return unrestricted query
     */
    public static ShopQuery all() {
        return new ShopQuery(null, null, null, null);
    }

    private static String normalizeWorld(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
