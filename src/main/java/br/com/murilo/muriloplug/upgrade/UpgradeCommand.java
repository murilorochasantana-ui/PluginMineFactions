package br.com.murilo.muriloplug.upgrade;

import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** /upgrade: por enquanto só o menu "Upgrades da Base" com o aviso "Em breve" (protegido: nada sai dele). */
public final class UpgradeCommand implements CommandExecutor, Listener {

    private static final int CENTER_SLOT = 13;

    /** Marca o menu de upgrades: todo clique e arrasto com ele aberto é cancelado. */
    private static final class Menu implements InventoryHolder {

        private final Inventory inventory;

        Menu() {
            this.inventory = Bukkit.createInventory(this, 27, Component.text("Upgrades da Base"));
            inventory.setItem(CENTER_SLOT, ShopUi.button(Material.BARRIER, "§eEm breve",
                    "§7O sistema de upgrades ainda está sendo feito."));
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem usar este comando.");
            return true;
        }
        player.openInventory(new Menu().getInventory());
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }
}
