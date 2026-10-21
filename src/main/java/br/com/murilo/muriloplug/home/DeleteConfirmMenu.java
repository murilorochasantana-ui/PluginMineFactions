package br.com.murilo.muriloplug.home;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Menu de confirmação "Apagar <nome>?". Guarda no holder o nome da home que será apagada. */
public final class DeleteConfirmMenu implements InventoryHolder {

    public static final int CANCEL_SLOT = 3;
    public static final int HOME_SLOT = 4;
    public static final int CONFIRM_SLOT = 5;

    private static final int SLOTS = 9;

    private final Inventory inventory;
    private final String homeName;

    private DeleteConfirmMenu(Home home, int number) {
        this.homeName = home.name();
        this.inventory = Bukkit.createInventory(this, SLOTS, Component.text("Apagar " + home.name() + "?"));
        inventory.setItem(CANCEL_SLOT, button(Material.RED_CONCRETE, "Cancelar", NamedTextColor.RED));
        inventory.setItem(HOME_SLOT, HomesMenu.bed(home, number, "Esta home será apagada", NamedTextColor.RED));
        inventory.setItem(CONFIRM_SLOT, button(Material.LIME_CONCRETE, "Apagar", NamedTextColor.GREEN));
    }

    /** Abre a confirmação; {@code allHomes} serve só para descobrir o número da home (quantidade da cama). */
    public static void open(Player player, Home home, List<Home> allHomes) {
        int number = 1;
        for (int i = 0; i < allHomes.size(); i++) {
            if (allHomes.get(i).name().equalsIgnoreCase(home.name())) {
                number = i + 1;
                break;
            }
        }
        player.openInventory(new DeleteConfirmMenu(home, number).getInventory());
    }

    public String homeName() {
        return homeName;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private static ItemStack button(Material material, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(HomesMenu.line(name, color));
        item.setItemMeta(meta);
        return item;
    }
}
