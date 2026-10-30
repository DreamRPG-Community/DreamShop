package cn.mythicland.dreamshop.api;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Immutable product identity and template.
 *
 * @param template       complete item template, always defensively copied
 * @param mythicItemId   DreamRPG item service internal ID, or null for a vanilla product
 * @param mythicRevision DreamRPG item service revision, or null for a vanilla product
 */
public record ShopProduct(
        ItemStack template,
        String mythicItemId,
        String mythicRevision
) {

    /**
     * Creates a detached product snapshot.
     */
    public ShopProduct {
        template = Objects.requireNonNull(template, "template").clone();
        if (template.getAmount() < 1) throw new IllegalArgumentException("Product amount must be positive");
        mythicItemId = normalizeText(mythicItemId);
        mythicRevision = normalizeText(mythicRevision);
    }

    private static String normalizeText(String value) {
        if (value == null) return null;
        String text = value.trim();
        return text.isBlank() ? null : text;
    }

    /**
     * Returns another defensive copy of the product template.
     *
     * @return cloned item template
     */
    @Override
    public ItemStack template() {
        return template.clone();
    }
}
