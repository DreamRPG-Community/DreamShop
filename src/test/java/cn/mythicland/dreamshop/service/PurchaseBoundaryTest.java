package cn.mythicland.dreamshop.service;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PurchaseBoundaryTest {

    @Test
    void fiveBlocksIsStillInsideButMovingBeyondItCancels() {
        Vector shop = new Vector(0.0D, 0.0D, 0.0D);
        assertFalse(PurchaseBoundary.outsideDistance(new Vector(5.0D, 0.0D, 0.0D), shop));
        assertTrue(PurchaseBoundary.outsideDistance(new Vector(5.01D, 0.0D, 0.0D), shop));
    }

    @Test
    void exactlyOneHundredTwentyDegreesIsAllowedButMoreIsRejected() {
        Vector facing = new Vector(0.0D, 0.0D, 1.0D);
        double allowedAngle = Math.toRadians(120.0D);
        Vector atBoundary = new Vector(Math.sin(allowedAngle), 0.0D, Math.cos(allowedAngle));
        double exceededAngle = Math.toRadians(120.01D);
        Vector beyondBoundary = new Vector(Math.sin(exceededAngle), 0.0D, Math.cos(exceededAngle));

        assertFalse(PurchaseBoundary.outsideViewAngle(facing, atBoundary));
        assertTrue(PurchaseBoundary.outsideViewAngle(facing, beyondBoundary));
    }

    @Test
    void straightUpOrDownDoesNotInventAHorizontalTurn() {
        assertFalse(PurchaseBoundary.outsideViewAngle(
                new Vector(0.0D, 1.0D, 0.0D),
                new Vector(0.0D, 0.0D, 1.0D)
        ));
    }
}
