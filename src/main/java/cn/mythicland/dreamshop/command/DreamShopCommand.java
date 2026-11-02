package cn.mythicland.dreamshop.command;

import cn.mythicland.dreamshop.DreamShopPlugin;
import cn.mythicland.dreamshop.api.ShopId;
import cn.mythicland.dreamshop.service.ShopService;
import cn.mythicland.framework.bukkit.command.CommandBinding;
import cn.mythicland.framework.bukkit.command.CommandContext;
import cn.mythicland.framework.bukkit.command.CommandRoute;
import cn.mythicland.framework.bukkit.text.LegacyText;

import java.util.Objects;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Annotation-driven DreamShop command tree.
 */
@CommandBinding("dreamshop")
public final class DreamShopCommand {

    private static final String ROOT = "/dreamshop";

    private final ShopService service;
    private final DreamShopPlugin plugin;

    public DreamShopCommand(ShopService service, DreamShopPlugin plugin) {
        this.service = Objects.requireNonNull(service, "service");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @CommandRoute(
            path = {"dreamshop", "create"},
            maxArguments = Integer.MAX_VALUE,
            permission = ShopService.ADMIN_PERMISSION,
            usages = ROOT + " create"
    )
    private void create(CommandContext context) {
        Player player = player(context.sender());
        Block block = targetBlock(player);
        if (block == null) {
            player.sendMessage(LegacyText.colorize("&c请看向一个箱子。"));
            return;
        }
        service.beginCreate(player, block, true);
    }

    @CommandRoute(
            path = {"dreamshop", "admin-create"},
            maxArguments = Integer.MAX_VALUE,
            permission = ShopService.ADMIN_PERMISSION,
            usages = ROOT + " admin-create"
    )
    private void adminCreate(CommandContext context) {
        Player player = player(context.sender());
        Block block = targetBlock(player);
        if (block == null) {
            player.sendMessage(LegacyText.colorize("&c请看向一个箱子。"));
            return;
        }
        service.beginCreate(player, block, true);
    }

    @CommandRoute(
            path = {"dreamshop", "manage"},
            maxArguments = Integer.MAX_VALUE,
            permission = ShopService.ADMIN_PERMISSION,
            usages = ROOT + " manage"
    )
    private void manage(CommandContext context) {
        Player player = player(context.sender());
        ShopId id = targetShop(player);
        if (id != null) service.openManagement(player, id);
    }

    @CommandRoute(
            path = {"dreamshop", "info"},
            maxArguments = Integer.MAX_VALUE,
            permission = ShopService.USE_PERMISSION,
            usages = ROOT + " info"
    )
    private void info(CommandContext context) {
        Player player = player(context.sender());
        ShopId id = targetShop(player);
        if (id != null) service.openInfo(player, id);
    }

    @CommandRoute(
            path = {"dreamshop", "refill"},
            maxArguments = Integer.MAX_VALUE,
            permission = ShopService.ADMIN_PERMISSION,
            usages = ROOT + " refill <数量>"
    )
    private void refill(CommandContext context) {
        context.requireArguments(1);
        int amount = positiveInteger(context.argument(0), context);
        Player player = player(context.sender());
        ShopId id = targetShop(player);
        if (id != null) service.refill(player, id, amount);
    }

    @CommandRoute(
            path = {"dreamshop", "take"},
            maxArguments = Integer.MAX_VALUE,
            permission = ShopService.ADMIN_PERMISSION,
            usages = ROOT + " take <数量>"
    )
    private void take(CommandContext context) {
        context.requireArguments(1);
        int amount = positiveInteger(context.argument(0), context);
        Player player = player(context.sender());
        ShopId id = targetShop(player);
        if (id != null) service.take(player, id, amount);
    }

    @CommandRoute(
            path = {"dreamshop", "delete"},
            maxArguments = Integer.MAX_VALUE,
            permission = ShopService.ADMIN_PERMISSION,
            usages = ROOT + " delete"
    )
    private void delete(CommandContext context) {
        Player player = player(context.sender());
        ShopId id = targetShop(player);
        if (id == null) return;
        player.sendMessage(LegacyText.colorize("&e请使用管理面板中的删除按钮完成二次确认。"));
        service.openManagement(player, id);
    }

    @CommandRoute(
            path = {"dreamshop", "reload"},
            maxArguments = Integer.MAX_VALUE,
            permission = "dreamshop.reload",
            usages = ROOT + " reload"
    )
    private void reload(CommandContext context) {
        plugin.reloadDreamShop();
        context.sender().sendMessage(LegacyText.colorize("&aDreamShop 配置已重载。"));
    }

    private static Player player(CommandSender sender) {
        if (!(sender instanceof Player player)) throw new IllegalStateException("Only players can use this command");
        return player;
    }

    private ShopId targetShop(Player player) {
        Block target = targetBlock(player);
        if (target == null) {
            player.sendMessage(LegacyText.colorize("&c请看向一个商店箱子。"));
            return null;
        }
        return service.shopAt(target).orElseGet(() -> {
            player.sendMessage(LegacyText.colorize("&c这里没有 DreamShop 商店。"));
            return null;
        });
    }

    @SuppressWarnings("deprecation")
    private static Block targetBlock(Player player) {
        return player.getTargetBlock((Set<Material>) null, 6);
    }

    private static int positiveInteger(String value, CommandContext context) {
        try {
            int number = Integer.parseInt(value);
            if (number > 0) return number;
        } catch (NumberFormatException ignored) {
            // Usage exception below provides the command's normal error response.
        }
        throw context.invalidUsage();
    }
}
