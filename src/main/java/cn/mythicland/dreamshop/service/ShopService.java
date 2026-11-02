package cn.mythicland.dreamshop.service;

import cn.mythicland.dreamshop.api.*;
import cn.mythicland.dreamshop.config.DreamShopConfiguration;
import cn.mythicland.dreamshop.database.DreamShopDatabase;
import cn.mythicland.dreamshop.integration.CoinLedger;
import cn.mythicland.dreamshop.integration.MmItemBridge;
import cn.mythicland.dreamshop.integration.WorldProtectBridge;
import cn.mythicland.dreamshop.listener.DreamShopMmListener;
import cn.mythicland.dreamshop.menu.ShopInfoMenu;
import cn.mythicland.dreamshop.menu.ShopManagementMenu;
import cn.mythicland.dreamshop.storage.ShopRecord;
import cn.mythicland.dreamshop.storage.ShopRepository;
import cn.mythicland.framework.bukkit.MainThread;
import cn.mythicland.framework.bukkit.admin.AdminPanelProvider;
import cn.mythicland.framework.bukkit.admin.AdminPanelRegistration;
import cn.mythicland.framework.bukkit.admin.AdminPanelService;
import cn.mythicland.framework.bukkit.component.ServiceComponent;
import cn.mythicland.framework.bukkit.menu.MenuManager;
import cn.mythicland.framework.bukkit.task.PluginTaskScope;
import cn.mythicland.framework.bukkit.text.LegacyText;
import cn.mythicland.framework.component.LifecycleComponent;
import cn.mythicland.framework.component.ManagedLifecycle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.*;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Main-thread DreamShop domain service.
 */
@LifecycleComponent(dependsOn = DreamShopDatabase.class)
@ServiceComponent(DreamShopApi.class)
public final class ShopService implements DreamShopApi, ManagedLifecycle {

    public static final String USE_PERMISSION = "dreamshop.use";
    public static final String ADMIN_PERMISSION = "dreamshop.admin";
    /**
     * Legacy permission name retained for configuration compatibility. Shop creation and
     * management now require {@link #ADMIN_PERMISSION}.
     */
    @Deprecated(forRemoval = false)
    public static final String CREATE_PERMISSION = ADMIN_PERMISSION;
    private static final String PURCHASE_CANCELLED_MESSAGE = "&c交易已取消。";
    private final JavaPlugin plugin;
    private final PluginTaskScope tasks;
    private final MenuManager menus;
    private final DreamShopConfiguration configuration;
    private final ShopRepository repository;
    private final CoinLedger coins;
    private final MmItemBridge mm;
    private final WorldProtectBridge worldProtect;
    private final AdminPanelService adminPanels;
    private final Logger logger;
    private final Map<ShopId, ShopRecord> records = new LinkedHashMap<>();
    private final Map<UUID, PendingPreview> pendingPreviews = new HashMap<>();
    private final Map<UUID, InputSession> inputSessions = new ConcurrentHashMap<>();
    private final Map<UUID, ShopId> infoSessions = new HashMap<>();
    private final Map<UUID, ShopId> managementSessions = new HashMap<>();
    private final Map<UUID, ShopId> stockSessions = new HashMap<>();
    private final Map<UUID, ShopStockInventorySession> stockInventorySessions = new HashMap<>();
    private final Map<ShopId, UUID> displayEntities = new HashMap<>();
    private DreamShopMmListener mmListener;
    private AdminPanelRegistration adminPanelRegistration;

    /**
     * Creates the injected shop domain service.
     */
    public ShopService(
            JavaPlugin plugin,
            PluginTaskScope tasks,
            MenuManager menus,
            DreamShopConfiguration configuration,
            DreamShopDatabase database,
            ShopRepository repository,
            CoinLedger coins,
            MmItemBridge mm,
            WorldProtectBridge worldProtect,
            AdminPanelService adminPanels,
            Logger logger
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(database, "database");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.coins = Objects.requireNonNull(coins, "coins");
        this.mm = Objects.requireNonNull(mm, "mm");
        this.worldProtect = Objects.requireNonNull(worldProtect, "worldProtect");
        this.adminPanels = Objects.requireNonNull(adminPanels, "adminPanels");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    private static boolean isPreviewCurrent(TradePreview preview, ShopRecord record) {
        ShopSnapshot shop = preview.shop();
        ItemStack previewProduct = shop.product().template();
        ItemStack currentProduct = record.product();
        return shop.price().compareTo(record.price()) == 0
                && shop.type() == record.type()
                && shop.stockMode() == record.stockMode()
                && Objects.equals(shop.product().mythicItemId(), record.mythicItemId())
                && previewProduct.isSimilar(currentProduct);
    }

    private static ItemStack[] snapshotContents(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        ItemStack[] copy = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) {
            copy[index] = contents[index] == null ? null : contents[index].clone();
        }
        return copy;
    }

    private static void restoreContents(Inventory inventory, ItemStack[] contents) {
        if (inventory == null || contents == null || inventory.getSize() != contents.length) return;
        for (int index = 0; index < contents.length; index++) {
            inventory.setItem(index, contents[index] == null ? null : contents[index].clone());
        }
    }

    private static boolean canUseStockSize(int size) {
        return size >= 9 && size <= 54 && size % 9 == 0;
    }

    private static boolean isDoubleChestPair(Block first, Block second) {
        if (!isChest(first) || !isChest(second)) return false;
        if (!(first.getState() instanceof Chest firstChest)) return false;
        InventoryHolder holder = firstChest.getInventory().getHolder();
        if (!(holder instanceof DoubleChest doubleChest)) return false;
        return sameBlock(doubleChest.getLeftSide(), first) && sameBlock(doubleChest.getRightSide(), second)
                || sameBlock(doubleChest.getLeftSide(), second) && sameBlock(doubleChest.getRightSide(), first);
    }

    private static boolean sameBlock(InventoryHolder holder, Block block) {
        if (!(holder instanceof Chest chest)) return false;
        org.bukkit.Location location = chest.getLocation();
        return location.getWorld() != null && location.getWorld().equals(block.getWorld())
                && location.getBlockX() == block.getX()
                && location.getBlockY() == block.getY()
                && location.getBlockZ() == block.getZ();
    }

    private static boolean isChest(Block block) {
        return block != null && (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST);
    }

    private static boolean hasPhysicalChest(ShopId id) {
        World world = Bukkit.getWorld(id.world());
        return world == null || isChest(world.getBlockAt(id.x(), id.y(), id.z()));
    }

    private static org.bukkit.Location displayLocation(
            ShopRecord record,
            org.bukkit.World world,
            Block chest
    ) {
        double centerX = record.id().x() + 0.5D;
        double centerZ = record.id().z() + 0.5D;
        if (chest.getState() instanceof Chest chestState
                && chestState.getInventory().getHolder() instanceof DoubleChest doubleChest
                && doubleChest.getLeftSide() instanceof Chest left
                && doubleChest.getRightSide() instanceof Chest right) {
            org.bukkit.Location leftLocation = left.getLocation();
            org.bukkit.Location rightLocation = right.getLocation();
            centerX = (leftLocation.getX() + rightLocation.getX()) / 2.0D + 0.5D;
            centerZ = (leftLocation.getZ() + rightLocation.getZ()) / 2.0D + 0.5D;
        }
        return new org.bukkit.Location(world, centerX, record.id().y() + 1.15D, centerZ);
    }

    private static BlockFace chestFront(Block chest) {
        if (chest.getState() instanceof Chest chestState
                && chestState.getData() instanceof org.bukkit.material.Directional directional) {
            BlockFace facing = directional.getFacing();
            if (facing == BlockFace.NORTH || facing == BlockFace.SOUTH
                    || facing == BlockFace.EAST || facing == BlockFace.WEST) return facing;
        }
        return BlockFace.NORTH;
    }

    private static String displayType(ShopRecord record) {
        return record.type() == ShopType.SELLING ? "出售" : "收购";
    }

    private static String displayColoredType(ShopRecord record) {
        return record.type() == ShopType.SELLING ? "&4出售" : "&2收购";
    }

