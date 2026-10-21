package br.com.murilo.muriloplug.claim;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** O menu "Meus Claims" é só de consulta: nada pode ser tirado, movido ou colocado. */
public final class ClaimsMenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ClaimsMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ClaimsMenu) {
            event.setCancelled(true);
        }
    }
}
