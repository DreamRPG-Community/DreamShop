package cn.mythicland.dreamshop.listener;

import cn.mythicland.dreamshop.service.ShopService;
import cn.mythicland.framework.bukkit.component.ListenerComponent;
import cn.mythicland.framework.bukkit.task.PluginTaskScope;
import cn.mythicland.framework.component.InjectComponent;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Captures only chat messages belonging to an active DreamShop input session.
 */
@InjectComponent
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
