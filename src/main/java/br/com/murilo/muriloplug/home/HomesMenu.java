package br.com.murilo.muriloplug.home;

import br.com.murilo.muriloplug.claim.ClaimManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Menu "Minhas Homes". O holder próprio identifica o inventário, sem depender do título. */
public final class HomesMenu implements InventoryHolder {

    private static final int SLOTS = 9;

    private final Inventory inventory;
    private final List<Home> homes;

    private HomesMenu(Player player, List<Home> homes, ClaimManager claims) {
        this.homes = homes;
        this.inventory = Bukkit.createInventory(this, SLOTS, Component.text("Minhas Homes"));
        for (int i = 0; i < homes.size() && i < SLOTS; i++) {
            Location location = homes.get(i).toLocation();
            boolean blocked = location != null && !claims.canBuild(player, location);
            inventory.setItem(i, bed(homes.get(i), i + 1, "Clique para teleportar", NamedTextColor.GREEN, blocked));
        }
    }

    public static void open(Player player, List<Home> homes, ClaimManager claims) {
        player.openInventory(new HomesMenu(player, homes, claims).getInventory());
    }

    /** Home exibida no slot, ou null se o slot não tiver uma home. */
    public Home homeAt(int slot) {
        return slot >= 0 && slot < homes.size() ? homes.get(slot) : null;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    static ItemStack bed(Home home, int amount, String action, NamedTextColor actionColor) {
        return bed(home, amount, action, actionColor, false);
    }

    static ItemStack bed(Home home, int amount, String action, NamedTextColor actionColor, boolean blocked) {
        ItemStack item = new ItemStack(Material.RED_BED, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(line(home.name(), NamedTextColor.YELLOW));
        List<Component> lore = new ArrayList<>(List.of(
                line("Mundo: " + home.world(), NamedTextColor.GRAY),
                line("X: " + Math.round(home.x()) + "  Y: " + Math.round(home.y()) + "  Z: " + Math.round(home.z()),
                        NamedTextColor.GRAY),
                line(action, actionColor)));
        if (blocked) {
            lore.add(line("Bloqueada: dentro do claim de outro jogador", NamedTextColor.RED));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
