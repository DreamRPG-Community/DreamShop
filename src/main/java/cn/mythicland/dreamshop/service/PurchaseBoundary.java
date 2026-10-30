package cn.mythicland.dreamshop.service;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.Objects;

/**
 * Geometric limits for an active DreamShop purchase interaction.
 */
final class PurchaseBoundary {

    static final double MAX_DISTANCE = 5.0D;
    static final double MAX_VIEW_ANGLE_DEGREES = 120.0D;
    // Bukkit 1.12.2 stores Vector components as floats, so the exact 120-degree boundary can
    // arrive a few millionths of a degree above the mathematical value.
    private static final double ANGLE_EPSILON = 0.001D;

    private PurchaseBoundary() {
    }

    static boolean outsideDistance(Vector playerPosition, Vector shopPosition) {
        Objects.requireNonNull(playerPosition, "playerPosition");
        Objects.requireNonNull(shopPosition, "shopPosition");
        return playerPosition.distanceSquared(shopPosition) > MAX_DISTANCE * MAX_DISTANCE;
    }

    static boolean outsideViewAngle(Location eyeLocation, Location shopLocation) {
        Objects.requireNonNull(eyeLocation, "eyeLocation");
        Objects.requireNonNull(shopLocation, "shopLocation");
        Vector facingDirection = horizontal(eyeLocation.getDirection());
        Vector towardShop = horizontal(shopLocation.toVector().subtract(eyeLocation.toVector()));
        return outsideViewAngle(facingDirection, towardShop);
    }

    static boolean outsideViewAngle(Vector facingDirection, Vector towardShop) {
        Objects.requireNonNull(facingDirection, "facingDirection");
        Objects.requireNonNull(towardShop, "towardShop");
        if (facingDirection.lengthSquared() == 0.0D || towardShop.lengthSquared() == 0.0D) return false;
        double angle = Math.toDegrees(facingDirection.angle(towardShop));
        return angle > MAX_VIEW_ANGLE_DEGREES + ANGLE_EPSILON;
    }

    private static Vector horizontal(Vector vector) {
        return new Vector(vector.getX(), 0.0D, vector.getZ());
    }
}
