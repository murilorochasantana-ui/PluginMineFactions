package br.com.murilo.muriloplug.home;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

/** Os menus de apagar home são só de consulta: nada pode ser tirado, movido ou colocado. */
public final class DeleteMenusListener implements Listener {

    private final JavaPlugin plugin;
    private final HomeManager homes;

    public DeleteMenusListener(JavaPlugin plugin, HomeManager homes) {
        this.plugin = plugin;
        this.homes = homes;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof DeleteHomesMenu) && !(holder instanceof DeleteConfirmMenu)) {
            return;
        }
        // Cancela qualquer clique com o menu aberto (inclusive shift-click e cliques no inventário do jogador).
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        if (holder instanceof DeleteHomesMenu list) {
            Home clicked = list.homeAt(event.getSlot());
            if (clicked != null) {
                openConfirmation(player, clicked.name());
            }
        } else if (holder instanceof DeleteConfirmMenu confirm) {
            switch (event.getSlot()) {
                case DeleteConfirmMenu.CANCEL_SLOT -> {
                    closeLater(player);
                    player.sendMessage("§eNada foi apagado.");
                }
                case DeleteConfirmMenu.CONFIRM_SLOT -> {
                    delete(player, confirm.homeName());
                    closeLater(player);
                }
                default -> { }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof DeleteHomesMenu || holder instanceof DeleteConfirmMenu) {
            event.setCancelled(true);
        }
    }

    private void delete(Player player, String name) {
        Home home = homes.getByName(player.getUniqueId(), name);
        if (home != null && homes.remove(player.getUniqueId(), home.name())) {
            player.sendMessage("§aHome §f" + home.name() + "§a apagada.");
        } else {
            player.sendMessage("§cA home " + name + " não existe mais.");
        }
    }

    /** Abre a confirmação no tick seguinte, fora do evento de clique. */
    private void openConfirmation(Player player, String name) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Home home = homes.getByName(player.getUniqueId(), name);
            if (home == null) {
                player.closeInventory();
                player.sendMessage("§cA home " + name + " não existe mais.");
            } else {
                DeleteConfirmMenu.open(player, home, homes.getByOwner(player.getUniqueId()));
            }
        });
    }

    private void closeLater(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
    }
}
