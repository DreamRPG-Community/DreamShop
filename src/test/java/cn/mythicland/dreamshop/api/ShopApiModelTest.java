package cn.mythicland.dreamshop.api;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the public model's validation and defensive-copy boundary.
 */
class ShopApiModelTest {

    @Test
    void shopProductReturnsDefensiveItemCopies() {
        ItemStack source = new ItemStack(Material.STONE, 4);
        ShopProduct product = new ShopProduct(source, null, null);

        ItemStack first = product.template();
        first.setAmount(1);

        assertNotSame(source, product.template());
        assertEquals(4, product.template().getAmount());
    }

    @Test
    void createRequestNormalizesPriceAndCopiesLocationAndProduct() {
        ItemStack source = new ItemStack(Material.STONE, 8);
        ShopCreateRequest request = ShopCreateRequest.builder()
                .location(nullLocation())
                .owner(UUID.randomUUID())
                .product(source)
                .price(new BigDecimal("1.5"))
                .build();

        source.setAmount(1);

        assertEquals(new BigDecimal("1.50"), request.price());
        assertEquals(DisplayMode.SIGN_AND_ITEM, request.displayMode());
        assertEquals(8, request.product().getAmount());
        assertEquals(12, request.location().getBlockX());
    }

    @Test
    void invalidPriceAndMissingPlayerOwnerAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ShopCreateRequest.builder()
                .location(nullLocation())
                .product(new ItemStack(Material.STONE))
                .price(BigDecimal.ZERO)
                .build()
        );

        assertThrows(IllegalArgumentException.class, () -> ShopCreateRequest.builder()
                .location(nullLocation())
                .product(new ItemStack(Material.STONE))
                .price(BigDecimal.ONE)
                .build()
        );
    }

    @Test
    void adminShopMayHaveNoOwner() {
        ShopCreateRequest request = ShopCreateRequest.builder()
                .location(nullLocation())
                .product(new ItemStack(Material.STONE))
                .price(BigDecimal.ONE)
                .adminShop(true)
                .build();

        assertTrue(request.adminShop());
    }

    @Test
    void unrestrictedQueryUsesNullableCriteria() {
        ShopQuery query = ShopQuery.all();

        assertNull(query.world());
        assertNull(query.owner());
        assertNull(query.type());
        assertNull(query.status());
    }

    @Test
    void rejectedWriteResultsDoNotExposeAStaleSnapshot() {
        assertNull(new ShopCreateResult(ShopCreateResult.Status.REJECTED, null, "rejected").snapshot());
        assertNull(new ShopUpdateResult(ShopUpdateResult.Status.REJECTED, null, "rejected").snapshot());
        assertNull(new ShopDeleteResult(ShopDeleteResult.Status.REJECTED, null, "rejected").snapshot());
    }

    private static org.bukkit.Location nullLocation() {
        return new org.bukkit.Location(null, 12.0, 64.0, -4.0);
    }
}
