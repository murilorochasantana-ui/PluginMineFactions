package br.com.murilo.muriloplug.clan.base;

import br.com.murilo.muriloplug.clan.ClanHolder;
import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * /clan itens (27 slots): o Núcleo (slot 11) e o Cofre (slot 15) do clan, com o estado de cada um na lore. Como é um
 * {@link ClanHolder}, todo clique e arrasto é cancelado; a entrega fica em {@link BaseBlocks}.
 */
public final class ClanItemsMenu implements ClanHolder {

    static final int CORE_SLOT = 11;
    static final int VAULT_SLOT = 15;

    private final Inventory inventory;
    private final String tag;

    ClanItemsMenu(String tag) {
        this.tag = tag;
        this.inventory = Bukkit.createInventory(this, 27, Component.text("Itens do Clan"));
    }

    String tag() {
        return tag;
    }

    void render(ItemStack core, List<String> coreLore, ItemStack vault, List<String> vaultLore) {
        inventory.clear();
        for (int slot = 0; slot < 27; slot++) {
            inventory.setItem(slot, ShopUi.filler());
        }
        inventory.setItem(CORE_SLOT, withLore(core, coreLore));
        inventory.setItem(VAULT_SLOT, withLore(vault, vaultLore));
    }

    private static ItemStack withLore(ItemStack item, List<String> lines) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lines.forEach(line -> lore.add(ShopUi.text(line)));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
