package br.com.murilo.muriloplug.clan.base;

import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** Itens "Núcleo do Clan" (BEACON) e "Cofre do Clan" (VAULT), reconhecidos pelo PersistentDataContainer. */
public final class BaseItems {

    public enum Kind { CORE, VAULT }

    private static final String CORE_ID = "nucleo";
    private static final String VAULT_ID = "cofre";

    private final NamespacedKey key;

    public BaseItems(JavaPlugin plugin) {
        this.key = new NamespacedKey(plugin, "clan_base");
    }

    public ItemStack create(Kind kind) {
        ItemStack item = new ItemStack(kind == Kind.CORE ? Material.BEACON : Material.VAULT);
        ItemMeta meta = item.getItemMeta();
        if (kind == Kind.CORE) {
            meta.displayName(ShopUi.text("§6Núcleo do Clan"));
            meta.lore(List.of(
                    ShopUi.text("§7Só o líder coloca, dentro de um claim"),
                    ShopUi.text("§7de um membro. 1 por clan."),
                    ShopUi.text("§7Só é destruído por explosões."),
                    ShopUi.text("§cDestruído: o clan fica vulnerável.")));
        } else {
            meta.displayName(ShopUi.text("§eCofre do Clan"));
            meta.lore(List.of(
                    ShopUi.text("§7Coloque perto do Núcleo do clan."),
                    ShopUi.text("§7Guarda dinheiro e rende juros."),
                    ShopUi.text("§7Só é destruído por explosões.")));
        }
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, kind == Kind.CORE ? CORE_ID : VAULT_ID);
        item.setItemMeta(meta);
        return item;
    }

    /** Tipo do item, ou null se não for um item da base. */
    public Kind read(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (CORE_ID.equals(id) && item.getType() == Material.BEACON) {
            return Kind.CORE;
        }
        if (VAULT_ID.equals(id) && item.getType() == Material.VAULT) {
            return Kind.VAULT;
        }
        return null;
    }
}
