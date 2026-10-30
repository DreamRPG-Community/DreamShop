package cn.mythicland.dreamshop.listener;

import cn.mythicland.dreamlib.bootstrap.PluginTaskScope;
import cn.mythicland.dreamlib.bootstrap.annotation.ListenerComponent;
import cn.mythicland.dreamshop.service.ShopService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Objects;

/**
 * Captures only chat messages belonging to an active DreamShop input session.
 */
@ListenerComponent
public final class DreamShopChatListener implements Listener {

    private final ShopService service;
    private final PluginTaskScope tasks;

    public DreamShopChatListener(ShopService service, PluginTaskScope tasks) {
        this.service = Objects.requireNonNull(service, "service");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!service.hasChatInput(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        String message = event.getMessage();
        tasks.runLater(0L, () -> service.acceptChatInput(event.getPlayer(), message));
    }
}
