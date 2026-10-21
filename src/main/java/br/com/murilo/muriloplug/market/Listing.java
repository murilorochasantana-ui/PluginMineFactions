package br.com.murilo.muriloplug.market;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** Anúncio do mercado. O {@link ItemStack} é sempre copiado na entrada e na saída. */
public record Listing(long id, UUID seller, String sellerName, ItemStack item, long price, long createdAt) {

    public Listing {
        item = item.clone();
    }

    @Override
    public ItemStack item() {
        return item.clone();
    }

    public Listing withPrice(long newPrice) {
        return new Listing(id, seller, sellerName, item, newPrice, createdAt);
    }
}
