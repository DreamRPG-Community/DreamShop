package cn.mythicland.dreamshop.api;

import org.bukkit.Location;

import java.util.Collection;
import java.util.Optional;

/**
 * Public DreamShop service contract exposed through Bukkit's ServicesManager.
 */
public interface DreamShopApi {

    /**
     * Finds a shop at one block location.
     *
     * @param location block location
     * @return immutable snapshot when present
     */
    Optional<ShopSnapshot> find(Location location);

    /**
     * Searches all shops by immutable criteria.
     *
     * @param query query criteria
     * @return immutable result collection
     */
    Collection<ShopSnapshot> search(ShopQuery query);

    /**
     * Creates a shop.
     *
     * @param request create request
     * @return write result
     */
    ShopCreateResult create(ShopCreateRequest request);

    /**
     * Updates a shop.
     *
     * @param request update request
     * @return write result
     */
    ShopUpdateResult update(ShopUpdateRequest request);

    /**
     * Marks a shop deleted and retains its transaction history.
     *
     * @param id shop identifier
     * @return write result
     */
    ShopDeleteResult delete(ShopId id);

    /**
     * Creates a one-time trade preview.
     *
     * @param request trade request
     * @return preview
     */
    TradePreview preview(TradeRequest request);

    /**
     * Commits a previous preview.
     *
     * @param confirmation confirmation token
     * @return trade result
     */
    TradeResult commit(TradeConfirmation confirmation);

    /**
     * Reloads configuration and refreshes product integrations.
     */
    void reload();
}
