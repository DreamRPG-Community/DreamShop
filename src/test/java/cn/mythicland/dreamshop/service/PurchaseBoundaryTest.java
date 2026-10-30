package cn.mythicland.dreamshop.service;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PurchaseBoundaryTest {

    @Test
    void interactionChecksTheDestinationPositionAndView() {
        Location shop = new Location(null, 0.0D, 0.5D, 0.0D);
        Location facingShop = new Location(null, 0.0D, 0.0D, -4.0D, 0.0F, 0.0F);
        assertFalse(PurchaseBoundary.outsideInteraction(facingShop, 1.62D, shop));

        Location turnedAway = facingShop.clone();
        turnedAway.setYaw(180.0F);
        assertTrue(PurchaseBoundary.outsideInteraction(turnedAway, 1.62D, shop));

        Location tooFar = facingShop.clone();
        tooFar.setZ(-5.1D);
        assertTrue(PurchaseBoundary.outsideInteraction(tooFar, 1.62D, shop));
    }

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
