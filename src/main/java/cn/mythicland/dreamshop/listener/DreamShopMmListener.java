package cn.mythicland.dreamshop.listener;

import cn.mythicland.framework.bukkit.task.PluginTaskScope;
import cn.mythicland.dreamshop.service.ShopService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event;

import java.util.Objects;

/**
 * Refreshes MM-backed shop products when DreamRPG item service publishes its change event.
 */
public final class DreamShopMmListener implements Listener {

    private static final String EVENT_NAME =
            "cn.mythicland.dreamrpgitems.api.MythicItemsChangedEvent";

    private static final String SYNC_EVENT_NAME =
            "cn.mythicland.dreamrpgitems.api.MythicItemsSyncRequestedEvent";

    private final ShopService service;
    private final PluginTaskScope tasks;

    public DreamShopMmListener(ShopService service, PluginTaskScope tasks) {
        this.service = Objects.requireNonNull(service, "service");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEvent(Event event) {
        if (!EVENT_NAME.equals(event.getClass().getName()) && !SYNC_EVENT_NAME.equals(event.getClass().getName()))
            return;
        tasks.runLater(0L, service::onMythicItemsChanged);
    }
}
