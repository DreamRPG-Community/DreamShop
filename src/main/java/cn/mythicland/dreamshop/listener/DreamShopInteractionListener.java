package cn.mythicland.dreamshop.listener;

import cn.mythicland.dreamshop.api.ShopId;
import cn.mythicland.dreamshop.service.ShopService;
import cn.mythicland.framework.bukkit.component.ListenerComponent;
import cn.mythicland.framework.component.InjectComponent;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Protects shop containers and routes player interactions into the domain service.
 */
@InjectComponent
@ListenerComponent
public final class DreamShopInteractionListener implements Listener {

    private final ShopService service;

    public DreamShopInteractionListener(ShopService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        service.onWorldLoaded();
    }

    /**
     * Stops the native chest block from handling an ordinary right-click. This must run before
     * the normal interaction router: cancelling InventoryOpenEvent is too late to suppress the
     * native chest open feedback on Paper 1.12.2.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onRegisteredShopChestInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getPlayer().isSneaking()) return;
        Block block = event.getClickedBlock();
        if (block == null || service.shopAt(block).isEmpty()) return;
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Optional<ShopId> signShop = service.shopAtSign(block);
        if (signShop.isPresent()) {
            event.setCancelled(true);
            ShopId id = signShop.orElseThrow();
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                service.beginTradeFromSign(event.getPlayer(), id);
            } else {
                service.openInfo(event.getPlayer(), id);
            }
            return;
        }

        Optional<ShopId> shop = service.shopAt(block);
        if (shop.isEmpty()) {
            if (event.getAction() == Action.LEFT_CLICK_BLOCK
                    && !event.getPlayer().isSneaking()
                    && isChest(block)
                    && event.getPlayer().hasPermission(ShopService.ADMIN_PERMISSION)
                    && hasMainHandItem(event.getPlayer())
                    && !service.isBuildingMode(event.getPlayer())) {
                event.setCancelled(true);
                service.beginCreate(event.getPlayer(), block, true);
            }
            return;
        }

        // Registered shop chests are deliberately inert. The wall sign is the public interaction
        // surface; Shift+right-click management is handled by Lib before this listener runs.
        // Deny the block interaction here, before Minecraft reaches the chest block, so no native
        // chest inventory or open sound can be produced. Do not consume Shift+right-click: Lib's
        // shared administrator panel must still receive that interaction.
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && !event.getPlayer().isSneaking()) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    private static boolean isChest(Block block) {
        return block.getType() == org.bukkit.Material.CHEST
                || block.getType() == org.bukkit.Material.TRAPPED_CHEST;
    }

    private static boolean hasMainHandItem(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return item != null && item.getType() != org.bukkit.Material.AIR && item.getAmount() > 0;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Optional<ShopId> shop = service.shopAt(event.getBlock());
        if (shop.isEmpty()) return;
        if (event.getPlayer().isSneaking() && event.getPlayer().hasPermission(ShopService.ADMIN_PERMISSION)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBroken(BlockBreakEvent event) {
        if (!event.getPlayer().isSneaking() || !event.getPlayer().hasPermission(ShopService.ADMIN_PERMISSION)) return;
        service.shopAt(event.getBlock()).ifPresent(id -> service.removeAfterChestBreak(event.getBlock(), id));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != org.bukkit.Material.CHEST
                && event.getBlockPlaced().getType() != org.bukkit.Material.TRAPPED_CHEST) return;
        Block block = event.getBlockPlaced();
        Set<ShopId> adjacentShops = new HashSet<>();
        for (Block neighbor : new Block[]{
                block.getRelative(1, 0, 0), block.getRelative(-1, 0, 0),
                block.getRelative(0, 0, 1), block.getRelative(0, 0, -1)
        }) {
            service.shopAt(neighbor).ifPresent(adjacentShops::add);
        }
        if (adjacentShops.size() < 2) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage("§c不能把两个商店箱子连接在一起。");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> service.shopAt(block).isPresent());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!isShopHolder(holder)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNativeStockClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!service.isNativeStockInventory(event.getView().getTopInventory(), player.getUniqueId())) return;
        if (!service.canEditNativeStock(player, event.getView().getTopInventory())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNativeStockDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!service.isNativeStockInventory(event.getView().getTopInventory(), player.getUniqueId())) return;
        if (!service.canEditNativeStock(player, event.getView().getTopInventory())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNativeStockClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            service.saveNativeStockInventory(player, event.getInventory());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.closePlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        service.cancelShopForTeleport(event.getPlayer());
        service.closePlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (sameView(event)) return;
        service.validateShopPosition(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDisplayPickup(EntityPickupItemEvent event) {
        if (event.getItem().hasMetadata("DreamShop.display")) event.setCancelled(true);
    }

    private boolean isShopHolder(InventoryHolder holder) {
        if (holder instanceof Chest chest) {
            return service.shopAt(chest.getLocation().getBlock()).isPresent();
        }
        if (holder instanceof DoubleChest doubleChest) {
            return isShopHolder(doubleChest.getLeftSide()) || isShopHolder(doubleChest.getRightSide());
        }
        return false;
    }

    private static boolean sameView(PlayerMoveEvent event) {
        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()) return false;
        return event.getFrom().getYaw() == event.getTo().getYaw()
                && event.getFrom().getPitch() == event.getTo().getPitch();
    }
}
