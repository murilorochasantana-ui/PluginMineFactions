package br.com.murilo.muriloplug.classes;

import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Menu "Classes": as 5 classes nos slots 11 a 15. */
public final class ClassMenu implements ClassHolder {

    private static final int SLOTS = 27;
    private static final int FIRST_SLOT = 11;

    private final Inventory inventory;

    private ClassMenu(Player player, ClassManager classes) {
        this.inventory = Bukkit.createInventory(this, SLOTS, Component.text("Classes"));
        PlayerClass current = classes.getClass(player.getUniqueId());
        for (PlayerClass playerClass : PlayerClass.values()) {
            inventory.setItem(FIRST_SLOT + playerClass.ordinal(), item(playerClass, playerClass == current));
        }
    }

    public static void open(Player player, ClassManager classes) {
        player.openInventory(new ClassMenu(player, classes).getInventory());
    }

    /** Classe que está no slot, ou null. */
    PlayerClass classAt(int slot) {
        int index = slot - FIRST_SLOT;
        PlayerClass[] all = PlayerClass.values();
        return index >= 0 && index < all.length ? all[index] : null;
    }

    private static ItemStack item(PlayerClass playerClass, boolean current) {
        List<String> lore = new ArrayList<>(playerClass.description());
        lore.add("");
        if (current) {
            lore.add("§aSua classe atual");
        } else if (playerClass.available()) {
            lore.add("§eClique para escolher");
        } else {
            lore.add("§8Em breve");
        }
        ItemStack item = ShopUi.button(playerClass.icon(), playerClass.coloredName(), lore.toArray(new String[0]));
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (current) {
            meta.setEnchantmentGlintOverride(true);
        }
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
