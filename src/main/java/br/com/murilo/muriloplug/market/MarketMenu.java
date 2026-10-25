package br.com.murilo.muriloplug.market;

import br.com.murilo.muriloplug.economy.EconomyManager;
import br.com.murilo.muriloplug.shop.ShopUi;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Tela principal do mercado (54 slots): 45 anúncios por página e, embaixo, a barra de navegação. */
public final class MarketMenu implements MarketHolder {

    static final int LISTINGS_PER_PAGE = 45;
    static final int PREVIOUS_SLOT = 45;
    static final int SELL_SLOT = 47;
    static final int BALANCE_SLOT = 49;
    static final int MY_LISTINGS_SLOT = 51;
    static final int NEXT_SLOT = 53;
    static final int EMPTY_SLOT = 22;

    private final Inventory inventory;
    private final MarketManager market;
    private final EconomyManager economy;
    private final UUID viewer;
    /** Id do anúncio de cada slot da página atual (-1 = vazio). */
    private final long[] listingIds = new long[LISTINGS_PER_PAGE];
    private int page;

    private MarketMenu(Player viewer, MarketManager market, EconomyManager economy, int page) {
        this.viewer = viewer.getUniqueId();
        this.market = market;
        this.economy = economy;
        this.page = page;
        this.inventory = Bukkit.createInventory(this, 54, Component.text("Mercado"));
        render();
    }

    public static void open(Player player, MarketManager market, EconomyManager economy, int page) {
        player.openInventory(new MarketMenu(player, market, economy, page).getInventory());
    }

    public int page() {
        return page;
    }

    /** Id do anúncio no slot, ou -1 se o slot não tiver anúncio. */
    long listingIdAt(int slot) {
        return slot >= 0 && slot < LISTINGS_PER_PAGE ? listingIds[slot] : -1;
    }

    int pageCount() {
        return Math.max(1, (market.size() + LISTINGS_PER_PAGE - 1) / LISTINGS_PER_PAGE);
    }

    /** Muda de página no próprio baú, sem reabrir. */
    void changePage(int delta) {
        page += delta;
        render();
    }

    /** Redesenha o baú com o estado atual do mercado. */
    void render() {
        List<Listing> all = market.newestFirst();
        page = Math.max(0, Math.min(page, pageCount() - 1));
        inventory.clear();
        Arrays.fill(listingIds, -1);

        int start = page * LISTINGS_PER_PAGE;
        for (int i = 0; i < LISTINGS_PER_PAGE && start + i < all.size(); i++) {
            Listing listing = all.get(start + i);
            listingIds[i] = listing.id();
            inventory.setItem(i, MarketUi.display(listing, viewer));
        }

        if (all.isEmpty()) {
            inventory.setItem(EMPTY_SLOT, ShopUi.button(Material.BARRIER, "§cNenhum item à venda"));
        }

        for (int slot = LISTINGS_PER_PAGE; slot < 54; slot++) {
            inventory.setItem(slot, ShopUi.filler());
        }
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, ShopUi.button(Material.ARROW, "§ePágina anterior"));
        }
        if (page < pageCount() - 1) {
            inventory.setItem(NEXT_SLOT, ShopUi.button(Material.ARROW, "§ePróxima página"));
        }
        inventory.setItem(SELL_SLOT, ShopUi.button(Material.LIME_DYE, "§a§lVender item"));
        Player player = Bukkit.getPlayer(viewer);
        inventory.setItem(BALANCE_SLOT, player == null
                ? ShopUi.button(Material.GOLD_INGOT, "§6Saldo: §a" + EconomyManager.format(economy.getBalance(viewer)))
                : ShopUi.button(Material.GOLD_INGOT, "§6Disponível: §a" + EconomyManager.format(economy.available(player)),
                ShopUi.moneyLore(player, economy)));
        inventory.setItem(MY_LISTINGS_SLOT, ShopUi.button(Material.CHEST,
                "§e§lMeus anúncios (" + market.count(viewer) + ")"));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
