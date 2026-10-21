package br.com.murilo.muriloplug.classes;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Cliques do menu de kits. Todo clique e arrasto é cancelado: nada pode ser pego, movido ou colocado. */
public final class KitMenusListener implements Listener {

    private final ClassItems items;

    public KitMenusListener(ClassItems items) {
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof KitMenu menu)) {
            return;
        }
        // Cancela tudo (shift-click, teclas 1-9, tecla F, clique duplo, cliques no inventário do jogador).
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        PlayerClass clicked = menu.classAt(event.getSlot());
        if (clicked == null) {
            return;
        }
        if (!player.hasPermission(KitCommand.PERMISSION)) {
            player.sendMessage("§cVocê não tem permissão.");
            return;
        }
        give(player, clicked);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof KitMenu) {
            event.setCancelled(true);
        }
    }

    /** Coloca o kit no inventário; o que não couber cai aos pés do jogador. */
    private void give(Player player, PlayerClass playerClass) {
        List<ItemStack> kit = items.getItems(playerClass);
        boolean dropped = false;
        for (ItemStack item : kit) {
            for (ItemStack leftover : player.getInventory().addItem(item).values()) {
                player.getWorld().dropItem(player.getLocation(), leftover);
                dropped = true;
            }
        }
        if (dropped) {
            player.sendMessage("§eInventário cheio: alguns itens caíram no chão.");
        }
        player.sendMessage("§aVocê pegou o kit de " + playerClass.coloredName() + "§a.");
    }
}
