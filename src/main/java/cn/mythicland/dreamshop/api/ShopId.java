package cn.mythicland.dreamshop.api;

import org.bukkit.Location;

import java.util.Objects;

/**
 * Immutable block coordinate identifying one shop.
 *
 * @param world world name
 * @param x     block x
 * @param y     block y
 * @param z     block z
 */
public record ShopId(String world, int x, int y, int z) {

    /**
     * Validates and normalizes the world name.
     */
    public ShopId {
        world = Objects.requireNonNull(world, "world").trim();
        if (world.isBlank()) throw new IllegalArgumentException("Shop world cannot be blank");
    }

    /**
     * Creates an identifier from a block location.
     *
     * @param location block location
     * @return immutable identifier
     */
    public static ShopId from(Location location) {
        Objects.requireNonNull(location, "location");
        if (location.getWorld() == null) throw new IllegalArgumentException("Shop location has no world");
        return new ShopId(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

    /**
     * Returns a stable database key.
     *
     * @return encoded block coordinate
     */
    public String encoded() {
        return world + ":" + x + ":" + y + ":" + z;
    }
}
