package br.com.murilo.muriloplug.classes;

import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Menu "Kit de Classe (admin)": uma entrada por classe que tem itens no registro. */
public final class KitMenu implements InventoryHolder {

    private static final int SLOTS = 27;
    private static final int CENTER_SLOT = 13;
    static final int SERVER_ITEMS_SLOT = 22;

    private final Inventory inventory;
    private final List<PlayerClass> classes;

    private KitMenu(ClassItems items, ServerItems serverItems) {
        this.inventory = Bukkit.createInventory(this, SLOTS, Component.text("Kit de Classe (admin)"));
        this.classes = items.classesWithItems();
        // Uma entrada a cada 2 slots, centralizadas na fileira do meio.
        int first = CENTER_SLOT - (classes.size() - 1);
        for (int i = 0; i < classes.size(); i++) {
            inventory.setItem(first + i * 2, item(classes.get(i), items.kitSize(classes.get(i))));
        }
        inventory.setItem(SERVER_ITEMS_SLOT, ShopUi.button(org.bukkit.Material.CHEST, "§eItens do Servidor",
                "§7Itens que não são de classe: Núcleo,", "§7Cofre e Baú do Clan, Contratos,",
                "§7dinheiro, upgrades e máquinas.", "§eClique para abrir"));
    }

    public static void open(Player player, ClassItems items, ServerItems serverItems) {
        player.openInventory(new KitMenu(items, serverItems).getInventory());
    }

    /** Classe que está no slot, ou null. */
    PlayerClass classAt(int slot) {
        int first = CENTER_SLOT - (classes.size() - 1);
        int offset = slot - first;
        if (offset < 0 || offset % 2 != 0 || offset / 2 >= classes.size()) {
            return null;
        }
        return classes.get(offset / 2);
    }

    private static ItemStack item(PlayerClass playerClass, int amount) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Itens: " + amount);
        lore.add("§eClique para pegar todos");
        ItemStack item = ShopUi.button(playerClass.icon(), playerClass.coloredName(), lore.toArray(new String[0]));
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
