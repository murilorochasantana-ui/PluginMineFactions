package br.com.murilo.muriloplug.clan.base;

import br.com.murilo.muriloplug.shop.ShopUi;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Itens "Núcleo do Clan" (BEACON), "Cofre do Clan" (VAULT, só dinheiro) e "Baú do Clan" (BARREL, 54 itens),
 * reconhecidos pelo PersistentDataContainer. O Baú recolhido leva o id dele (os itens continuam guardados nesse id);
 * o Baú da loja/kit não tem id: ganha um novo ao ser colocado.
 *
 * <p>Núcleo e Cofre só valem com o id e a TAG gravados pelo /clan itens ({@link #createOwned}); o clan guarda o id
 * atual, então um item antigo ou duplicado não pode ser colocado. Os do /kit admin (sem id) também não.
 */
public final class BaseItems {

    public enum Kind { CORE, VAULT, CHEST }

    private static final String CORE_ID = "nucleo";
    private static final String VAULT_ID = "cofre";
    private static final String CHEST_ID = "bau";

    private final NamespacedKey key;
    private final NamespacedKey chestKey;
    private final NamespacedKey ownedIdKey;
    private final NamespacedKey ownedTagKey;

    public BaseItems(JavaPlugin plugin) {
        this.key = new NamespacedKey(plugin, "clan_base");
        this.chestKey = new NamespacedKey(plugin, "bau_id");
        this.ownedIdKey = new NamespacedKey(plugin, "clan_item_id");
        this.ownedTagKey = new NamespacedKey(plugin, "clan_item_tag");
    }

    /** Núcleo ou Cofre do clan, com id único (o clan guarda qual id vale). */
    public ItemStack createOwned(Kind kind, String tag, String id) {
        ItemStack item = create(kind);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(ownedIdKey, PersistentDataType.STRING, id);
        meta.getPersistentDataContainer().set(ownedTagKey, PersistentDataType.STRING, tag);
        java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>(meta.lore());
        lore.add(ShopUi.text("§7Clan: §6[" + tag + "]"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Id do Núcleo/Cofre (null = sem id: item do kit, antigo ou falso). */
    public String ownedId(ItemStack item) {
        return item == null || !item.hasItemMeta() ? null
                : item.getItemMeta().getPersistentDataContainer().get(ownedIdKey, PersistentDataType.STRING);
    }

    /** TAG do clan dono do Núcleo/Cofre (null se não tiver). */
    public String ownedTag(ItemStack item) {
        return item == null || !item.hasItemMeta() ? null
                : item.getItemMeta().getPersistentDataContainer().get(ownedTagKey, PersistentDataType.STRING);
    }

    /** Núcleo ou Cofre (que não podem ser comprados nem vendidos). */
    public boolean isCoreOrVault(ItemStack item) {
        Kind kind = read(item);
        return kind == Kind.CORE || kind == Kind.VAULT;
    }

    /** Baú do Clan recolhido: leva o id (e, com ele, os itens guardados). */
    public ItemStack createChest(String id) {
        ItemStack item = create(Kind.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(chestKey, PersistentDataType.STRING, id);
        meta.lore(List.of(
                ShopUi.text("§7Guarda 54 itens do clan."),
                ShopUi.text("§7Tem 3 de vida (explosões)."),
                ShopUi.text("§eItens guardados dentro.")));
        item.setItemMeta(meta);
        return item;
    }

    /** Id do Baú do Clan (null se não tiver: baú novo). */
    public String chestId(ItemStack item) {
        return item == null || !item.hasItemMeta() ? null
                : item.getItemMeta().getPersistentDataContainer().get(chestKey, PersistentDataType.STRING);
    }

    public ItemStack create(Kind kind) {
        Material material = switch (kind) {
            case CORE -> Material.BEACON;
            case VAULT -> Material.VAULT;
            case CHEST -> Material.BARREL;
        };
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (kind == Kind.CHEST) {
            meta.displayName(ShopUi.text("§6Baú do Clan"));
            meta.lore(List.of(ShopUi.text("§7Guarda 54 itens do clan."), ShopUi.text("§7Tem 3 de vida (explosões).")));
        } else if (kind == Kind.CORE) {
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
                    ShopUi.text("§7Guarda só o dinheiro do clan e rende juros."),
                    ShopUi.text("§7Só é destruído por explosões.")));
        }
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING,
                kind == Kind.CORE ? CORE_ID : kind == Kind.VAULT ? VAULT_ID : CHEST_ID);
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
        if (CHEST_ID.equals(id) && item.getType() == Material.BARREL) {
            return Kind.CHEST;
        }
        return null;
    }
}
