package cn.mythicland.dreamshop.command;

import cn.mythicland.dreamlib.bootstrap.annotation.CommandComponent;
import cn.mythicland.dreamlib.bootstrap.annotation.CommandHandler;
import cn.mythicland.dreamlib.command.CommandContext;
import cn.mythicland.dreamlib.text.LegacyText;
import cn.mythicland.dreamshop.api.ShopId;
import cn.mythicland.dreamshop.service.ShopService;
import cn.mythicland.dreamshop.DreamShopPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Set;

/**
 * Annotation-driven DreamShop command tree.
 */
@CommandComponent("dreamshop")
public final class DreamShopCommand {

    private static final String ROOT = "/dreamshop";

    private final ShopService service;
    private final DreamShopPlugin plugin;

    public DreamShopCommand(ShopService service, DreamShopPlugin plugin) {
        this.service = Objects.requireNonNull(service, "service");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @CommandHandler(value = "create", permission = ShopService.ADMIN_PERMISSION, usage = ROOT + " create")
    private void create(CommandContext context) {
        Player player = player(context.sender());
        Block block = targetBlock(player);
        if (block == null) {
            player.sendMessage(LegacyText.colorize("&c请看向一个箱子。"));
            return;
        }
        service.beginCreate(player, block, true);
    }

    @CommandHandler(
            value = "admin-create",
            permission = ShopService.ADMIN_PERMISSION,
            usage = ROOT + " admin-create"
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

    @CommandHandler(
            value = "manage",
            permission = ShopService.ADMIN_PERMISSION,
            usage = ROOT + " manage"
    )
    private void manage(CommandContext context) {
        Player player = player(context.sender());
        ShopId id = targetShop(player);
        if (id != null) service.openManagement(player, id);
    }

    @CommandHandler(value = "info", permission = ShopService.USE_PERMISSION, usage = ROOT + " info")
    private void info(CommandContext context) {
        Player player = player(context.sender());
        ShopId id = targetShop(player);
        if (id != null) service.openInfo(player, id);
    }

    @CommandHandler(value = "refill", permission = ShopService.ADMIN_PERMISSION, usage = ROOT + " refill <数量>")
    private void refill(CommandContext context) {
        context.requireArguments(1);
        int amount = positiveInteger(context.argument(0), context);
        Player player = player(context.sender());
        ShopId id = targetShop(player);
        if (id != null) service.refill(player, id, amount);
    }

    @CommandHandler(value = "take", permission = ShopService.ADMIN_PERMISSION, usage = ROOT + " take <数量>")
    private void take(CommandContext context) {
        context.requireArguments(1);
        int amount = positiveInteger(context.argument(0), context);
        Player player = player(context.sender());
        ShopId id = targetShop(player);
        if (id != null) service.take(player, id, amount);
    }

    @CommandHandler(value = "delete", permission = ShopService.ADMIN_PERMISSION, usage = ROOT + " delete")
    private void delete(CommandContext context) {
        Player player = player(context.sender());
        ShopId id = targetShop(player);
        if (id == null) return;
        player.sendMessage(LegacyText.colorize("&e请使用管理面板中的删除按钮完成二次确认。"));
        service.openManagement(player, id);
    }

    @CommandHandler(value = "reload", permission = "dreamshop.reload", usage = ROOT + " reload")
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
