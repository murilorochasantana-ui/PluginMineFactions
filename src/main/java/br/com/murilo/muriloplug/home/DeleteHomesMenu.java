package br.com.murilo.muriloplug.home;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

/** Menu "Apagar Home": lista as homes; clicar em uma abre a confirmação. */
public final class DeleteHomesMenu implements InventoryHolder {

    private static final int SLOTS = 9;

    private final Inventory inventory;
    private final List<Home> homes;

    private DeleteHomesMenu(List<Home> homes) {
        this.homes = homes;
        this.inventory = Bukkit.createInventory(this, SLOTS, Component.text("Apagar Home"));
        for (int i = 0; i < homes.size() && i < SLOTS; i++) {
            inventory.setItem(i, HomesMenu.bed(homes.get(i), i + 1, "Clique para apagar", NamedTextColor.RED));
        }
    }

    public static void open(Player player, List<Home> homes) {
        player.openInventory(new DeleteHomesMenu(homes).getInventory());
    }

    /** Home exibida no slot, ou null se o slot não tiver uma home. */
    public Home homeAt(int slot) {
        return slot >= 0 && slot < homes.size() ? homes.get(slot) : null;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
