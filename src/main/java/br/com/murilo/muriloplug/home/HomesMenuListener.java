package br.com.murilo.muriloplug.home;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** O menu "Minhas Homes" é só de consulta: nada pode ser tirado, movido ou colocado. */
public final class HomesMenuListener implements Listener {

    private final JavaPlugin plugin;
    private final HomeManager homes;
    private final HomeTeleportManager teleports;

    public HomesMenuListener(JavaPlugin plugin, HomeManager homes, HomeTeleportManager teleports) {
        this.plugin = plugin;
        this.homes = homes;
        this.teleports = teleports;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof HomesMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != menu.getInventory()) {
            return;
        }
        Home clicked = menu.homeAt(event.getSlot());
        if (clicked == null) {
            return;
        }
        // Fecha o baú e inicia o teleporte no tick seguinte, fora do evento de clique.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.closeInventory();
            Home current = homes.getByName(player.getUniqueId(), clicked.name());
            if (current != null) {
                teleports.start(player, current);
            }
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof HomesMenu) {
            event.setCancelled(true);
        }
    }
}