    private static String displayName(ItemStack item) {
        if (item == null) return "&0未知物品";
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return "&0" + item.getType().name();
        }
        // Keep the exact legacy color/formatting from the product name so the sign matches the
        // item shown in the preview menu and in the player's inventory.
        return meta.getDisplayName();
    }

    private static void collectAndRemove(java.util.Set<UUID> players, Map<UUID, ShopId> sessions, ShopId id) {
        for (Map.Entry<UUID, ShopId> entry : List.copyOf(sessions.entrySet())) {
            if (!entry.getValue().equals(id)) continue;
            players.add(entry.getKey());
            sessions.remove(entry.getKey(), id);
        }
    }

    private static String itemDisplayName(ItemStack product, ItemMeta meta) {
        if (meta != null && meta.hasDisplayName()) return meta.getDisplayName();
        return LegacyText.colorize("&f" + product.getType().name());
    }

    @Override
    public void load() {
        requirePrimaryThread();
        records.clear();
        for (ShopRecord record : repository.loadAll()) records.put(record.id(), record);
        cleanupMissingAnchors();
        adminPanelRegistration = adminPanels.register(new AdminPanelProvider() {
            @Override
            public String id() {
                return "dreamshop";
            }

            @Override
            public String displayName() {
                return "&aDreamShop 商店";
            }

            @Override
            public Material icon() {
                return Material.CHEST;
            }

            @Override
            public List<String> description() {
                return List.of("&7创建或管理一个系统商店", "", "&e点击创建或管理!");
            }

            @Override
            public boolean supports(Player player, Block block) {
                return player.hasPermission(ADMIN_PERMISSION) && isChest(block);
            }

            @Override
            public void open(Player player, Block block) {
                Optional<ShopId> shop = shopAt(block);
                if (shop.isPresent()) {
                    openManagement(player, shop.orElseThrow());
                    return;
                }
                menus.close(player);
                beginCreate(player, block, true);
            }
        });
        registerMythicItemsChangedEvent();
        refreshMythicProducts();
        refreshDisplays();
    }

    @Override
    public void reload() {
        requirePrimaryThread();
        refreshMythicProducts();
        refreshDisplays();
        refreshOpenMenus(managementSessions);
        refreshOpenMenus(stockSessions);
        refreshOpenMenus(infoSessions);
    }

    @Override
    public void unload() {
        requirePrimaryThread();
        if (adminPanelRegistration != null) {
            adminPanelRegistration.close();
            adminPanelRegistration = null;
        }
        tasks.close();
        for (UUID playerId : List.copyOf(stockInventorySessions.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) closeNativeStockInventory(player);
            else stockInventorySessions.remove(playerId);
        }
        java.util.Set<UUID> menuPlayers = new java.util.HashSet<>(managementSessions.keySet());
        menuPlayers.addAll(stockSessions.keySet());
        menuPlayers.addAll(infoSessions.keySet());
        pendingPreviews.values().stream()
                .map(pending -> pending.request().player())
                .forEach(menuPlayers::add);
        for (UUID playerId : menuPlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) menus.close(player);
        }
        pendingPreviews.clear();
        inputSessions.clear();
        managementSessions.clear();
        stockSessions.clear();
        stockInventorySessions.clear();
        infoSessions.clear();
        for (UUID entityId : List.copyOf(displayEntities.values())) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) entity.remove();
        }
        displayEntities.clear();
        mmListener = null;
        records.clear();
    }

    @Override
    public Optional<ShopSnapshot> find(org.bukkit.Location location) {
        requirePrimaryThread();
        ShopRecord record = records.get(resolveId(location));
        return record == null || record.status() == ShopStatus.DELETED
                ? Optional.empty()
                : Optional.of(snapshot(record));
    }

    @Override
    public Collection<ShopSnapshot> search(ShopQuery query) {
        requirePrimaryThread();
        Objects.requireNonNull(query, "query");
        return records.values().stream()
                .filter(record -> record.status() != ShopStatus.DELETED)
                .filter(record -> query.world() == null || query.world().equals(record.id().world()))
                .filter(record -> query.owner() == null || query.owner().equals(record.owner()))
                .filter(record -> query.type() == null || query.type() == record.type())
                .filter(record -> query.status() == null || query.status() == record.status())
                .map(this::snapshot)
                .toList();
    }

    @Override
    public ShopCreateResult create(ShopCreateRequest request) {
        requirePrimaryThread();
        Objects.requireNonNull(request, "request");
        Block block = request.location().getBlock();
        if (!isChest(block)) return rejected("商店必须绑定在箱子上。");
        ShopId id = ShopId.from(block.getLocation());
        ShopRecord existing = records.get(id);
        if ((existing != null && existing.status() != ShopStatus.DELETED)
                || findAdjacentShop(block).isPresent()) {
            return new ShopCreateResult(ShopCreateResult.Status.CONFLICT, null, "这个箱子已经有商店了。");
        }
        ItemStack product = request.product();
        if (ItemMatcher.isEmpty(product)) return rejected("商品不能为空。");
        product.setAmount(1);
        Optional<MmItemBridge.Identity> identity = mm.identify(product);
        ItemStack[] initialStock;
        try {
            initialStock = snapshotContents(physicalInventory(id));
        } catch (RuntimeException exception) {
            logger.warning("DreamShop could not read the new shop chest at " + id.encoded()
                    + ": " + exception.getMessage());
            initialStock = new ItemStack[27];
        }
        Instant now = Instant.now();
        ShopRecord record = new ShopRecord(
                id,
                request.owner(),
                request.adminShop(),
                product,
                identity.map(MmItemBridge.Identity::internalName).orElse(null),
                identity.map(MmItemBridge.Identity::revision).orElse(null),
                request.price(),
                request.type(),
                request.stockMode(),
                request.displayMode(),
                ShopStatus.ACTIVE,
                "",
                initialStock,
                now,
                now
        );
        try {
            if (existing == null) repository.insert(record);
            else repository.update(record);
            records.put(id, record);
            try {
                physicalInventory(id).clear();
            } catch (RuntimeException exception) {
                logger.warning("DreamShop could not clear the new shop chest at " + id.encoded()
                        + ": " + exception.getMessage());
            }
            updateDisplay(record);
            return new ShopCreateResult(ShopCreateResult.Status.CREATED, snapshot(record), "商店已创建。");
        } catch (RuntimeException exception) {
            logger.warning("DreamShop create failed at " + id.encoded() + ": " + exception.getMessage());
            return new ShopCreateResult(ShopCreateResult.Status.FAILED, null, "商店创建失败。");
        }
    }

    @Override
    public ShopUpdateResult update(ShopUpdateRequest request) {
        requirePrimaryThread();
        Objects.requireNonNull(request, "request");
        ShopRecord record = records.get(request.id());
        if (record == null || record.status() == ShopStatus.DELETED) {
            return rejectedUpdate("商店不存在。");
        }
        ItemStack product = request.product();
        if (ItemMatcher.isEmpty(product)) return rejectedUpdate("商品不能为空。");
        product.setAmount(1);
        Optional<MmItemBridge.Identity> identity = mm.identify(product);
        record.setProduct(product);
        record.setMythicItemId(identity.map(MmItemBridge.Identity::internalName).orElse(null));
        record.setMythicRevision(identity.map(MmItemBridge.Identity::revision).orElse(null));
        record.setPrice(request.price());
        record.setType(request.type());
        record.setStockMode(request.stockMode());
        record.setDisplayMode(request.displayMode());
        record.setStatus(ShopStatus.ACTIVE);
        record.setInvalidReason("");
        record.touch();
        repository.update(record);
        updateDisplay(record);
        refreshManagement(request.id());
        return new ShopUpdateResult(ShopUpdateResult.Status.UPDATED, snapshot(record), "商店已更新。");
    }

    @Override
    public ShopDeleteResult delete(ShopId id) {
        requirePrimaryThread();
        ShopRecord record = records.get(Objects.requireNonNull(id, "id"));
        if (record == null || record.status() == ShopStatus.DELETED) return rejectedDelete("商店不存在。");
        closeShopMenus(id);
        repository.markDeleted(record);
        removeShopSign(record);
        removeDisplayEntity(id);
        return new ShopDeleteResult(ShopDeleteResult.Status.DELETED, snapshot(record), "商店已删除。");
    }

    /**
     * Deletes a shop after checking the player's owner or administrator permission.
     *
     * @param player acting player
     * @param id     shop identifier
     * @return deletion result
     */
    public ShopDeleteResult delete(Player player, ShopId id) {
        requirePrimaryThread();
        ShopRecord record = records.get(Objects.requireNonNull(id, "id"));
        if (!canManage(player, record)) return rejectedDelete("你没有删除这个商店的权限。");
        return delete(id);
    }

    /**
     * Removes a shop only after its chest block has actually been broken.
     *
     * @param block chest block involved in the break
     * @param id    shop associated with that chest
     */
    public void removeAfterChestBreak(Block block, ShopId id) {
        requirePrimaryThread();
        ShopRecord record = records.get(id);
        tasks.runLater(1L, () -> {
                    if (isChest(block) || records.get(id) != record || record.status() == ShopStatus.DELETED) return;
                    delete(id);
                }
        );
    }

    @Override
    public TradePreview preview(TradeRequest request) {
        requirePrimaryThread();
        Objects.requireNonNull(request, "request");
        ShopRecord record = records.get(request.shopId());
        Player player = Bukkit.getPlayer(request.player());
        UUID token = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(configuration.snapshot().previewTimeoutSeconds());
        if (player == null) return invalidPreview(token, request, record, expiresAt, "玩家不在线。");
        if (request.quantity() > configuration.snapshot().maximumQuantity()) {
            return invalidPreview(token, request, record, expiresAt, "数量超过上限。");
        }
        if (record == null || record.status() != ShopStatus.ACTIVE) {
            return invalidPreview(token, request, record, expiresAt, "商店当前不可用。");
        }
        if (outsideShopBoundary(player, request.shopId(), player.getLocation())) {
            return invalidPreview(token, request, record, expiresAt, "交易已取消。");
        }
        if (record.owner() != null && record.owner().equals(request.player())) {
            return invalidPreview(token, request, record, expiresAt, "不能和自己的商店交易。");
        }
        BigDecimal total = record.price().multiply(BigDecimal.valueOf(request.quantity())).setScale(2);
        BigDecimal balance = coins.balance(request.player());
        int stockBefore = stock(record);
        int stockAfter = record.stockMode() == StockMode.UNLIMITED
                ? stockBefore
                : record.type() == ShopType.SELLING
                  ? Math.max(0, stockBefore - request.quantity())
                  : stockBefore + request.quantity();
        String failure = validateTrade(player, record, request.quantity(), total, balance, stockBefore);
        boolean canCommit = failure.isBlank();
        TradePreview preview = new TradePreview(
                token,
                snapshot(record),
                request.player(),
                request.quantity(),
                total,
                balance,
                record.type() == ShopType.SELLING
                        ? balance.subtract(total).max(BigDecimal.ZERO)
                        : balance.add(total),
                stockBefore,
                stockAfter,
                canCommit,
                canCommit ? "确认交易" : failure,
                expiresAt
        );
        pendingPreviews.put(token, new PendingPreview(preview, request));
        schedulePreviewExpiry(token);
        return preview;
    }

    @Override
    public TradeResult commit(TradeConfirmation confirmation) {
        requirePrimaryThread();
        PendingPreview pending = pendingPreviews.remove(Objects.requireNonNull(confirmation, "confirmation").token());
        if (pending == null || !pending.request().player().equals(confirmation.player())) {
            return failedTrade(confirmation.player(), null, 1, BigDecimal.ZERO, TradeResult.Status.EXPIRED,
                    "交易预览已失效。"
            );
        }
        TradePreview preview = pending.preview();
        if (preview.expiresAt().isBefore(Instant.now())) {
            return failedTrade(confirmation.player(), preview.shop().id(), preview.quantity(), preview.totalPrice(),
                    TradeResult.Status.EXPIRED, "交易预览已超时。"
            );
        }
        ShopRecord record = records.get(preview.shop().id());
        Player player = Bukkit.getPlayer(confirmation.player());
        if (record == null || player == null || record.status() != ShopStatus.ACTIVE) {
            return failedTrade(confirmation.player(), preview.shop().id(), preview.quantity(), preview.totalPrice(),
                    TradeResult.Status.REJECTED, "商店当前不可用。"
            );
        }
        if (outsideShopBoundary(player, preview.shop().id(), player.getLocation())) {
            return failedTrade(confirmation.player(), preview.shop().id(), preview.quantity(), preview.totalPrice(),
                    TradeResult.Status.REJECTED, "交易已取消。"
            );
        }
        if (!isPreviewCurrent(preview, record)) {
            return failedTrade(confirmation.player(), preview.shop().id(), preview.quantity(), preview.totalPrice(),
                    TradeResult.Status.REJECTED, "商店设置已变化, 请重新发起交易。"
            );
        }
        synchronized (record) {
            return executeTrade(record, player, preview);
        }
    }

    private TradeResult executeTrade(ShopRecord record, Player player, TradePreview preview) {
        UUID transactionId = UUID.randomUUID();
        Instant now = Instant.now();
        TransactionRecord processing = transaction(
                transactionId, record, player.getUniqueId(), preview.quantity(), preview.totalPrice(),
                TransactionRecord.Status.PROCESSING, "", now
        );
        repository.insertTransaction(processing);
        BigDecimal total = preview.totalPrice();
        try {
            String failure = validateTrade(
                    player,
                    record,
                    preview.quantity(),
                    total,
                    coins.balance(player.getUniqueId()),
                    stock(record)
            );
            if (!failure.isBlank()) return finishFailed(transactionId, record, player, preview, failure);
            if (record.type() == ShopType.SELLING) return executeSelling(transactionId, record, player, preview);
            return executeBuying(transactionId, record, player, preview);
        } catch (RuntimeException exception) {
            logger.warning("DreamShop transaction failed: " + exception.getMessage());
            return finishFailed(transactionId, record, player, preview, "交易失败, 物品和硬币已尽量回滚。");
        }
    }

    private TradeResult executeSelling(UUID transactionId, ShopRecord record, Player player, TradePreview preview) {
        Inventory stock = managedInventory(record);
        ItemStack[] stockBefore = snapshotContents(stock);
        ItemStack[] playerBefore = snapshotContents(player.getInventory());
        boolean withdrawn = false;
        boolean ownerDeposited = false;
        try {
            int removed = record.stockMode() == StockMode.UNLIMITED
                    ? 0
                    : ItemMatcher.remove(stock, record, mm, preview.quantity());
            if (record.stockMode() != StockMode.UNLIMITED && removed != preview.quantity()) {
                restoreContents(stock, stockBefore);
                return finishFailed(transactionId, record, player, preview, "库存不足。");
            }
            coins.withdraw(player.getUniqueId(), preview.totalPrice(), "DreamShop 出售商品");
            withdrawn = true;
            int added = ItemMatcher.add(player.getInventory(), record, mm, preview.quantity());
            if (added != preview.quantity()) throw new IllegalStateException("玩家背包空间不足");
            if (record.owner() != null) {
                coins.deposit(record.owner(), preview.totalPrice(), "DreamShop 商店收入");
                ownerDeposited = true;
            }
            if (record.stockMode() != StockMode.UNLIMITED) persistManagedStock(record, stock);
            repository.insertTransaction(transaction(transactionId, record, player.getUniqueId(), preview.quantity(),
                    preview.totalPrice(), TransactionRecord.Status.SUCCESS, "", Instant.now()
            ));
            return successTrade(transactionId, record, player, preview, "交易成功。");
        } catch (RuntimeException exception) {
            restoreContents(player.getInventory(), playerBefore);
            restoreContents(stock, stockBefore);
            if (record.stockMode() != StockMode.UNLIMITED) restoreManagedStock(record, stockBefore);
            if (withdrawn) coins.deposit(player.getUniqueId(), preview.totalPrice(), "DreamShop 交易回滚");
            if (ownerDeposited && record.owner() != null) {
                coins.withdraw(record.owner(), preview.totalPrice(), "DreamShop 交易回滚");
            }
            return finishFailed(transactionId, record, player, preview, exception.getMessage());
        }
    }

    private TradeResult executeBuying(UUID transactionId, ShopRecord record, Player player, TradePreview preview) {
        ItemStack[] playerBefore = snapshotContents(player.getInventory());
        Inventory stock = record.stockMode() == StockMode.UNLIMITED ? null : managedInventory(record);
        ItemStack[] stockBefore = stock == null ? null : snapshotContents(stock);
        boolean withdrawn = false;
        boolean deposited = false;
        try {
            int removed = ItemMatcher.remove(player.getInventory(), record, mm, preview.quantity());
            if (removed != preview.quantity()) {
                restoreContents(player.getInventory(), playerBefore);
                return finishFailed(transactionId, record, player, preview, "你的物品数量不足。");
            }
            if (record.owner() != null) {
                coins.withdraw(record.owner(), preview.totalPrice(), "DreamShop 收购支出");
                withdrawn = true;
            }
            if (record.stockMode() != StockMode.UNLIMITED) {
                int added = ItemMatcher.add(stock, record, mm, preview.quantity());
                if (added != preview.quantity()) throw new IllegalStateException("商店库存空间不足");
            }
            coins.deposit(player.getUniqueId(), preview.totalPrice(), "DreamShop 收购商品");
            deposited = true;
            if (stock != null) persistManagedStock(record, stock);
            repository.insertTransaction(transaction(transactionId, record, player.getUniqueId(), preview.quantity(),
                    preview.totalPrice(), TransactionRecord.Status.SUCCESS, "", Instant.now()
            ));
            return successTrade(transactionId, record, player, preview, "交易成功。");
        } catch (RuntimeException exception) {
            restoreContents(player.getInventory(), playerBefore);
            if (stock != null) {
                restoreContents(stock, stockBefore);
                restoreManagedStock(record, stockBefore);
            }
            if (deposited) coins.withdraw(player.getUniqueId(), preview.totalPrice(), "DreamShop 交易回滚");
            if (withdrawn && record.owner() != null)
                coins.deposit(record.owner(), preview.totalPrice(), "DreamShop 交易回滚");
            return finishFailed(transactionId, record, player, preview, exception.getMessage());
        }
    }

    private String validateTrade(
            Player player,
            ShopRecord record,
            int quantity,
            BigDecimal total,
            BigDecimal balance,
            int stock
    ) {
        if (record.type() == ShopType.SELLING) {
            if (record.stockMode() != StockMode.UNLIMITED && stock < quantity) return "库存不足。";
            if (balance.compareTo(total) < 0) return "余额不足。";
            if (!ItemMatcher.canAdd(player.getInventory(), record, quantity)) return "你的背包空间不足。";
            return "";
        }
        if (record.owner() != null && !coins.has(record.owner(), total)) return "店主余额不足。";
        if (ItemMatcher.count(player.getInventory(), record, mm) < quantity) return "你的物品数量不足。";
        if (record.stockMode() != StockMode.UNLIMITED
                && !ItemMatcher.canAdd(managedInventory(record), record, quantity)) return "商店库存空间不足。";
        return "";
    }

    private TradePreview invalidPreview(
            UUID token,
            TradeRequest request,
            ShopRecord record,
            Instant expiresAt,
            String message
    ) {
        ShopSnapshot snapshot = record == null ? emptySnapshot(request.shopId()) : snapshot(record);
        BigDecimal price = record == null ? BigDecimal.ZERO.setScale(2) : record.price();
        TradePreview preview = new TradePreview(token, snapshot, request.player(), request.quantity(),
                price.multiply(BigDecimal.valueOf(request.quantity())).setScale(2), BigDecimal.ZERO, BigDecimal.ZERO,
                0, 0, false, message, expiresAt
        );
        pendingPreviews.put(token, new PendingPreview(preview, request));
        schedulePreviewExpiry(token);
        return preview;
    }

    private void schedulePreviewExpiry(UUID token) {
        tasks.runLater(
                configuration.snapshot().previewTimeoutSeconds() * 20L,
                () -> pendingPreviews.remove(token)
        );
    }

    private ShopSnapshot emptySnapshot(ShopId id) {
        return new ShopSnapshot(
                id,
                null,
                false,
                new ShopProduct(new ItemStack(Material.STONE), null, null),
                BigDecimal.ONE.setScale(2),
                ShopType.SELLING,
                StockMode.NORMAL,
                DisplayMode.OFF,
                ShopStatus.INVALID,
                "商店不存在",
                0,
                Instant.EPOCH,
                Instant.EPOCH
        );
    }

    private TradeResult finishFailed(
            UUID transactionId,
            ShopRecord record,
            Player player,
            TradePreview preview,
            String reason
    ) {
        repository.insertTransaction(transaction(transactionId, record, player.getUniqueId(), preview.quantity(),
                preview.totalPrice(), TransactionRecord.Status.FAILED, Objects.requireNonNullElse(reason, "交易失败"),
                Instant.now()
        ));
        return failedTrade(player.getUniqueId(), record.id(), preview.quantity(), preview.totalPrice(),
                TradeResult.Status.FAILED, Objects.requireNonNullElse(reason, "交易失败"), transactionId
        );
    }

    private TradeResult failedTrade(
            UUID player,
            ShopId shopId,
            int quantity,
            BigDecimal total,
            TradeResult.Status status,
            String message
    ) {
        return failedTrade(player, shopId, quantity, total, status, message, UUID.randomUUID());
    }

    private TradeResult failedTrade(
            UUID player,
            ShopId shopId,
            int quantity,
            BigDecimal total,
            TradeResult.Status status,
            String message,
            UUID id
    ) {
        return new TradeResult(status, id, shopId == null ? new ShopId("unknown", 0, 0, 0) : shopId,
                player, quantity, total, message, Instant.now()
        );
    }

    private TradeResult successTrade(UUID id, ShopRecord record, Player player, TradePreview preview, String message) {
        return new TradeResult(TradeResult.Status.SUCCESS, id, record.id(), player.getUniqueId(), preview.quantity(),
                preview.totalPrice(), message, Instant.now()
        );
    }

    private TransactionRecord transaction(
            UUID id,
            ShopRecord record,
            UUID player,
            int quantity,
            BigDecimal total,
            TransactionRecord.Status status,
            String reason,
            Instant createdAt
    ) {
        return new TransactionRecord(id, record.id(), player, record.owner(), record.type(), quantity,
                record.price(), total, status, reason, createdAt
        );
    }

    private ShopSnapshot snapshot(ShopRecord record) {
        return new ShopSnapshot(record.id(), record.owner(), record.adminShop(),
                record.productSnapshot(), record.price(), record.type(), record.stockMode(), record.displayMode(),
                record.status(), record.invalidReason(), stock(record), record.createdAt(), record.updatedAt()
        );
    }

    private int stock(ShopRecord record) {
        if (record.status() == ShopStatus.DELETED || record.stockMode() == StockMode.UNLIMITED) return 0;
        try {
            return ItemMatcher.count(managedInventory(record), record, mm);
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private Inventory managedInventory(ShopRecord record) {
        Inventory inventory = Bukkit.createInventory(null, record.stockSize());
        inventory.setContents(record.stockContents());
        return inventory;
    }

    private void persistManagedStock(ShopRecord record, Inventory inventory) {
        record.setStockContents(snapshotContents(inventory));
        record.touch();
        repository.update(record);
        refreshStockInventory(record);
    }

    private void restoreManagedStock(ShopRecord record, ItemStack[] contents) {
        if (contents == null) return;
        record.setStockContents(contents);
        record.touch();
        repository.update(record);
        refreshStockInventory(record);
    }

    private void refreshStockInventory(ShopRecord record) {
        for (ShopStockInventorySession session : stockInventorySessions.values()) {
            if (session.holder().shopId().equals(record.id())) {
                session.mergeRefresh(record.stockContents());
            }
        }
    }

    private Inventory physicalInventory(ShopId id) {
        org.bukkit.World world = Bukkit.getWorld(id.world());
        if (world == null) throw new IllegalStateException("Shop world is not loaded: " + id.world());
        Block block = world.getBlockAt(id.x(), id.y(), id.z());
        if (!(block.getState() instanceof Chest chest)) throw new IllegalStateException("Shop chest is missing");
        return chest.getInventory();
    }

    private ShopId resolveId(org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) return null;
        ShopId direct = ShopId.from(location);
        ShopRecord directRecord = records.get(direct);
        if (directRecord != null && directRecord.status() != ShopStatus.DELETED) return direct;
        Block block = location.getBlock();
        return findAdjacentShop(block).orElse(direct);
    }

    private Optional<ShopId> findAdjacentShop(Block block) {
        if (!isChest(block)) return Optional.empty();
        for (Block neighbor : List.of(
                block.getRelative(1, 0, 0), block.getRelative(-1, 0, 0),
                block.getRelative(0, 0, 1), block.getRelative(0, 0, -1)
        )) {
            if (!isDoubleChestPair(block, neighbor)) continue;
            ShopId id = ShopId.from(neighbor.getLocation());
            ShopRecord record = records.get(id);
            if (record != null && record.status() != ShopStatus.DELETED) return Optional.of(id);
        }
        return Optional.empty();
    }

    /**
     * Removes non-deleted shop records whose configured anchor is no longer a chest block. The
     * database is the source of truth for active shops, but it must not retain unusable map
     * anchors after a world edit, external deletion, or a restored world snapshot.
     */
    private void cleanupMissingAnchors() {
        List<ShopRecord> stale = records.values().stream()
                .filter(record -> record.status() != ShopStatus.DELETED)
                .filter(record -> !hasPhysicalChest(record.id()))
                .toList();
        if (stale.isEmpty()) return;
        for (ShopRecord record : stale) {
            records.remove(record.id());
            repository.delete(record.id());
            logger.info("Removed DreamShop shop without a physical chest anchor: " + record.id().encoded());
        }
    }

    /**
     * Rechecks shop anchors when a managed world finishes loading.
     */
    public void onWorldLoaded() {
        tasks.runLater(1L, () -> {
                    cleanupMissingAnchors();
                    refreshDisplays();
                }
        );
    }

    private ShopCreateResult rejected(String message) {
        return new ShopCreateResult(ShopCreateResult.Status.REJECTED, null, message);
    }

    private ShopUpdateResult rejectedUpdate(String message) {
        return new ShopUpdateResult(ShopUpdateResult.Status.REJECTED, null, message);
    }

    private ShopDeleteResult rejectedDelete(String message) {
        return new ShopDeleteResult(ShopDeleteResult.Status.REJECTED, null, message);
    }

    private void refreshMythicProducts() {
        for (ShopRecord record : records.values()) {
            if (record.status() == ShopStatus.DELETED) continue;
            if (record.mythicItemId() != null) {
                MmItemBridge.RefreshOutcome outcome = mm.refresh(record.product());
                if (outcome.status() == MmItemBridge.Status.UPDATED && outcome.replacement() != null) {
                    setRefreshedProduct(record, outcome.replacement());
                    record.setStatus(ShopStatus.ACTIVE);
                    record.setInvalidReason("");
                    record.touch();
                    repository.update(record);
                } else if (outcome.status() == MmItemBridge.Status.CURRENT) {
                    if (record.status() != ShopStatus.ACTIVE) {
                        record.setStatus(ShopStatus.ACTIVE);
                        record.setInvalidReason("");
                        record.touch();
                        repository.update(record);
                    }
                } else if (outcome.status() == MmItemBridge.Status.UNMANAGED
                        || outcome.status() == MmItemBridge.Status.STALE) {
                    record.setStatus(ShopStatus.INVALID);
                    record.setInvalidReason("MythicMobs 物品不存在或无法生成");
                    record.touch();
                    repository.update(record);
                }
            }
            refreshInventory(record);
        }
    }

    private void refreshInventory(ShopRecord record) {
        if (!mm.available() || !record.hasManagedStock()) return;
        try {
            Inventory inventory = managedInventory(record);
            boolean changed = false;
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                ItemStack item = inventory.getItem(slot);
                MmItemBridge.RefreshOutcome outcome = mm.refresh(item);
                if (outcome.status() != MmItemBridge.Status.UPDATED || outcome.replacement() == null) continue;
                ItemStack replacement = outcome.replacement();
                if (item != null) replacement.setAmount(item.getAmount());
                inventory.setItem(slot, replacement);
                changed = true;
            }
            if (changed) persistManagedStock(record, inventory);
        } catch (RuntimeException exception) {
            logger.warning("Failed to refresh MM items in " + record.id().encoded() + ": " + exception.getMessage());
        }
    }

    private void setRefreshedProduct(ShopRecord record, ItemStack replacement) {
        record.setProduct(replacement);
        Optional<MmItemBridge.Identity> identity = mm.identify(replacement);
        record.setMythicItemId(identity.map(MmItemBridge.Identity::internalName).orElse(null));
        record.setMythicRevision(identity.map(MmItemBridge.Identity::revision).orElse(null));
    }

    private void refreshDisplays() {
        for (ShopRecord record : records.values()) updateDisplay(record);
        for (ShopId id : List.copyOf(displayEntities.keySet())) {
            ShopRecord record = records.get(id);
            if (record == null || record.status() != ShopStatus.ACTIVE) {
                removeDisplayEntity(id);
            }
        }
    }

    private void updateDisplay(ShopRecord record) {
        if (record.status() != ShopStatus.ACTIVE) {
            removeDisplayEntity(record.id());
            return;
        }
        org.bukkit.World world = Bukkit.getWorld(record.id().world());
        if (world == null) {
            removeDisplayEntity(record.id());
            return;
        }
        Block chest = world.getBlockAt(record.id().x(), record.id().y(), record.id().z());
        Sign sign = ensureSign(record, chest);
        if (sign != null) {
            List<String> lines = configuration.snapshot().signLines();
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index)
                        .replace("{owner}", displayOwnerName(record))
                        .replace("{item}", displayName(record.product()))
                        .replace("{type}", displayType(record))
                        .replace("{type-colored}", displayColoredType(record))
                        .replace("{stock}", displayStock(record))
                        .replace("{price}", record.price().toPlainString());
                sign.setLine(index, LegacyText.colorize(line));
            }
            sign.update(true, false);
        }
        if (record.displayMode() == DisplayMode.SIGN_AND_ITEM) ensureDisplayEntity(record, world);
        else removeDisplayEntity(record.id());
    }

    private void ensureDisplayEntity(ShopRecord record, org.bukkit.World world) {
        Block chest = world.getBlockAt(record.id().x(), record.id().y(), record.id().z());
        org.bukkit.Location location = displayLocation(record, world, chest);
        Item display = null;
        UUID entityId = displayEntities.get(record.id());
        if (entityId != null && Bukkit.getEntity(entityId) instanceof Item item && !item.isDead()) {
            display = item;
        }
        if (display == null) {
            display = world.dropItem(location, record.product());
            display.setMetadata(
                    "DreamShop.display",
                    new FixedMetadataValue(plugin, record.id().encoded())
            );
            displayEntities.put(record.id(), display.getUniqueId());
        }
        display.setItemStack(record.product());
        display.setPickupDelay(Integer.MAX_VALUE);
        display.setGravity(false);
        // World.dropItem gives the entity an initial random velocity. Clear it every refresh so
        // the display cannot drift away from the center of its shop chest.
        display.setVelocity(new Vector(0, 0, 0));
        display.teleport(location);
        display.setVelocity(new Vector(0, 0, 0));
    }

    private void removeDisplayEntity(ShopId id) {
        UUID entityId = displayEntities.remove(id);
        if (entityId == null) return;
        org.bukkit.entity.Entity entity = Bukkit.getEntity(entityId);
        if (entity != null) entity.remove();
    }

    private Sign ensureSign(ShopRecord record, Block chest) {
        BlockFace front = chestFront(chest);
        Block signBlock = chest.getRelative(front);
        if (signBlock.getType() == Material.WALL_SIGN && signBlock.getState() instanceof Sign sign) {
            org.bukkit.material.Sign data = (org.bukkit.material.Sign) sign.getData();
            if (data.isWallSign() && data.getAttachedFace() == front.getOppositeFace()) return sign;
            logger.warning("The block in front of DreamShop shop " + record.id().encoded()
                    + " is a sign with an incompatible orientation.");
            return null;
        }
        if (signBlock.getType() != Material.AIR) {
            logger.warning("The block in front of DreamShop shop " + record.id().encoded()
                    + " is occupied; the wall sign was not created.");
            return null;
        }
        signBlock.setType(Material.WALL_SIGN, false);
        if (!(signBlock.getState() instanceof Sign sign)) {
            logger.warning("Failed to create the wall sign in front of DreamShop shop "
                    + record.id().encoded());
            return null;
        }
        org.bukkit.material.Sign data = (org.bukkit.material.Sign) sign.getData();
        data.setFacingDirection(front);
        sign.setData(data);
        sign.update(true, false);
        return sign;
    }

    private void removeShopSign(ShopRecord record) {
        World world = Bukkit.getWorld(record.id().world());
        if (world == null) {
            logger.warning("Could not remove the DreamShop sign because world is unavailable: "
                    + record.id().encoded());
            return;
        }
        Block chest = world.getBlockAt(record.id().x(), record.id().y(), record.id().z());
        BlockFace front = chestFront(chest);
        Block signBlock = chest.getRelative(front);
        if (signBlock.getType() != Material.WALL_SIGN || !(signBlock.getState() instanceof Sign sign)) return;
        org.bukkit.material.Sign data = (org.bukkit.material.Sign) sign.getData();
        if (!data.isWallSign() || data.getAttachedFace() != front.getOppositeFace()) return;
        signBlock.setType(Material.AIR, false);
    }

    private String displayOwnerName(ShopRecord record) {
        if (record.adminShop()) return "系统商店";
        if (record.owner() == null) return "Unknown";
        String name = Bukkit.getOfflinePlayer(record.owner()).getName();
        return name == null || name.isBlank() ? "Unknown" : name;
    }

    private String displayStock(ShopRecord record) {
        if (record.stockMode() == StockMode.UNLIMITED) return "无限";
        try {
            Inventory inventory = managedInventory(record);
            return Integer.toString(record.type() == ShopType.SELLING
                    ? ItemMatcher.count(inventory, record, mm)
                    : ItemMatcher.space(inventory, record, mm));
        } catch (RuntimeException exception) {
            logger.warning("Failed to calculate sign stock for " + record.id().encoded()
                    + ": " + exception.getMessage());
            return "0";
        }
    }

    private void registerMythicItemsChangedEvent() {
        if (!mm.available()) {
            markUnavailableMythicShops();
            return;
        }
        try {
            ClassLoader addonLoader = mm.classLoader();
            if (addonLoader == null) return;
            mmListener = new DreamShopMmListener(this, tasks);
            EventExecutor executor = (listener, event) -> ((DreamShopMmListener) listener).onEvent(event);
            PluginManager pluginManager = plugin.getServer().getPluginManager();
            for (String eventName : List.of(
                    "cn.mythicland.dreamrpgitems.api.MythicItemsChangedEvent",
                    "cn.mythicland.dreamrpgitems.api.MythicItemsSyncRequestedEvent"
            )) {
                Class<?> eventType = Class.forName(eventName, true, addonLoader);
                if (!Event.class.isAssignableFrom(eventType)) {
                    throw new IllegalStateException("Not an event: " + eventName);
                }
                @SuppressWarnings("unchecked")
                Class<? extends Event> bukkitEventType = (Class<? extends Event>) eventType;
                pluginManager.registerEvent(bukkitEventType, mmListener, EventPriority.MONITOR, executor, plugin, true);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logger.warning("DreamRPG items change event is unavailable: " + exception.getMessage());
        }
    }

    private void markUnavailableMythicShops() {
        for (ShopRecord record : records.values()) {
            if (record.status() == ShopStatus.DELETED || record.mythicItemId() == null) continue;
            record.setStatus(ShopStatus.INVALID);
            record.setInvalidReason("DreamRPG items 不可用");
            record.touch();
            repository.update(record);
        }
    }

    /**
     * Refreshes all MM-backed shop products after a DreamRPG item service change event.
     */
    public void onMythicItemsChanged() {
        requirePrimaryThread();
        refreshMythicProducts();
        refreshDisplays();
        refreshOpenMenus(managementSessions);
        refreshOpenMenus(stockSessions);
        refreshOpenMenus(infoSessions);
    }

    /**
     * Returns a detached snapshot for a menu.
     *
     * @param id shop ID
     * @return current snapshot
     */
    public ShopSnapshot menuSnapshot(ShopId id) {
        requirePrimaryThread();
        return snapshot(record(id));
    }

    private void refreshManagement(ShopId id) {
        for (Map.Entry<UUID, ShopId> entry : managementSessions.entrySet()) {
            if (!entry.getValue().equals(id)) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) menus.refresh(player);
        }
    }

    private void refreshOpenMenus(Map<UUID, ShopId> sessions) {
        for (UUID playerId : List.copyOf(sessions.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) menus.refresh(player);
        }
    }

    private void closeShopMenus(ShopId id) {
        java.util.Set<UUID> players = new java.util.HashSet<>();
        collectAndRemove(players, managementSessions, id);
        collectAndRemove(players, stockSessions, id);
        collectAndRemove(players, infoSessions, id);
        for (Map.Entry<UUID, ShopStockInventorySession> entry : List.copyOf(stockInventorySessions.entrySet())) {
            if (!entry.getValue().holder().shopId().equals(id)) continue;
            players.add(entry.getKey());
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) closeNativeStockInventory(player);
            else stockInventorySessions.remove(entry.getKey(), entry.getValue());
        }
        for (UUID playerId : players) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) menus.close(player);
        }
    }

    /**
     * Starts the quantity chat workflow.
     *
     * @param player player
     * @param id     shop ID
     */
    public void beginTrade(Player player, ShopId id) {
        ShopRecord record = records.get(id);
        if (!canBeginTrade(player, record)) return;
        beginQuantityInput(player, id);
    }

    /**
     * Sends the product details and starts the quantity workflow for a wall-sign click.
     *
     * @param player player
     * @param id     shop ID
     */
    public void beginTradeFromSign(Player player, ShopId id) {
        ShopRecord record = records.get(id);
        if (!canBeginTrade(player, record)) return;
        sendTradeDetails(player, record);
        beginQuantityInput(player, id);
    }

    /**
     * Cancels a shop interaction when the player's position or view leaves its boundary.
     */
    public void validateShopPosition(Player player, Location location) {
        requirePrimaryThread();
        UUID playerId = Objects.requireNonNull(player, "player").getUniqueId();
        Optional<ShopId> shopId = activeInteractionShop(playerId);
        if (shopId.isEmpty()) return;
        ShopId id = shopId.orElseThrow();
        InputSession input = inputSessions.get(playerId);
        ShopRecord record = records.get(id);
        if (!(input instanceof CreatePriceInput)
                && (record == null || record.status() == ShopStatus.DELETED)) {
            cancelShopInteraction(player);
            return;
        }
        if ((input instanceof QuantityInput || input instanceof PriceInput)
                && record.status() != ShopStatus.ACTIVE) {
            cancelShopInteraction(player);
            return;
        }
        if (!outsideShopBoundary(player, id, location)) return;
        cancelShopInteraction(player);
    }

    private boolean outsideShopBoundary(Player player, ShopId id, Location location) {
        World world = Bukkit.getWorld(id.world());
        if (world == null) return true;
        Location center = new Location(world, id.x() + 0.5D, id.y() + 0.5D, id.z() + 0.5D);
        return PurchaseBoundary.outsideInteraction(location, player.getEyeHeight(), center);
    }

    /**
     * Cancels an active shop interaction when the player teleports.
     */
    public void cancelShopForTeleport(Player player) {
        requirePrimaryThread();
        if (activeInteractionShop(Objects.requireNonNull(player, "player").getUniqueId()).isPresent()) {
            cancelShopInteraction(player);
        }
    }

    private boolean canBeginTrade(Player player, ShopRecord record) {
        if (!player.hasPermission(USE_PERMISSION)) {
            player.sendMessage(LegacyText.colorize("&c你没有使用商店的权限。"));
            return false;
        }
        if (record == null || record.status() != ShopStatus.ACTIVE) {
            player.sendMessage(LegacyText.colorize("&c商店当前不可用。"));
            return false;
        }
        return true;
    }

    private void beginQuantityInput(Player player, ShopId id) {
        int timeout = configuration.snapshot().quantityTimeoutSeconds();
        putInput(player.getUniqueId(), new QuantityInput(id, expiresIn(timeout)), timeout);
        validateShopPosition(player, player.getLocation());
        if (!inputSessions.containsKey(player.getUniqueId())) return;
        player.sendMessage(LegacyText.colorize("&e请输入交易数量, 输入 cancel 取消。"));
    }

    private void sendTradeDetails(Player player, ShopRecord record) {
        ItemStack product = record.product();
        ItemMeta meta = product.getItemMeta();
        player.sendMessage(LegacyText.colorize("&7&m------------------------------"));
        player.sendMessage(LegacyText.colorize("&e商品详情"));
        player.sendMessage(itemDisplayName(product, meta));
        player.sendMessage(LegacyText.colorize("&7交易类型: &f" + displayType(record)));
        player.sendMessage(LegacyText.colorize("&7单件价格: &f" + record.price().toPlainString()));
        player.sendMessage(LegacyText.colorize("&7库存: &f" + displayStock(record)));
        if (meta != null && meta.hasLore() && meta.getLore() != null) {
            for (String line : meta.getLore()) {
                if (line != null) player.sendMessage(LegacyText.colorize(line));
            }
        }
        player.sendMessage(LegacyText.colorize("&7&m------------------------------"));
    }

    /**
     * Starts creating a shop from the item in the player's hand.
     *
     * @param player player
     * @param block  chest block
     */
    public void beginCreate(Player player, Block block) {
        beginCreate(player, block, true);
    }

    /**
     * Starts creating either a player shop or an administrator system shop.
     *
     * @param player    player creating the shop
     * @param block     chest block
     * @param adminShop whether the shop belongs to the system
     */
    public void beginCreate(Player player, Block block, boolean adminShop) {
        if (!adminShop || !player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage(LegacyText.colorize("&c只有管理员可以创建和管理系统商店。"));
            return;
        }
        if (!isChest(block) || find(block.getLocation()).isPresent()) return;
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (ItemMatcher.isEmpty(hand)) {
            player.sendMessage(LegacyText.colorize("&c请先手持要交易的物品。"));
            return;
        }
        ItemStack product = hand.clone();
        product.setAmount(1);
        int timeout = configuration.snapshot().quantityTimeoutSeconds();
        putInput(player.getUniqueId(), new CreatePriceInput(block.getLocation(), product, adminShop,
                        expiresIn(timeout)
                ), timeout
        );
        validateShopPosition(player, player.getLocation());
        if (!inputSessions.containsKey(player.getUniqueId())) return;
        player.sendMessage(LegacyText.colorize("&e请输入每件商品价格, 输入 cancel 取消。"));
    }

    /**
     * Returns whether the player has enabled WorldProtect's temporary building mode.
     *
     * @param player player to inspect
     * @return true when the optional WorldProtect edit mode is active
     */
    public boolean isBuildingMode(Player player) {
        return player != null && worldProtect.isEditMode(player.getUniqueId());
    }

    /**
     * Starts changing a shop's fixed unit price.
     *
     * @param player player
     * @param id     shop ID
     */
    public void beginPriceInput(Player player, ShopId id) {
        ShopRecord record = records.get(id);
        if (record == null || record.status() != ShopStatus.ACTIVE || !canManage(player, record)) return;
        int timeout = configuration.snapshot().quantityTimeoutSeconds();
        putInput(player.getUniqueId(), new PriceInput(id, expiresIn(timeout)), timeout);
        menus.close(player);
        validateShopPosition(player, player.getLocation());
        if (!inputSessions.containsKey(player.getUniqueId())) return;
        player.sendMessage(LegacyText.colorize("&e请输入新的每件价格, 输入 cancel 取消。"));
    }

    private void putInput(UUID playerId, InputSession session, int timeoutSeconds) {
        inputSessions.put(playerId, session);
        tasks.runLater(timeoutSeconds * 20L, () -> inputSessions.remove(playerId, session));
    }

    /**
     * Accepts one asynchronous chat input on the main thread.
     *
     * @param player player
     * @param text   raw input
     */
    public void acceptChatInput(Player player, String text) {
        validateShopPosition(player, player.getLocation());
        InputSession session = inputSessions.get(player.getUniqueId());
        if (session == null) return;
        if (session.expiresAt().isBefore(Instant.now())) {
            inputSessions.remove(player.getUniqueId());
            player.sendMessage(LegacyText.colorize("&c输入已超时。"));
            return;
        }
        if (text.trim().equalsIgnoreCase("cancel")) {
            inputSessions.remove(player.getUniqueId());
            player.sendMessage(LegacyText.colorize("&c交易已取消。"));
            return;
        }
        switch (session) {
            case QuantityInput quantity -> acceptQuantity(player, quantity, text);
            case CreatePriceInput create -> acceptCreatePrice(player, create, text);
            case PriceInput price -> acceptShopPrice(player, price, text);
            default -> {
            }
        }
    }

    private void acceptQuantity(Player player, QuantityInput session, String text) {
        int quantity;
        try {
            quantity = Integer.parseInt(text.trim());
        } catch (NumberFormatException exception) {
            player.sendMessage(LegacyText.colorize("&c请输入正整数。"));
            return;
        }
        if (quantity < 1 || quantity > configuration.snapshot().maximumQuantity()) {
            player.sendMessage(LegacyText.colorize("&c数量超出允许范围。"));
            return;
        }
        inputSessions.remove(player.getUniqueId());
        TradePreview preview = preview(new TradeRequest(session.shopId(), player.getUniqueId(), quantity));
        if (!preview.canCommit()) {
            invalidatePreview(preview);
            player.sendMessage(LegacyText.colorize("&c" + preview.message()));
            return;
        }
        TradeResult result = commit(new TradeConfirmation(preview.token(), player.getUniqueId()));
        player.sendMessage(LegacyText.colorize(result.status() == TradeResult.Status.SUCCESS
                ? "&a" + result.message() : "&c" + result.message()));
    }

    private void acceptCreatePrice(Player player, CreatePriceInput session, String text) {
        BigDecimal price = parsePrice(player, text);
        if (price == null) return;
        inputSessions.remove(player.getUniqueId());
        ShopCreateResult result = create(ShopCreateRequest.builder()
                .location(session.location())
                .owner(session.adminShop() ? null : player.getUniqueId())
                .product(session.product())
                .price(price)
                .adminShop(session.adminShop())
                .displayMode(configuration.snapshot().defaultDisplayMode())
                .build());
        player.sendMessage(LegacyText.colorize(result.status() == ShopCreateResult.Status.CREATED
                ? "&a商店已创建。" : "&c" + result.message()));
    }

    private void acceptShopPrice(Player player, PriceInput session, String text) {
        BigDecimal price = parsePrice(player, text);
        if (price == null) return;
        inputSessions.remove(player.getUniqueId());
        ShopRecord record = records.get(session.shopId());
        if (record == null || record.status() != ShopStatus.ACTIVE || !canManage(player, record)) {
            player.sendMessage(LegacyText.colorize("&c商店不存在。"));
            return;
        }
        record.setPrice(price);
        record.touch();
        repository.update(record);
        updateDisplay(record);
        menus.close(player);
        player.sendMessage(LegacyText.colorize("&a价格已更新。"));
    }

    private BigDecimal parsePrice(Player player, String text) {
        try {
            BigDecimal price = new BigDecimal(text.trim()).setScale(2, RoundingMode.UNNECESSARY);
            if (price.signum() <= 0) throw new NumberFormatException();
            return price;
        } catch (NumberFormatException | ArithmeticException exception) {
            player.sendMessage(LegacyText.colorize("&c请输入正数价格, 最多两位小数。"));
            return null;
        }
    }

    private Instant expiresIn(int seconds) {
        return Instant.now().plusSeconds(seconds);
    }

    /**
     * Opens a management menu after checking owner or administrator access.
     */
    public void openManagement(Player player, ShopId id) {
        ShopRecord record = records.get(id);
        if (record == null || record.status() == ShopStatus.DELETED) return;
        if (!canManage(player, record)) {
            player.sendMessage(LegacyText.colorize("&c你没有管理这个商店的权限。"));
            return;
        }
        menus.open(player, new ShopManagementMenu(this, id));
        managementSessions.put(player.getUniqueId(), id);
        validateShopPosition(player, player.getLocation());
    }

    /**
     * Returns from a DreamShop setting panel to Lib's shared administrator panel.
     *
     * @param player administrator
     * @param id     shop being managed
     */
    public void openAdminOverview(Player player, ShopId id) {
        ShopRecord record = records.get(id);
        if (!canManage(player, record)) {
            menus.close(player);
            return;
        }
        World world = Bukkit.getWorld(id.world());
        if (world == null) {
            menus.close(player);
            player.sendMessage(LegacyText.colorize("&c商店所在世界当前不可用。"));
            return;
        }
        adminPanels.openOverview(player, world.getBlockAt(id.x(), id.y(), id.z()));
    }

    /**
     * Opens the read-only information menu.
     */
    public void openInfo(Player player, ShopId id) {
        if (!player.hasPermission(USE_PERMISSION)) {
            player.sendMessage(LegacyText.colorize("&c你没有使用商店的权限。"));
            return;
        }
        ShopRecord record = records.get(id);
        if (record != null && record.status() == ShopStatus.ACTIVE) {
            menus.open(player, new ShopInfoMenu(this, id));
            infoSessions.put(player.getUniqueId(), id);
            validateShopPosition(player, player.getLocation());
        }
    }

    /**
     * Opens safe stock management for a shop owner or administrator.
     */
    public void openStock(Player player, ShopId id) {
        ShopRecord record = records.get(id);
        if (record == null || record.status() != ShopStatus.ACTIVE || !canManage(player, record)) return;
        if (record.stockMode() == StockMode.UNLIMITED) {
            player.sendMessage(LegacyText.colorize("&7无限库存商店不需要管理库存。"));
            return;
        }
        ShopStockInventorySession previous = stockInventorySessions.get(player.getUniqueId());
        if (previous != null) saveNativeStockInventory(player, previous.inventory());
        stockSessions.put(player.getUniqueId(), id);
        ShopStockInventoryHolder holder = new ShopStockInventoryHolder(id, player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(
                holder,
                record.stockSize(),
                LegacyText.colorize("&8DreamShop 库存")
        );
        holder.attach(inventory);
        inventory.setContents(record.stockContents());
        stockInventorySessions.put(
                player.getUniqueId(),
                new ShopStockInventorySession(holder, inventory)
        );
        player.openInventory(inventory);
        validateShopPosition(player, player.getLocation());
    }

    public boolean canManage(Player player, ShopRecord record) {
        return player != null && record != null && record.status() != ShopStatus.DELETED
                && player.hasPermission(ADMIN_PERMISSION);
    }

    public boolean canManage(Player player, ShopId id) {
        return canManage(player, records.get(id));
    }

    private ShopRecord record(ShopId id) {
        ShopRecord record = records.get(id);
        if (record == null) throw new IllegalStateException("Shop record disappeared: " + id.encoded());
        return record;
    }

    public void cycleType(Player player, ShopId id, boolean next) {
        ShopRecord record = record(id);
        if (!canManage(player, record)) return;
        ShopType[] values = ShopType.values();
        int index = (record.type().ordinal() + (next ? 1 : values.length - 1)) % values.length;
        record.setType(values[index]);
        record.touch();
        repository.update(record);
        updateDisplay(record);
        refreshManagement(id);
    }

    public void cycleStockMode(Player player, ShopId id, boolean next) {
        ShopRecord record = record(id);
        if (!canManage(player, record)) return;
        StockMode[] values = StockMode.values();
        int index = (record.stockMode().ordinal() + (next ? 1 : values.length - 1)) % values.length;
        record.setStockMode(values[index]);
        record.touch();
        repository.update(record);
        refreshManagement(id);
    }

    public void toggleDisplayItem(Player player, ShopId id) {
        ShopRecord record = record(id);
        if (!canManage(player, record)) return;
        record.setDisplayMode(record.displayMode() == DisplayMode.SIGN_AND_ITEM
                ? DisplayMode.SIGN : DisplayMode.SIGN_AND_ITEM);
        record.touch();
        repository.update(record);
        updateDisplay(record);
        refreshManagement(id);
    }

    public void closeManagement(Player player, ShopId id) {
        managementSessions.remove(player.getUniqueId(), id);
    }

    public void closeStock(Player player, ShopId id) {
        stockSessions.remove(player.getUniqueId(), id);
        ShopStockInventorySession session = stockInventorySessions.get(player.getUniqueId());
        if (session != null && session.holder().shopId().equals(id)) closeNativeStockInventory(player);
    }

    public void closeInfo(Player player, ShopId id) {
        infoSessions.remove(player.getUniqueId(), id);
    }

    /**
     * Returns whether the top inventory is a DreamShop native stock editor for this player.
     */
    public boolean isNativeStockInventory(Inventory inventory, UUID viewer) {
        if (inventory == null || viewer == null) return false;
        ShopStockInventorySession session = stockInventorySessions.get(viewer);
        return session != null && session.inventory() == inventory
                && session.holder().viewer().equals(viewer);
    }

    /**
     * Returns whether the player may mutate a native stock editor.
     */
    public boolean canEditNativeStock(Player player, Inventory inventory) {
        if (player == null || !isNativeStockInventory(inventory, player.getUniqueId())) return false;
        ShopStockInventorySession session = stockInventorySessions.get(player.getUniqueId());
        ShopRecord record = session == null ? null : records.get(session.holder().shopId());
        return record != null && record.status() == ShopStatus.ACTIVE
                && record.stockMode() != StockMode.UNLIMITED
                && canManage(player, record);
    }

    /**
     * Persists a native stock editor when Bukkit closes its inventory view.
     */
    public void saveNativeStockInventory(Player player, Inventory inventory) {
        if (player == null || !isNativeStockInventory(inventory, player.getUniqueId())) return;
        UUID viewer = player.getUniqueId();
        ShopStockInventorySession session = stockInventorySessions.remove(viewer);
        stockSessions.remove(viewer, session.holder().shopId());
        saveNativeStockSession(session);
    }

    private void closeNativeStockInventory(Player player) {
        UUID viewer = player.getUniqueId();
        ShopStockInventorySession session = stockInventorySessions.remove(viewer);
        if (session == null) return;
        stockSessions.remove(viewer, session.holder().shopId());
        saveNativeStockSession(session);
        if (player.getOpenInventory().getTopInventory() == session.inventory()) player.closeInventory();
    }

    private void saveNativeStockSession(ShopStockInventorySession session) {
        ShopRecord record = records.get(session.holder().shopId());
        if (record == null || record.status() == ShopStatus.DELETED) return;
        if (!canUseStockSize(session.inventory().getSize())) return;
        record.setStockContents(snapshotContents(session.inventory()));
        record.touch();
        repository.update(record);
    }

    public void completePreview(Player player, TradePreview preview) {
        TradeResult result = commit(new TradeConfirmation(preview.token(), player.getUniqueId()));
        player.sendMessage(LegacyText.colorize(result.status() == TradeResult.Status.SUCCESS
                ? "&a" + result.message() : "&c" + result.message()));
        menus.close(player);
    }

    public void cancelPreview(Player player) {
        UUID playerId = player.getUniqueId();
        pendingPreviews.entrySet().removeIf(entry -> entry.getValue().request().player().equals(playerId));
        menus.close(player);
    }

    /**
     * Cancels a preview and invalidates its one-time confirmation token.
     *
     * @param player  player viewing the preview
     * @param preview preview to invalidate
     */
    public void cancelPreview(Player player, TradePreview preview) {
        invalidatePreview(preview);
        menus.close(player);
    }

    /**
     * Invalidates a preview without changing the player's current inventory view.
     *
     * @param preview preview to invalidate
     */
    public void invalidatePreview(TradePreview preview) {
        if (preview != null) pendingPreviews.remove(preview.token());
    }

    public void refill(Player player, ShopId id, int amount) {
        if (amount < 1) return;
        ShopRecord record = record(id);
        synchronized (record) {
            if (record.status() != ShopStatus.ACTIVE
                    || !canManage(player, record)
                    || record.stockMode() == StockMode.UNLIMITED) return;
            int moved = ItemMatcher.remove(player.getInventory(), record, mm, amount);
            Inventory stock = managedInventory(record);
            int added = ItemMatcher.add(stock, record, mm, moved);
            if (added < moved) ItemMatcher.add(player.getInventory(), record, mm, moved - added);
            persistManagedStock(record, stock);
            player.sendMessage(LegacyText.colorize("&a已补货 " + added + " 件。"));
        }
    }

    public void take(Player player, ShopId id, int amount) {
        if (amount < 1) return;
        ShopRecord record = record(id);
        synchronized (record) {
            if (record.status() != ShopStatus.ACTIVE
                    || !canManage(player, record)
                    || record.stockMode() == StockMode.UNLIMITED) return;
            Inventory stock = managedInventory(record);
            int removed = ItemMatcher.remove(stock, record, mm, amount);
            int added = ItemMatcher.add(player.getInventory(), record, mm, removed);
            if (added < removed) ItemMatcher.add(stock, record, mm, removed - added);
            persistManagedStock(record, stock);
            player.sendMessage(LegacyText.colorize("&a已取出 " + added + " 件。"));
        }
    }

    public Optional<ShopId> shopAt(Block block) {
        return Optional.ofNullable(resolveId(block == null ? null : block.getLocation()))
                .map(records::get)
                .filter(record -> record.status() != ShopStatus.DELETED)
                .map(ShopRecord::id);
    }

    /**
     * Finds the shop whose generated wall sign was clicked.
     *
     * @param block clicked block
     * @return matching shop, or empty for an unrelated sign
     */
    public Optional<ShopId> shopAtSign(Block block) {
        if (block == null || block.getType() != Material.WALL_SIGN
                || !(block.getState() instanceof Sign sign)) return Optional.empty();
        org.bukkit.material.Sign data = (org.bukkit.material.Sign) sign.getData();
        if (!data.isWallSign()) return Optional.empty();
        BlockFace attachedFace = data.getAttachedFace();
        if (attachedFace == null) return Optional.empty();
        return shopAt(block.getRelative(attachedFace));
    }

    /**
     * Returns whether a player is waiting for a quantity or price input.
     *
     * @param player player UUID
     * @return true when a chat session exists
     */
    public boolean hasChatInput(UUID player) {
        return inputSessions.containsKey(player);
    }

    /**
     * Clears all sessions for one player and closes its managed menu.
     *
     * @param player player to clean up
     */
    public void closePlayer(Player player) {
        UUID uniqueId = player.getUniqueId();
        boolean hadInput = inputSessions.remove(uniqueId) != null;
        boolean hadPreview = pendingPreviews.entrySet()
                .removeIf(entry -> entry.getValue().request().player().equals(uniqueId));
        boolean hadManagement = managementSessions.remove(uniqueId) != null;
        boolean hadStock = stockSessions.remove(uniqueId) != null;
        boolean hadInfo = infoSessions.remove(uniqueId) != null;
        boolean hadNativeStock = stockInventorySessions.containsKey(uniqueId);
        if (hadNativeStock) closeNativeStockInventory(player);
        if (hadInput || hadPreview || hadManagement || hadStock || hadInfo || hadNativeStock) {
            menus.close(player);
        }
    }

    private Optional<ShopId> activeInteractionShop(UUID playerId) {
        InputSession input = inputSessions.get(playerId);
        if (input instanceof QuantityInput quantity) return Optional.of(quantity.shopId());
        if (input instanceof PriceInput price) return Optional.of(price.shopId());
        if (input instanceof CreatePriceInput create) return Optional.of(ShopId.from(create.location()));
        ShopId stock = stockSessions.get(playerId);
        if (stock != null) return Optional.of(stock);
        ShopId management = managementSessions.get(playerId);
        if (management != null) return Optional.of(management);
        ShopId info = infoSessions.get(playerId);
        if (info != null) return Optional.of(info);
        return pendingPreviews.values().stream()
                .filter(pending -> pending.request().player().equals(playerId))
                .map(pending -> pending.request().shopId())
                .findFirst();
    }

    private void cancelShopInteraction(Player player) {
        UUID playerId = player.getUniqueId();
        InputSession input = inputSessions.get(playerId);
        boolean trade = input instanceof QuantityInput || pendingPreviews.values().stream()
                .anyMatch(pending -> pending.request().player().equals(playerId));
        closePlayer(player);
        player.sendMessage(LegacyText.colorize(trade ? PURCHASE_CANCELLED_MESSAGE : "&c商店操作已取消。"));
    }

    private sealed interface InputSession permits QuantityInput, CreatePriceInput, PriceInput {
        Instant expiresAt();
    }

    private record PendingPreview(TradePreview preview, TradeRequest request) {
    }

    private record QuantityInput(ShopId shopId, Instant expiresAt) implements InputSession {
    }

    private record CreatePriceInput(org.bukkit.Location location, ItemStack product, boolean adminShop,
                                    Instant expiresAt)
            implements InputSession {

        private CreatePriceInput {
            location = location.clone();
            product = product.clone();
        }

        @Override
        public org.bukkit.Location location() {
            return location.clone();
        }

        @Override
        public ItemStack product() {
            return product.clone();
        }
    }

    private record PriceInput(ShopId shopId, Instant expiresAt) implements InputSession {
    }

    private void requirePrimaryThread() {
        MainThread.require("DreamShop API requires the Bukkit main thread");
    }

}
