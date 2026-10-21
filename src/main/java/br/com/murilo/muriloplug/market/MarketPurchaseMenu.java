package br.com.murilo.muriloplug.market;

import br.com.murilo.muriloplug.economy.EconomyManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** Confirmação de compra (54 slots). Guarda o id e o preço que o jogador viu, para conferir antes de cobrar. */
public final class MarketPurchaseMenu implements MarketHolder {

    static final int ITEM_SLOT = 13;
    static final int SELLER_SLOT = 22;
    static final int PRICE_SLOT = 31;
    static final int CANCEL_SLOT = 45;
    static final int CONFIRM_SLOT = 53;

    private final Inventory inventory;
    private final long listingId;
    private final long price;
    private final int page;
    /** Vira true quando a compra é concluída (ou fica inválida), para um clique duplo não repetir nada. */
    private boolean used;

    private MarketPurchaseMenu(Listing listing, int page) {
        this.listingId = listing.id();
        this.price = listing.price();
        this.page = page;
        this.inventory = Bukkit.createInventory(this, 54, Component.text("Comprar?"));
        inventory.setItem(ITEM_SLOT, listing.item());
        inventory.setItem(SELLER_SLOT, MarketUi.sellerHead(listing.seller(), listing.sellerName()));
        inventory.setItem(PRICE_SLOT, ShopUi.button(Material.NAME_TAG, "Preço: §a" + EconomyManager.format(listing.price())));
        inventory.setItem(CANCEL_SLOT, ShopUi.button(Material.RED_CONCRETE, "§cCancelar"));
        inventory.setItem(CONFIRM_SLOT, ShopUi.button(Material.LIME_CONCRETE,
                "§aComprar por " + EconomyManager.format(listing.price())));
    }

    static void open(Player player, Listing listing, int page) {
        player.openInventory(new MarketPurchaseMenu(listing, page).getInventory());
    }

    long listingId() {
        return listingId;
    }

    long price() {
        return price;
    }

    int page() {
        return page;
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
