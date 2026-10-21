package br.com.murilo.muriloplug.market;

import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** Confirmação de "Remover anúncio" (verde remove e devolve o item, vermelho volta). */
public final class ListingRemoveMenu implements MarketHolder {

    static final int CANCEL_SLOT = 3;
    static final int ITEM_SLOT = 4;
    static final int CONFIRM_SLOT = 5;

    private final Inventory inventory;
    private final long listingId;
    private final int backPage;
    private boolean used;

    private ListingRemoveMenu(Listing listing, int backPage) {
        this.listingId = listing.id();
        this.backPage = backPage;
        this.inventory = Bukkit.createInventory(this, 9, Component.text("Remover anúncio?"));
        inventory.setItem(CANCEL_SLOT, ShopUi.button(Material.RED_CONCRETE, "§cCancelar"));
        inventory.setItem(ITEM_SLOT, listing.item());
        inventory.setItem(CONFIRM_SLOT, ShopUi.button(Material.LIME_CONCRETE, "§aRemover e devolver o item"));
    }

    static void open(Player player, Listing listing, int backPage) {
        player.openInventory(new ListingRemoveMenu(listing, backPage).getInventory());
    }

    long listingId() {
        return listingId;
    }

    int backPage() {
        return backPage;
    }

    boolean used() {
        return used;
    }

    void markUsed() {
        used = true;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
