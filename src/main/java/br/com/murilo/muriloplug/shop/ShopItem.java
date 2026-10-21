package br.com.murilo.muriloplug.shop;

import org.bukkit.inventory.ItemStack;

/** Item à venda na loja. O {@link ItemStack} é sempre copiado na entrada e na saída, para ninguém alterar o original. */
public record ShopItem(int id, ItemStack item, long price) {

    public ShopItem {
        item = item.clone();
    }

    @Override
    public ItemStack item() {
        return item.clone();
    }
}
